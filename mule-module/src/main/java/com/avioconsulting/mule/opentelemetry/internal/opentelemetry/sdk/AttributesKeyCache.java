package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class AttributesKeyCache {

  private static final Map<String, AttributeKey<String>> attributeKeyMap = new HashMap<>();

  private AttributesKeyCache() {
  }

  static {
    mapFields(HttpAttributes.class.getDeclaredFields());
    mapFields(UrlAttributes.class.getDeclaredFields());
    mapFields(UserAgentAttributes.class.getDeclaredFields());
    mapFields(ServerAttributes.class.getDeclaredFields());
    mapFields(DbIncubatingAttributes.class.getDeclaredFields());
    mapFields(MessagingIncubatingAttributes.class.getDeclaredFields());
    mapFields(SemanticAttributes.class.getDeclaredFields());
  }

  private static void mapFields(Field[] declaredFields) {
    for (Field declaredField : declaredFields) {
      if (declaredField.getType().isAssignableFrom(AttributeKey.class)) {
        try {
          AttributeKey<String> key = (AttributeKey<String>) declaredField.get(null);
          attributeKeyMap.put(key.toString(), key);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  private final Object lock = new Object();

  public static AttributeKey<String> getAttributeKey(String keyString) {
    return attributeKeyMap.computeIfAbsent(keyString, AttributeKey::stringKey);
  }

}
