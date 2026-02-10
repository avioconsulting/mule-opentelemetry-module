package com.avioconsulting.mule.opentelemetry.jmh;

import com.avioconsulting.mule.opentelemetry.internal.util.logger.PeriodicLogger;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class PeriodicLoggerBenchmarkJMHTest extends AbstractJMHTest {

  private Logger logger;
  private PeriodicLogger periodicLogger;

  @Setup
  public void setup() {
    logger = LoggerFactory.getLogger("test");
    periodicLogger = PeriodicLogger.builder()
        .interval(60, TimeUnit.SECONDS)
        .build();
  }

  @Benchmark
  public void suppressedLogCall(Blackhole bh) {
    // First call logs, rest suppressed
    periodicLogger.error(logger, "Test error");
    bh.consume(periodicLogger);
  }

  @Benchmark
  public void directLogCall(Blackhole bh) {
    // Baseline - direct SLF4J call
    if (logger.isErrorEnabled()) {
      logger.error("Test error");
    }
    bh.consume(logger);
  }

  @Benchmark
  @Threads(10)
  public void concurrentSuppressedCalls(Blackhole bh) {
    periodicLogger.error(logger, "Concurrent error");
    bh.consume(periodicLogger);
  }

  @Override
  public int getIterations() {
    return 2;
  }

  @Override
  public int getWarmupIterations() {
    return 3;
  }
}
