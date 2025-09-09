package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes;
import com.avioconsulting.mule.opentelemetry.internal.util.memoizers.FunctionMemoizer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import io.opentelemetry.semconv.incubating.ContainerIncubatingAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class AttributesKeyCache {

  private static final Map<String, AttributeKey<?>> attributeKeyMap = new HashMap<>(175, 0.75f);

  public AttributesKeyCache() {
  }

  private final Function<String, Long> stringToLong = FunctionMemoizer.memoize(Long::valueOf);
  private final Function<String, Double> stringToDouble = FunctionMemoizer.memoize(Double::valueOf);

  static {
    mapFields(SemanticAttributes.class.getDeclaredFields());
    mapFields(HttpAttributes.class.getDeclaredFields());
    mapFields(UrlAttributes.class.getDeclaredFields());
    mapFields(UserAgentAttributes.class.getDeclaredFields());
    mapFields(ServerAttributes.class.getDeclaredFields());
    mapFields(DbIncubatingAttributes.class.getDeclaredFields());
    mapFields(HostIncubatingAttributes.class.getDeclaredFields());
    mapFields(ContainerIncubatingAttributes.class.getDeclaredFields());
    mapFields(MessagingIncubatingAttributes.class.getDeclaredFields());
  }

  private static void mapFields(Field[] declaredFields) {
    for (Field declaredField : declaredFields) {
      if (declaredField.getType().isAssignableFrom(AttributeKey.class)) {
        try {
          AttributeKey<?> key = (AttributeKey<?>) declaredField.get(null);
          attributeKeyMap.put(key.toString(), key);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  private final Object lock = new Object();

  public AttributeKey<?> getAttributeKey(String keyString) {
    AttributeKey<?> key = attributeKeyMap.get(keyString);
    if (key == null) {
      key = AttributeKey.stringKey(keyString);
      synchronized (lock) {
        attributeKeyMap.put(keyString, key);
      }
    }
    return key;
  }

  public <T> T convertValue(AttributeKey<T> key, String value) {
    if (value == null) {
      return (T) null;
    }
    switch (key.getType()) {
      case STRING:
        return (T) value;
      case LONG:
        return (T) stringToLong.apply(value);
      case DOUBLE:
        return (T) stringToDouble.apply(value);
      case BOOLEAN:
        return (T) Boolean.valueOf(value);
      case STRING_ARRAY:
        return (T) new String[] { value };
      case LONG_ARRAY:
        return (T) new Long[] { convertValue(AttributeKey.longKey("temp"), value) };
      case DOUBLE_ARRAY:
        return (T) new Double[] { convertValue(AttributeKey.doubleKey("temp"), value) };
      case BOOLEAN_ARRAY:
        return (T) new Boolean[] { convertValue(AttributeKey.booleanKey("temp"), value) };
      default:
        throw new IllegalArgumentException("Unsupported attribute type: " + key.getType());
    }
  }

}
