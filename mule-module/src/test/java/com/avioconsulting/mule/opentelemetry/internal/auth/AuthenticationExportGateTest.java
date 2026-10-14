package com.avioconsulting.mule.opentelemetry.internal.auth;

import com.avioconsulting.mule.opentelemetry.internal.auth.oauth2.OAuth2AuthenticationException;
import io.opentelemetry.sdk.common.CompletableResultCode;
import org.junit.Test;
import org.slf4j.Logger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuthenticationExportGateTest {
  @Test
  public void logsOriginalFailureAndThrottlesSkippedExports() {
    Logger logger = mock(Logger.class);
    when(logger.isWarnEnabled()).thenReturn(true);
    AtomicLong clock = new AtomicLong();
    ExporterAuthenticationProvider provider = mock(ExporterAuthenticationProvider.class);
    doThrow(OAuth2AuthenticationException.cooldown(OAuth2AuthenticationException.http(401)))
        .when(provider).ensureUsable();
    AuthenticationExportGate gate = new AuthenticationExportGate(provider, logger, clock::get);
    Supplier<CompletableResultCode> delegate = mock(Supplier.class);
    assertThat(gate.export(delegate).isSuccess()).isFalse();
    assertThat(gate.export(delegate).isSuccess()).isFalse();
    verify(logger).warn(anyString(), eq(OAuth2AuthenticationException.Category.COOLDOWN),
        eq(OAuth2AuthenticationException.Category.HTTP), eq(OAuth2AuthenticationException.Reason.UNSPECIFIED),
        eq(401));
    verify(logger, times(1)).warn(anyString(), any(), any(), any(), any());
    clock.set(TimeUnit.SECONDS.toNanos(30));
    gate.export(delegate);
    verify(logger, times(2)).warn(anyString(), any(), any(), any(), any());
    verify(delegate, never()).get();
  }

  @Test
  public void reentrantAppenderCannotRepeatWarning() {
    Logger logger = mock(Logger.class);
    when(logger.isWarnEnabled()).thenReturn(true);
    ExporterAuthenticationProvider provider = mock(ExporterAuthenticationProvider.class);
    doThrow(OAuth2AuthenticationException.http(503)).when(provider).ensureUsable();
    AuthenticationExportGate gate = new AuthenticationExportGate(provider, logger, () -> 0);
    doAnswer(invocation -> {
      assertThat(gate.export(CompletableResultCode::ofSuccess).isSuccess()).isFalse();
      return null;
    }).when(logger).warn(anyString(), any(), any(), any(), any());
    assertThat(gate.export(CompletableResultCode::ofSuccess).isSuccess()).isFalse();
    verify(logger, times(1)).warn(anyString(), any(), any(), any(), any());
  }
}
