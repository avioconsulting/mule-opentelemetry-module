package com.avioconsulting.mule.opentelemetry.test.util.auth;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
    sdk = invoke(connection, "_getSdkForTest");
    provider = invoke(connection, "_getAuthenticationProviderForTest");
    invoke(provider, "_useTimeSourcesForTest", (LongSupplier) clock::get, (DoubleSupplier) () -> 0.5);
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

  /**
   * Calls only public methods declared by public API types, never private SDK
   * implementation members.
   */
  public static Object invoke(Object target, String name, Object... args) throws Exception {
    Method method = publicMethod(target.getClass(), name, args);
    if (method == null) {
      throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof Exception) {
        throw (Exception) failure.getCause();
      }
      throw failure;
    }
  }

  private static Method publicMethod(Class<?> type, String name, Object[] args) {
    if (type == null) {
      return null;
    }
    if (Modifier.isPublic(type.getModifiers())) {
      for (Method method : type.getMethods()) {
        if (!Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
          continue;
        }
        if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
          continue;
        }
        Class<?>[] parameters = method.getParameterTypes();
        boolean match = true;
        for (int i = 0; i < parameters.length; i++) {
          if (!parameters[i].isInstance(args[i])
              && !(parameters[i] == long.class && args[i] instanceof Long)) {
            match = false;
            break;
          }
        }
        if (match) {
          return method;
        }
      }
    }
    // SDK implementations can be package-private. Invoke their public interface
    // declarations instead.
    for (Class<?> contract : type.getInterfaces()) {
      Method method = publicMethod(contract, name, args);
      if (method != null) {
        return method;
      }
    }
    return publicMethod(type.getSuperclass(), name, args);
  }
}
