package com.avioconsulting.mule.opentelemetry.test.util.auth;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import static org.assertj.core.api.Assertions.*;

/**
 * Bridges the Mule artifact loader without replacing any connection, provider
 * or exporter.
 */
public final class OAuthSdkProbe {
  public final Object sdk;
  public final Object provider;

  public OAuthSdkProbe(Object connection, AtomicLong clock) throws Exception {
    sdk = field(connection, "sdk");
    provider = field(connection, "authenticationProvider");
    Method hook = provider.getClass().getDeclaredMethod("useTimeSourcesForTest", LongSupplier.class,
        DoubleSupplier.class);
    hook.setAccessible(true);
    hook.invoke(provider, (LongSupplier) clock::get, (DoubleSupplier) () -> 0.5);
  }

  public void emit(String marker) throws Exception {
    Object tracer = invoke(sdk, "getTracer", "oauth-e2e");
    Object span = invoke(invoke(tracer, "spanBuilder", marker + "-trace"), "startSpan");
    invoke(span, "end");
    Object meter = invoke(sdk, "getMeter", "oauth-e2e");
    Object counter = invoke(invoke(meter, "counterBuilder", marker.replace('-', '_') + "_metric"), "build");
    invoke(counter, "add", 1L);
    Object logger = invoke(invoke(sdk, "getSdkLoggerProvider"), "get", "oauth-e2e");
    Object record = invoke(logger, "logRecordBuilder");
    invoke(record, "setBody", marker + "-log");
    invoke(record, "emit");
  }

  public void flush() throws Exception {
    flush("getSdkTracerProvider");
    flush("getSdkMeterProvider");
    flush("getSdkLoggerProvider");
  }

  public void flush(String accessor) throws Exception {
    Object result = invoke(invoke(sdk, accessor), "forceFlush");
    invoke(result, "join", 5L, TimeUnit.SECONDS);
    assertThat(invoke(result, "isDone")).as("SDK flush must complete within its budget").isEqualTo(true);
  }

  public static Object field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  public static Object invoke(Object target, String name, Object... args) throws Exception {
    for (Method method : target.getClass().getMethods()) {
      if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
        continue;
      }
      Class<?>[] parameters = method.getParameterTypes();
      boolean match = true;
      for (int i = 0; i < parameters.length; i++) {
        if (!parameters[i].isInstance(args[i]) && !(parameters[i] == long.class && args[i] instanceof Long)) {
          match = false;
          break;
        }
      }
      if (match) {
        method.setAccessible(true);
        try {
          return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
          if (failure.getCause() instanceof Exception) {
            throw (Exception) failure.getCause();
          }
          throw failure;
        }
      }
    }
    throw new NoSuchMethodException(target.getClass().getName() + "." + name);
  }
}
