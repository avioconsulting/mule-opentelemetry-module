package com.avioconsulting.mule.opentelemetry.jmh;

import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.SpanProcessorConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.LoggingExporter;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryConfigWrapper;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.mule.runtime.dsl.api.component.config.DefaultComponentLocation;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class InMemoryTransactionStoreTest extends AbstractJMHTest {

  public static final String TEST_1_FLOW_FLOW_REF = "/test-1-flow/flow-ref";
  public static final DefaultComponentLocation COMPONENT_LOCATION = DefaultComponentLocation
      .fromSingleComponent(TEST_1_FLOW_FLOW_REF);
  public static final String TEST_1_FLOW = "test-1-flow";
  OpenTelemetryConnection connection;

  @Setup
  public void setup() {
    OpenTelemetryResource resource = new OpenTelemetryResource();
    OpenTelemetryExporter exporter = new LoggingExporter();
    OpenTelemetryConfigWrapper wrapper = new OpenTelemetryConfigWrapper(resource, exporter,
        new SpanProcessorConfiguration());
    connection = OpenTelemetryConnection.getInstance(wrapper);

    Tracer tracer = GlobalOpenTelemetry.get().getTracer("test", "v1");
    SpanBuilder spanBuilder = tracer.spanBuilder("test-transaction")
        .setSpanKind(SpanKind.SERVER)
        .setStartTimestamp(Instant.now());
    connection.getTransactionStore().startTransaction("test-1", TEST_1_FLOW, spanBuilder);

    connection.getTransactionStore().addProcessorSpan("test-1", TEST_1_FLOW, TEST_1_FLOW_FLOW_REF,
        tracer.spanBuilder(TEST_1_FLOW_FLOW_REF).setSpanKind(SpanKind.INTERNAL));
  }

  @Benchmark
  public void getTransactionContext(Blackhole blackhole) {
    Context transactionContext = connection.getTransactionStore().getTransactionContext("test-1", null);
    blackhole.consume(transactionContext);
  }

  @Benchmark
  public void getTransactionComponentContext(Blackhole blackhole) {
    Context transactionContext = connection.getTransactionStore().getTransactionContext("test-1",
        COMPONENT_LOCATION);
    blackhole.consume(transactionContext);
  }

  @Benchmark
  public void getTraceContext(Blackhole blackhole) {
    Map<String, String> transactionContext = connection.getTraceContext("test-1");
    blackhole.consume(transactionContext);
  }

  @Benchmark
  public void getTraceContextComponent(Blackhole blackhole) {
    Map<String, String> transactionContext = connection.getTraceContext("test-1",
        COMPONENT_LOCATION);
    blackhole.consume(transactionContext);
  }
}
