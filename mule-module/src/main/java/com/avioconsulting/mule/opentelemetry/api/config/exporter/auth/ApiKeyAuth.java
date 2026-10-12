package com.avioconsulting.mule.opentelemetry.api.config.exporter.auth;

import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Password;

/** A static API key in a named header. */
@Alias("api-key")
public class ApiKeyAuth implements ExporterAuthentication {
  @Parameter
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  @DisplayName(value = "Header Name")
  private String headerName;

  @Parameter
  @Password
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  @DisplayName(value = "Value")
  private String value;

  public ApiKeyAuth() {
  }

  public ApiKeyAuth(String headerName, String value) {
    this.headerName = headerName;
    this.value = value;
  }

  @Override
  public String getHeaderName() {
    return headerName;
  }

  public String getValue() {
    return value;
  }

  @Override
  public void validate() {
    AuthenticationValidation.headerName(headerName);
    AuthenticationValidation.headerValue(value);
  }
}
