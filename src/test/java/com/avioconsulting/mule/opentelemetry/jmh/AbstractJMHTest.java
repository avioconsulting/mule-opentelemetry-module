package com.avioconsulting.mule.opentelemetry.jmh;

import org.junit.Assume;
import org.junit.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * To run JMH, set runJMH=true on system properties.
 * Eg. Run maven command - `./mvnw test -DrunJMH=true`
 */

public abstract class AbstractJMHTest {
  @Test
  public void runBenchmarks() throws Exception {
    Assume.assumeTrue(System.getProperty("runJMH", "false").equals("true"));
    Options options = new OptionsBuilder()
        .include(getClass().getSimpleName().concat(".*"))
        .mode(Mode.Throughput)
        .warmupTime(TimeValue.seconds(1))
        .warmupIterations(getWarmupIterations())
        .threads(1)
        .measurementIterations(getIterations())
        .forks(1)
        .shouldFailOnError(true)
        .shouldDoGC(true)
        .build();

    new Runner(options).run();
  }

  public int getWarmupIterations() {
    return 6;
  }

  public int getIterations() {
    return 6;
  }
}
