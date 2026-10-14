package com.avioconsulting.mule.opentelemetry.internal.auth;

import com.avioconsulting.mule.opentelemetry.internal.auth.oauth2.OAuth2AuthenticationException;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import org.junit.Test;
import java.util.Collections;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuthenticatedExporterTest {
  @Test
  public void authenticationOutageSkipsAllSignalsAndLaterRecovers() {
    ExporterAuthenticationProvider provider = mock(ExporterAuthenticationProvider.class);
    AuthenticationExportGate gate = new AuthenticationExportGate(provider);
    SpanExporter span = mock(SpanExporter.class);
    MetricExporter metric = mock(MetricExporter.class);
    LogRecordExporter log = mock(LogRecordExporter.class);
    AuthenticatedSpanExporter spans = new AuthenticatedSpanExporter(span, gate);
    AuthenticatedMetricExporter metrics = new AuthenticatedMetricExporter(metric, gate);
    AuthenticatedLogRecordExporter logs = new AuthenticatedLogRecordExporter(log, gate);
    doThrow(new OAuth2AuthenticationException(OAuth2AuthenticationException.Category.HTTP)).when(provider)
        .ensureUsable();
    assertThat(spans.export(Collections.emptyList()).isSuccess()).isFalse();
    assertThat(metrics.export(Collections.emptyList()).isSuccess()).isFalse();
    assertThat(logs.export(Collections.emptyList()).isSuccess()).isFalse();
    verify(span, never()).export(any());
    verify(metric, never()).export(any());
    verify(log, never()).export(any());
    doNothing().when(provider).ensureUsable();
    CompletableResultCode result = CompletableResultCode.ofSuccess();
    when(span.export(any())).thenReturn(result);
    assertThat(spans.export(Collections.emptyList())).isSameAs(result);
    when(span.export(any()))
        .thenThrow(new OAuth2AuthenticationException(OAuth2AuthenticationException.Category.CLOSED));
    assertThat(spans.export(Collections.emptyList()).isSuccess()).isFalse();
  }

  @Test
  public void shutdownAndFlushDelegateWithoutClosingSharedProvider() {
    ExporterAuthenticationProvider provider = mock(ExporterAuthenticationProvider.class);
    SpanExporter delegate = mock(SpanExporter.class);
    CompletableResultCode result = CompletableResultCode.ofSuccess();
    when(delegate.shutdown()).thenReturn(result);
    when(delegate.flush()).thenReturn(result);
    AuthenticatedSpanExporter wrapper = new AuthenticatedSpanExporter(delegate,
        new AuthenticationExportGate(provider));
    assertThat(wrapper.flush()).isSameAs(result);
    assertThat(wrapper.shutdown()).isSameAs(result);
    assertThat(wrapper.shutdown()).isSameAs(result);
    verify(delegate, times(1)).shutdown();
    verify(provider, never()).close();
  }
}
