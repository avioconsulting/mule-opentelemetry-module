package com.avioconsulting.mule.opentelemetry.api.config.exporter.auth;

import org.junit.Test;
import static org.assertj.core.api.Assertions.*;

public class ExporterAuthenticationTest {
  @Test
  public void validatesWithoutChangingOpaqueSecrets() {
    BearerTokenAuth bearer = new BearerTokenAuth(" opaque-secret ");
    bearer.validate();
    assertThat(bearer.getToken()).isEqualTo(" opaque-secret ");
    new ApiKeyAuth("X-Api-Key", "dummy").validate();
    new OAuth2Auth("https://localhost/token", " id ", " secret ", null).validate();
  }

  @Test
  public void rejectsUnsafeHeadersWithoutExposingValues() {
    assertThatThrownBy(() -> new BearerTokenAuth("secret\r\nheader").validate())
        .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret");
    assertThatThrownBy(() -> new ApiKeyAuth("bad:header", "dummy").validate())
        .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("dummy");
  }

  @Test
  public void requiresSecureTokenEndpointWithoutCredentialsOrFragment() {
    for (String url : new String[] { "http://localhost/token", "https://user:secret@localhost/token",
        "https://localhost/token#secret", "not a url" }) {
      assertThatThrownBy(() -> new OAuth2Auth(url, "id", "secret", null).validate())
          .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret");
    }
  }
}
