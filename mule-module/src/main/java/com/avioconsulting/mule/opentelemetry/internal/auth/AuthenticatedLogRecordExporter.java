package com.avioconsulting.mule.opentelemetry.internal.auth;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.util.Collection;

public final class AuthenticatedLogRecordExporter implements LogRecordExporter {
  private final LogRecordExporter delegate;
  private final AuthenticationExportGate gate;
  private CompletableResultCode shutdown;

  public AuthenticatedLogRecordExporter(LogRecordExporter delegate, AuthenticationExportGate gate) {
    this.delegate = delegate;
    this.gate = gate;
  }

  @Override
  public CompletableResultCode export(Collection<LogRecordData> logs) {
    return gate.export(() -> delegate.export(logs));
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
    return "AuthenticatedLogRecordExporter";
  }
}
