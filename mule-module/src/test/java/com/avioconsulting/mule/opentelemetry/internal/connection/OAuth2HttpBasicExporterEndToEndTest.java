package com.avioconsulting.mule.opentelemetry.internal.connection;

public class OAuth2HttpBasicExporterEndToEndTest extends OAuth2HttpExporterEndToEndTest {
  @Override
  protected String placement() {
    return "CLIENT_SECRET_BASIC";
  }
}
