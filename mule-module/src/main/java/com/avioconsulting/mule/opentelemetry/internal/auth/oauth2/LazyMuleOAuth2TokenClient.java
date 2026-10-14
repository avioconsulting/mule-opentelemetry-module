package com.avioconsulting.mule.opentelemetry.internal.auth.oauth2;

import com.avioconsulting.mule.opentelemetry.api.config.exporter.auth.OAuth2Auth;
import org.mule.runtime.http.api.HttpService;

/**
 * Does not allocate a HTTP client when overrides disable all authenticated
 * signals.
 */
public final class LazyMuleOAuth2TokenClient implements OAuth2TokenClient {
  private final HttpService service;
  private OAuth2Auth configuration;
  private MuleOAuth2TokenClient delegate;
  private boolean closed;

  public LazyMuleOAuth2TokenClient(HttpService service, OAuth2Auth configuration) {
    this.service = service;
    this.configuration = configuration;
  }

  @Override
  public OAuth2AccessToken acquire() {
    MuleOAuth2TokenClient client;
    synchronized (this) {
      if (closed) {
        throw new OAuth2AuthenticationException(OAuth2AuthenticationException.Category.CLOSED);
      }
      if (delegate == null) {
        delegate = new MuleOAuth2TokenClient(service, configuration);
      }
      client = delegate;
    }
    return client.acquire();
  }

  @Override
  public void close() {
    MuleOAuth2TokenClient client;
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
      configuration = null;
      client = delegate;
      delegate = null;
    }
    if (client != null) {
      client.close();
    }
  }
}
