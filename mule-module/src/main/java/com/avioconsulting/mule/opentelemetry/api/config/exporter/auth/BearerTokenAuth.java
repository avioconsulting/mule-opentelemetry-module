package com.avioconsulting.mule.opentelemetry.api.config.exporter.auth;

import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Password;

/** A static opaque bearer token. */
@Alias("bearer-token")
public class BearerTokenAuth implements ExporterAuthentication {
  @Parameter
  @Password
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  @DisplayName(value = "Token")
  private String token;

  public BearerTokenAuth() {
  }

  public BearerTokenAuth(String token) {
    this.token = token;
  }

  public String getToken() {
    return token;
  }

  @Override
  public String getHeaderName() {
    return "Authorization";
  }

  @Override
  public void validate() {
    AuthenticationValidation.headerValue(token);
  }
}
