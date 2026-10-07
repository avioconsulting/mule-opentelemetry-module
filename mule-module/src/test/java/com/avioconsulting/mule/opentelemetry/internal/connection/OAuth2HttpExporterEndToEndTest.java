package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.test.util.auth.LocalOtlpCollector;

public class OAuth2HttpExporterEndToEndTest extends AbstractOAuth2ExporterEndToEndTest {
  @Override
  protected LocalOtlpCollector.Protocol protocol() {
    return LocalOtlpCollector.Protocol.HTTP_PROTOBUF;
  }
}
