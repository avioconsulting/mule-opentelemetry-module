package com.avioconsulting.mule.opentelemetry.internal.auth.oauth2;

import com.avioconsulting.mule.opentelemetry.internal.auth.ExporterAuthenticationProvider;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import static com.avioconsulting.mule.opentelemetry.internal.auth.oauth2.OAuth2AuthenticationException.Category.*;

/**
 * Immutable, monotonic token cache with single-flight, bounded wait and retry
 * cooldown.
 */
public final class OAuth2TokenProvider implements ExporterAuthenticationProvider {
  private final OAuth2TokenClient client;
  private volatile LongSupplier clock;
  private volatile DoubleSupplier jitter;
  private final long skewNanos;
  private final int waitMillis;
  private volatile State state;
  private volatile boolean closed;
  private CompletableFuture<Void> inFlight;
  private long retryAt;
  private int failures;

  public OAuth2TokenProvider(OAuth2TokenClient client, int skewSeconds, int waitMillis) {
    this(client, skewSeconds, waitMillis, System::nanoTime, () -> ThreadLocalRandom.current().nextDouble());
  }

  public OAuth2TokenProvider(OAuth2TokenClient client, int skewSeconds, int waitMillis,
      LongSupplier clock, DoubleSupplier jitter) {
    this.client = client;
    this.skewNanos = TimeUnit.SECONDS.toNanos(skewSeconds);
    this.waitMillis = waitMillis;
    this.clock = clock;
    this.jitter = jitter;
  }

  /**
   * Test-only instance seam across the Mule artifact loader, usable only before
   * the first acquisition; never a
   * global override.
   *
   * @param clock
   *            monotonic time source in nanoseconds
   * @param jitter
   *            renewal jitter source
   */
  public synchronized void _useTimeSourcesForTest(LongSupplier clock, DoubleSupplier jitter) {
    checkOpen();
    if (state != null || inFlight != null || failures != 0) {
      throw new IllegalStateException("Authentication time sources cannot change after acquisition starts");
    }
    this.clock = java.util.Objects.requireNonNull(clock, "clock");
    this.jitter = java.util.Objects.requireNonNull(jitter, "jitter");
  }

  @Override
  public void ensureUsable() {
    checkOpen();
    State cached = state;
    long now = clock.getAsLong();
    if (cached != null && now - cached.refreshAt < 0) {
      return;
    }
    CompletableFuture<Void> flight;
    boolean winner = false;
    synchronized (this) {
      checkOpen();
      cached = state;
      now = clock.getAsLong();
      if (cached != null && now - cached.refreshAt < 0) {
        return;
      }
      if (inFlight != null) {
        if (valid(cached, now)) {
          return;
        }
        flight = inFlight;
      } else {
        if (failures > 0 && now - retryAt < 0) {
          if (valid(cached, now)) {
            return;
          }
          throw new OAuth2AuthenticationException(COOLDOWN);
        }
        flight = new CompletableFuture<>();
        inFlight = flight;
        winner = true;
      }
    }
    if (winner) {
      renew(flight);
    } else {
      try {
        flight.get(waitMillis, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new OAuth2AuthenticationException(INTERRUPTED);
      } catch (TimeoutException e) {
        throw new OAuth2AuthenticationException(TIMEOUT);
      } catch (ExecutionException e) {
        /* Recheck state; prior token may still be usable. */ }
    }
    checkOpen();
    if (!valid(state, clock.getAsLong())) {
      throw new OAuth2AuthenticationException(COOLDOWN);
    }
  }

  private void renew(CompletableFuture<Void> flight) {
    long start = clock.getAsLong();
    try {
      OAuth2AccessToken token = client.acquire();
      long lifetime = token.getLifetimeNanos();
      State replacement = new State(token, start + lifetime,
          start + lifetime - Math.min(skewNanos, lifetime / 2));
      synchronized (this) {
        checkOpen();
        if (!valid(replacement, clock.getAsLong())) {
          throw new OAuth2AuthenticationException(TIMEOUT);
        }
        state = replacement;
        failures = 0;
        flight.complete(null);
      }
    } catch (RuntimeException failure) {
      synchronized (this) {
        failures = Math.min(6, failures + 1);
        long base = TimeUnit.SECONDS.toNanos(Math.min(30, 1L << (failures - 1)));
        double noise = Math.max(0, Math.min(1, jitter.getAsDouble()));
        retryAt = clock.getAsLong() + Math.min(TimeUnit.SECONDS.toNanos(30),
            (long) (base * (0.8 + noise * 0.4)));
        flight.completeExceptionally(new OAuth2AuthenticationException(COOLDOWN));
      }
    } finally {
      synchronized (this) {
        if (inFlight == flight) {
          inFlight = null;
        }
      }
    }
  }

  private static boolean valid(State state, long now) {
    return state != null && now - state.expiresAt < 0;
  }

  private void checkOpen() {
    if (closed) {
      throw new OAuth2AuthenticationException(CLOSED);
    }
  }

  @Override
  public Map<String, String> headers() {
    checkOpen();
    State cached = state;
    // Empty only before first acquisition, for SDK diagnostics. Actual exports are
    // gated.
    if (cached == null) {
      return Collections.emptyMap();
    }
    if (!valid(cached, clock.getAsLong())) {
      throw new OAuth2AuthenticationException(TIMEOUT);
    }
    return cached.headers;
  }

  @Override
  public String getHeaderName() {
    return "Authorization";
  }

  @Override
  public void close() {
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
      state = null;
      if (inFlight != null) {
        inFlight.completeExceptionally(new OAuth2AuthenticationException(CLOSED));
      }
    }
    client.close();
  }

  private static final class State {
    private final Map<String, String> headers;
    private final long expiresAt;
    private final long refreshAt;

    State(OAuth2AccessToken token, long expiresAt, long refreshAt) {
      headers = Collections.singletonMap("Authorization", "Bearer " + token.getValue());
      this.expiresAt = expiresAt;
      this.refreshAt = refreshAt;
    }
  }
}
