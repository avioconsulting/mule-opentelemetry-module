package com.avioconsulting.mule.opentelemetry.api.config.exporter.auth;

import org.junit.Test;
import static org.assertj.core.api.Assertions.*;

public class ExporterAuthenticationTest {

  @Test
  public void credentialsAreMaskedAndAllParametersRejectEventExpressions() throws Exception {
    for (Class<?> type : new Class<?>[] { BearerTokenAuth.class, ApiKeyAuth.class, OAuth2Auth.class }) {
      for (java.lang.reflect.Field field : type.getDeclaredFields()) {
        if (field.isAnnotationPresent(org.mule.runtime.extension.api.annotation.param.Parameter.class)) {
          assertThat(field.getAnnotation(org.mule.runtime.extension.api.annotation.Expression.class).value())
              .isEqualTo(org.mule.runtime.api.meta.ExpressionSupport.NOT_SUPPORTED);
        }
      }
    }
    assertThat(BearerTokenAuth.class.getDeclaredField("token").isAnnotationPresent(
        org.mule.runtime.extension.api.annotation.param.display.Password.class)).isTrue();
    assertThat(ApiKeyAuth.class.getDeclaredField("value").isAnnotationPresent(
        org.mule.runtime.extension.api.annotation.param.display.Password.class)).isTrue();
    assertThat(OAuth2Auth.class.getDeclaredField("clientSecret").isAnnotationPresent(
        org.mule.runtime.extension.api.annotation.param.display.Password.class)).isTrue();
  }

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
