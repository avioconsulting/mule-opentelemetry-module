package com.avioconsulting.mule.opentelemetry.internal.auth;

import java.util.Map;

/** Connection-owned credentials shared across signals. */
public interface ExporterAuthenticationProvider extends AutoCloseable {
  void ensureUsable();

  /** Cache-only lookup; must never initiate network activity. */
  Map<String, String> headers();

  String getHeaderName();

  @Override
  void close();
}
