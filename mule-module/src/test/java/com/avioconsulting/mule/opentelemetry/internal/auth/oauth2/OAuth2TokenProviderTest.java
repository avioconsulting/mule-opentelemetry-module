package com.avioconsulting.mule.opentelemetry.internal.auth.oauth2;

import org.junit.Test;
import org.slf4j.Logger;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OAuth2TokenProviderTest {
  private final AtomicLong time = new AtomicLong();
  private final OAuth2TokenClient client = mock(OAuth2TokenClient.class);
  private final OAuth2TokenProvider provider = new OAuth2TokenProvider(client, 60, 100, time::get, () -> 0.5);

  private void seconds(long seconds) {
    time.set(TimeUnit.SECONDS.toNanos(seconds));
  }

  @Test
  public void preservesHttpFailureThroughCooldownAndLogsRecovery() {
    Logger logger = mock(Logger.class);
    when(logger.isWarnEnabled()).thenReturn(true);
    when(logger.isInfoEnabled()).thenReturn(true);
    when(logger.isDebugEnabled()).thenReturn(true);
    OAuth2TokenProvider observed = new OAuth2TokenProvider(client, 60, 100, time::get, () -> 0.5, logger);
    when(client.acquire()).thenThrow(OAuth2AuthenticationException.http(401))
        .thenThrow(OAuth2AuthenticationException.http(503))
        .thenReturn(new OAuth2AccessToken("secret-token", 10));
    for (int i = 0; i < 2; i++) {
      assertThatThrownBy(observed::ensureUsable).isInstanceOfSatisfying(OAuth2AuthenticationException.class,
          failure -> {
            assertThat(failure.getCategory()).isEqualTo(OAuth2AuthenticationException.Category.COOLDOWN);
            assertThat(failure.getFailureCategory()).isEqualTo(OAuth2AuthenticationException.Category.HTTP);
            assertThat(failure.getHttpStatus()).isEqualTo(401);
            assertThat(failure.getCause()).isNull();
          });
    }
    verify(client, times(1)).acquire();
    seconds(1);
    assertThatThrownBy(observed::ensureUsable).hasMessageContaining("httpStatus=503");
    verify(logger, times(1)).warn(anyString(), any(), any(), any(), any(), any());
    verify(logger).warn(anyString(), eq(OAuth2AuthenticationException.Category.HTTP),
        eq(OAuth2AuthenticationException.Reason.UNSPECIFIED), eq(401), eq(false), eq(1000L));
    seconds(3);
    observed.ensureUsable();
    verify(logger).info("OAuth2 exporter authentication recovered; token acquisition succeeded");
    verify(logger).debug(anyString(), eq("acquisition"), eq(0L), eq(10000L), eq(5000L));
    verify(logger).debug(anyString(), eq(OAuth2AuthenticationException.Category.HTTP),
        eq(OAuth2AuthenticationException.Reason.UNSPECIFIED), eq(503), eq(2000L));
    observed.ensureUsable();
    verify(logger, times(1)).info(anyString());
    for (org.mockito.invocation.Invocation invocation : mockingDetails(logger).getInvocations()) {
      assertThat(java.util.Arrays.deepToString(invocation.getRawArguments())).doesNotContain("secret-token");
    }
  }

  @Test
  public void warningAppenderCanReenterProviderWithoutAnotherAcquisition() {
    Logger logger = mock(Logger.class);
    when(logger.isWarnEnabled()).thenReturn(true);
    OAuth2TokenProvider observed = new OAuth2TokenProvider(client, 60, 100, time::get, () -> 0.5, logger);
    when(client.acquire()).thenThrow(OAuth2AuthenticationException.http(401));
    doAnswer(invocation -> {
      assertThatThrownBy(observed::ensureUsable).hasMessageContaining("COOLDOWN").hasMessageContaining("401");
      return null;
    }).when(logger).warn(anyString(), any(), any(), any(), any(), any());
    assertThatThrownBy(observed::ensureUsable).hasMessageContaining("COOLDOWN");
    verify(client, times(1)).acquire();
    verify(logger, times(1)).warn(anyString(), any(), any(), any(), any(), any());
  }

  @Test
  public void logsRefreshFailureEvenWhileCachedTokenIsUsableAndThrottlesWarnings() {
    Logger logger = mock(Logger.class);
    when(logger.isWarnEnabled()).thenReturn(true);
    OAuth2TokenProvider observed = new OAuth2TokenProvider(client, 60, 100, time::get, () -> 0.5, logger);
    when(client.acquire()).thenReturn(new OAuth2AccessToken("secret-token", 100))
        .thenThrow(new IllegalStateException("secret-client-secret"));
    observed.ensureUsable();
    seconds(50);
    observed.ensureUsable();
    verify(logger).warn(anyString(), eq(OAuth2AuthenticationException.Category.TRANSPORT),
        eq(OAuth2AuthenticationException.Reason.UNSPECIFIED), eq(0), eq(true), eq(1000L));
    seconds(51);
    observed.ensureUsable();
    verify(logger, times(1)).warn(anyString(), any(), any(), any(), any(), any());
    seconds(80);
    observed.ensureUsable();
    verify(logger, times(2)).warn(anyString(), any(), any(), any(), any(), any());
    assertThat(observed.headers()).containsEntry("Authorization", "Bearer secret-token");
    for (org.mockito.invocation.Invocation invocation : mockingDetails(logger).getInvocations()) {
      assertThat(java.util.Arrays.deepToString(invocation.getRawArguments()))
          .doesNotContain("secret-token", "secret-client-secret");
    }
  }

  @Test
  public void concurrentSignalsShareOneSuccessfulAcquisition() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(client.acquire()).thenAnswer(invocation -> {
      started.countDown();
      assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
      return new OAuth2AccessToken("A", 10);
    });
    OAuth2TokenProvider concurrent = new OAuth2TokenProvider(client, 60, 1000, time::get, () -> 0.5);
    ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      java.util.List<Future<?>> calls = new java.util.ArrayList<>();
      for (int i = 0; i < 8; i++) {
        calls.add(executor.submit(concurrent::ensureUsable));
      }
      assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
      release.countDown();
      for (Future<?> call : calls) {
        call.get(2, TimeUnit.SECONDS);
      }
      verify(client, times(1)).acquire();
    } finally {
      release.countDown();
      concurrent.close();
      executor.shutdownNow();
    }
  }

  @Test
  public void concurrentCallersUseValidTokenDuringProactiveRenewal() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(client.acquire()).thenReturn(new OAuth2AccessToken("A", 10)).thenAnswer(invocation -> {
      started.countDown();
      assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
      return new OAuth2AccessToken("B", 10);
    });
    provider.ensureUsable();
    seconds(5);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> renewal = executor.submit(provider::ensureUsable);
      assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
      provider.ensureUsable();
      assertThat(provider.headers()).containsEntry("Authorization", "Bearer A");
      release.countDown();
      renewal.get(1, TimeUnit.SECONDS);
      assertThat(provider.headers()).containsEntry("Authorization", "Bearer B");
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  public void interruptedWaitPreservesInterruptStatus() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(client.acquire()).thenAnswer(invocation -> {
      started.countDown();
      release.await(2, TimeUnit.SECONDS);
      return new OAuth2AccessToken("A", 10);
    });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> winner = executor.submit(provider::ensureUsable);
      assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
      Thread.currentThread().interrupt();
      assertThatThrownBy(provider::ensureUsable).hasMessageContaining("INTERRUPTED");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      Thread.interrupted();
      release.countDown();
      winner.get(1, TimeUnit.SECONDS);
    } finally {
      Thread.interrupted();
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  public void cachesAndRotatesAtHalfShortLifetime() {
    when(client.acquire()).thenReturn(new OAuth2AccessToken("A", 10), new OAuth2AccessToken("B", 10));
    assertThat(provider.headers()).isEmpty();
    verify(client, never()).acquire();
    provider.ensureUsable();
    seconds(4);
    provider.ensureUsable();
    assertThat(provider.headers()).containsEntry("Authorization", "Bearer A");
    seconds(5);
    provider.ensureUsable();
    assertThat(provider.headers()).containsEntry("Authorization", "Bearer B");
    verify(client, times(2)).acquire();
  }

  @Test
  public void retainsValidTokenAndThrottlesFailuresThenRecovers() {
    when(client.acquire()).thenReturn(new OAuth2AccessToken("A", 10))
        .thenThrow(new OAuth2AuthenticationException(OAuth2AuthenticationException.Category.HTTP))
        .thenReturn(new OAuth2AccessToken("B", 10));
    provider.ensureUsable();
    seconds(5);
    provider.ensureUsable();
    provider.ensureUsable();
    assertThat(provider.headers()).containsEntry("Authorization", "Bearer A");
    verify(client, times(2)).acquire();
    seconds(10);
    assertThatThrownBy(provider::headers).isInstanceOf(OAuth2AuthenticationException.class);
    provider.ensureUsable();
    assertThat(provider.headers()).containsEntry("Authorization", "Bearer B");
  }

  @Test
  public void emptyCacheUsesCooldown() {
    when(client.acquire())
        .thenThrow(new OAuth2AuthenticationException(OAuth2AuthenticationException.Category.HTTP));
    assertThatThrownBy(provider::ensureUsable).isInstanceOf(OAuth2AuthenticationException.class);
    assertThatThrownBy(provider::ensureUsable).isInstanceOf(OAuth2AuthenticationException.class);
    verify(client, times(1)).acquire();
    seconds(1);
    assertThatThrownBy(provider::ensureUsable).isInstanceOf(OAuth2AuthenticationException.class);
    verify(client, times(2)).acquire();
  }

  @Test
  public void coordinatesConcurrentAcquisitionAndCloseRace() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(client.acquire()).thenAnswer(invocation -> {
      started.countDown();
      assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
      return new OAuth2AccessToken("A", 10);
    });
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> winner = executor.submit(provider::ensureUsable);
      assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(provider::ensureUsable).hasMessageContaining("TIMEOUT");
      verify(client, times(1)).acquire();
      provider.close();
      provider.close();
      release.countDown();
      assertThatThrownBy(() -> winner.get(1, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
      assertThatThrownBy(provider::headers).hasMessageContaining("CLOSED");
      verify(client, times(1)).close();
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }
}
