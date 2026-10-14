package com.avioconsulting.mule.opentelemetry.api.config.exporter.auth;

import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Password;

/** OAuth2 client credentials; renewal always requests a new access token. */
@Alias("oauth2")
public class OAuth2Auth implements ExporterAuthentication {
  @Parameter
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  @DisplayName(value = "Token URL")
  private String tokenUrl;
  @Parameter
  @Password
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  @DisplayName(value = "Client ID")
  private String clientId;
  @Parameter
  @Password
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  @DisplayName(value = "Client Secret")
  private String clientSecret;
  @Parameter
  @Optional
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  @DisplayName(value = "Scopes")
  private String scopes;
  @Parameter
  @Optional(defaultValue = "CLIENT_SECRET_POST")
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  @DisplayName(value = "Credential Placement")
  private CredentialPlacement credentialPlacement = CredentialPlacement.CLIENT_SECRET_POST;
  @Parameter
  @Optional(defaultValue = "60")
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  @DisplayName(value = "Refresh Before Expiry (Seconds)")
  private int refreshBeforeExpirySeconds = 60;
  @Parameter
  @Optional(defaultValue = "8000")
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  @DisplayName(value = "Request Timeout Millis")
  private int requestTimeoutMillis = 8000;

  public OAuth2Auth() {
  }

  public OAuth2Auth(String tokenUrl, String clientId, String clientSecret, String scopes) {
    this.tokenUrl = tokenUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.scopes = scopes;
  }

  public String getTokenUrl() {
    return tokenUrl;
  }

  public String getClientId() {
    return clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public String getScopes() {
    return scopes;
  }

  public CredentialPlacement getCredentialPlacement() {
    return credentialPlacement;
  }

  public int getRefreshBeforeExpirySeconds() {
    return refreshBeforeExpirySeconds;
  }

  public int getRequestTimeoutMillis() {
    return requestTimeoutMillis;
  }

  /**
   * Sets the bounded token-request timeout; validation runs before connection
   * creation.
   *
   * @param requestTimeoutMillis
   *            token-request budget in milliseconds, from 1 to 60000
   * @return this configuration
   */
  public OAuth2Auth setRequestTimeoutMillis(int requestTimeoutMillis) {
    this.requestTimeoutMillis = requestTimeoutMillis;
    return this;
  }

  public OAuth2Auth setCredentialPlacement(CredentialPlacement placement) {
    this.credentialPlacement = placement;
    return this;
  }

  @Override
  public String getHeaderName() {
    return "Authorization";
  }

  @Override
  public void validate() {
    AuthenticationValidation.httpsUrl(tokenUrl, "tokenUrl");
    AuthenticationValidation.nonblank(clientId, "clientId");
    AuthenticationValidation.nonblank(clientSecret, "clientSecret");
    if (credentialPlacement == null || refreshBeforeExpirySeconds < 0 || refreshBeforeExpirySeconds > 3600
        || requestTimeoutMillis < 1 || requestTimeoutMillis > 60000) {
      throw new IllegalArgumentException("Invalid exporter OAuth2 advanced options");
    }
  }

  public enum CredentialPlacement {
    CLIENT_SECRET_POST, CLIENT_SECRET_BASIC
  }
}
