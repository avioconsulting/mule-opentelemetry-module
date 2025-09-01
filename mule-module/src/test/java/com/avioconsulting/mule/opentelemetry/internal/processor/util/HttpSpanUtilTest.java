package com.avioconsulting.mule.opentelemetry.internal.processor.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class HttpSpanUtilTest {

  @Test
  public void spanName() {
    List<String> httpMethods = Arrays.asList("get", "post", "put", "delete", "patch", "head", "options", "trace",
        "connect", "unknown");
    for (String httpMethod : httpMethods) {
      String spanName = HttpSpanUtil.spanName(httpMethod, "test-svc-name");
      assertThat(httpMethod.toUpperCase() + " " + "test-svc-name").isEqualTo(spanName);
    }
  }

}