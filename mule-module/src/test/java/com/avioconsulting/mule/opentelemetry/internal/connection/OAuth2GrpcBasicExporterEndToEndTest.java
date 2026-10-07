package com.avioconsulting.mule.opentelemetry.internal.connection;

public class OAuth2GrpcBasicExporterEndToEndTest extends OAuth2GrpcExporterEndToEndTest {
  @Override
  protected String placement() {
    return "CLIENT_SECRET_BASIC";
  }
}
