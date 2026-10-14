package com.avioconsulting.mule.opentelemetry.internal.auth;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;

public final class AuthenticatedSpanExporter implements SpanExporter {
  private final SpanExporter delegate;
  private final AuthenticationExportGate gate;
  private CompletableResultCode shutdown;

  public AuthenticatedSpanExporter(SpanExporter delegate, AuthenticationExportGate gate) {
    this.delegate = delegate;
    this.gate = gate;
  }

  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    return gate.export(() -> delegate.export(spans));
  }

  @Override
  public CompletableResultCode flush() {
    return delegate.flush();
  }

  @Override
  public synchronized CompletableResultCode shutdown() {
    if (shutdown == null) {
      shutdown = delegate.shutdown();
    }
    return shutdown;
  }

  @Override
  public String toString() {
    return "AuthenticatedSpanExporter";
  }
}
