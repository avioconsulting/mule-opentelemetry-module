package com.avioconsulting.mule.opentelemetry.internal.auth.oauth2;

/** Dedicated, bounded token acquisition transport. */
public interface OAuth2TokenClient extends AutoCloseable {
  OAuth2AccessToken acquire();

  @Override
  void close();
}
