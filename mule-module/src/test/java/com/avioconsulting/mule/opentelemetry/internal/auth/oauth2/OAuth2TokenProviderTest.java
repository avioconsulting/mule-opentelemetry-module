package com.avioconsulting.mule.opentelemetry.internal.auth.oauth2;

import org.junit.Test;
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
