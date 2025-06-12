package com.avioconsulting.mule.opentelemetry.internal.util;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

public class ServiceProviderUtil {

  private ServiceProviderUtil() {

  }

  public static <T, R extends List<T>> R load(ClassLoader classLoader, Class<T> spiClass, R accumulator) {
    Objects.requireNonNull(accumulator);
    for (T service : ServiceLoader.load(spiClass, classLoader)) {
      accumulator.add(service);
    }
    return accumulator;
  }
}
