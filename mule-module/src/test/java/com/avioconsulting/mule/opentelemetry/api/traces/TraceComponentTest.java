package com.avioconsulting.mule.opentelemetry.api.traces;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnitParamsRunner.class)
public class TraceComponentTest {

  @Test
  public void testContextNestingLevel() {
    TraceComponent traceComponent = TraceComponent.of("Test", new HashMap<>())
        .withEventContextId("58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100_894835844_515059234");

    assertThat(traceComponent.contextNestingLevel())
        .isEqualTo(5);
  }

  @Test
  public void testGetEventContextPrimaryId() {
    TraceComponent traceComponent = TraceComponent.of("Test", new HashMap<>())
        .withEventContextId("58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100_894835844_515059234");

    assertThat(traceComponent.getEventContextPrimaryId())
        .isEqualTo("58660cf1-e735-11ee-bd25-ca89f39a1b64");
  }

  @Test
  public void testContextScopedPath() {
    TraceComponent traceComponent = TraceComponent.of("Test", new HashMap<>())
        .withEventContextId("58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100_894835844_515059234");

    assertThat(traceComponent.contextScopedPath("test-location-path"))
        .isEqualTo(
            "58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100_894835844_515059234/test-location-path");
  }

  @Test
  public void testPrevContextScopedPath() {
    TraceComponent traceComponent = TraceComponent.of("Test", new HashMap<>())
        .withEventContextId("58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100_894835844_515059234");

    assertThat(traceComponent.prevContextScopedPath("test-location-path"))
        .isPresent()
        .get(InstanceOfAssertFactories.STRING)
        .isEqualTo("58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100_894835844/test-location-path");
  }

  @Test
  @Parameters({
      "0,58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100_894835844_515059234/test-location-path",
      "1,58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100_894835844/test-location-path",
      "2,58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100/test-location-path",
      "3,58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029/test-location-path",
      "4,58660cf1-e735-11ee-bd25-ca89f39a1b64/test-location-path",
  })
  public void testContextScopedPathWithLevel(int level, String expectedPath) throws Exception {
    TraceComponent traceComponent = TraceComponent.of("Test", new HashMap<>())
        .withEventContextId("58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100_894835844_515059234");
    assertThat(traceComponent.contextScopedPath("test-location-path", level))
        .isEqualTo(expectedPath);
  }

}