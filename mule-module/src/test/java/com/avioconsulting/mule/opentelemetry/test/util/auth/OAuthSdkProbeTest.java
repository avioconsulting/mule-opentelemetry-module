package com.avioconsulting.mule.opentelemetry.test.util.auth;

import org.junit.Test;
import static org.assertj.core.api.Assertions.*;

public class OAuthSdkProbeTest {
  public interface PublicApi {
    String value();
  }

  private static final class PrivateImplementation implements PublicApi {
    @Override
    public String value() {
      return "public-api";
    }

    public String implementationDetail() {
      return "not-an-api";
    }
  }

  @Test
  public void invokesPublicInterfaceWithoutOpeningPrivateImplementation() throws Exception {
    assertThat(OAuthSdkProbe.invoke(new PrivateImplementation(), "value")).isEqualTo("public-api");
  }

  @Test
  public void rejectsMethodsAvailableOnlyOnPrivateImplementation() {
    assertThatThrownBy(() -> OAuthSdkProbe.invoke(new PrivateImplementation(), "implementationDetail"))
        .isInstanceOf(NoSuchMethodException.class);
  }
}
