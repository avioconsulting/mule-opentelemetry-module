package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;

/**
 * Reads the Semantic Convention stability flag.
 * Could have used
 * {@link io.opentelemetry.instrumentation.api.internal.SemconvStability} but
 * that is marked as internal
 * so replicating it here for now.
 */
public class SemconvStability {

  private static boolean emitOldHttpSemconv;
  private static boolean emitStableHttpSemconv;
  private static boolean emitOldJvmSemconv;
  private static boolean emitStableJvmSemconv;

  static {
    init();
  }

  public static void init() {
    boolean oldHttp = true;
    boolean stableHttp = false;
    boolean oldJvm = true;
    boolean stableJvm = false;

    String value = System.getProperty("otel.semconv-stability.opt-in",
        System.getenv("OTEL_SEMCONV_STABILITY_OPT_IN"));

    if (value != null) {
      Set<String> values = new HashSet<>(asList(value.split(",")));
      if (values.contains("http")) {
        oldHttp = false;
        stableHttp = true;
      }
      // no else -- technically it's possible to set "http,http/dup", in which case we
      // should emit
      // both sets of attributes
      if (values.contains("http/dup")) {
        oldHttp = true;
        stableHttp = true;
      }

      if (values.contains("jvm")) {
        oldJvm = false;
        stableJvm = true;
      }
      if (values.contains("jvm/dup")) {
        oldJvm = true;
        stableJvm = true;
      }
    }

    emitOldHttpSemconv = oldHttp;
    emitStableHttpSemconv = stableHttp;
    emitOldJvmSemconv = oldJvm;
    emitStableJvmSemconv = stableJvm;
  }

  public static boolean emitOldHttpSemconv() {
    return emitOldHttpSemconv;
  }

  public static boolean emitStableHttpSemconv() {
    return emitStableHttpSemconv;
  }

  public static boolean emitOldJvmSemconv() {
    return emitOldJvmSemconv;
  }

  public static boolean emitStableJvmSemconv() {
    return emitStableJvmSemconv;
  }

  private SemconvStability() {
  }

}
