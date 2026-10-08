package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import io.opentelemetry.api.common.AttributeKey;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AttributesKeyCacheTest {

  private final AttributesKeyCache attributesKeyCache = new AttributesKeyCache();

  @Test
  public void convertValue_long() {
    assertThat(attributesKeyCache.convertValue(AttributeKey.longKey("server.port"), "8081"))
        .isEqualTo(8081L);
  }

  @Test
  public void convertValue_longWithUnresolvedExpression() {
    // GitHub# issues/328
    assertThat(attributesKeyCache.convertValue(AttributeKey.longKey("server.port"),
        "#[vars.dynamicConfig.port default 443]")).isNull();
  }

  @Test
  public void convertValue_doubleWithNonNumericValue() {
    assertThat(attributesKeyCache.convertValue(AttributeKey.doubleKey("test.double"), "not-a-number"))
        .isNull();
  }

  @Test
  public void convertValue_longArrayWithNonNumericValue() {
    assertThat(attributesKeyCache.convertValue(AttributeKey.longArrayKey("test.longs"), "not-a-number"))
        .isNull();
  }

  @Test
  public void convertValue_nullValue() {
    assertThat(attributesKeyCache.convertValue(AttributeKey.longKey("server.port"), null)).isNull();
  }

  @Test
  public void convertValue_serverPortKeyIsLong() {
    AttributeKey<?> key = attributesKeyCache.getAttributeKey("server.port");
    assertThat(attributesKeyCache.convertValue(key, "443")).isEqualTo(443L);
    assertThat(attributesKeyCache.convertValue(key, "#[vars.port]")).isNull();
  }
}
