package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.LoggingExporter;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryConfigWrapper;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.processor.FlowProcessorComponent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mule.runtime.dsl.api.component.config.DefaultComponentLocation;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class InMemoryTransactionStoreTest {

  public static final String TEST_1_FLOW_FLOW_REF = "/test-1-flow/flow-ref";
  public static final DefaultComponentLocation COMPONENT_LOCATION = DefaultComponentLocation
      .fromSingleComponent(TEST_1_FLOW_FLOW_REF);
  public static final String TEST_1_FLOW = "test-1-flow";
  OpenTelemetryConnection connection;

  @Before
  public void setUp() {
    OpenTelemetryResource resource = new OpenTelemetryResource();
    OpenTelemetryExporter exporter = new LoggingExporter();
    OpenTelemetryExtensionConfiguration configuration = new OpenTelemetryExtensionConfiguration();
    ExporterConfiguration exporterConfiguration = new ExporterConfiguration();
    exporterConfiguration.setExporter(exporter);

    configuration.setExporterConfiguration(exporterConfiguration);

    OpenTelemetryConfigWrapper wrapper = new OpenTelemetryConfigWrapper(configuration);
    connection = OpenTelemetryConnection.getInstance(wrapper);

    Tracer tracer = GlobalOpenTelemetry.get().getTracer("test", "v1");
    Instant startTimestamp = Instant.now();
    SpanBuilder spanBuilder = tracer.spanBuilder("test-transaction")
        .setSpanKind(SpanKind.SERVER)
        .setStartTimestamp(startTimestamp);
    TraceComponent traceComponent = TraceComponent.of("test-1").withTransactionId("test-1")
        .withStartTime(startTimestamp)
        .withLocation(TEST_1_FLOW_FLOW_REF)
        .withTags(new HashMap<>());
    connection.getTransactionStore().startTransaction(traceComponent, TEST_1_FLOW, spanBuilder);
    connection.getTransactionStore().addProcessorSpan(TEST_1_FLOW, traceComponent,
        tracer.spanBuilder(TEST_1_FLOW_FLOW_REF).setSpanKind(SpanKind.INTERNAL));
  }

  @Test
  public void endTransaction_return_new_tags_in_meta() {
    TraceComponent endTraceComponent = TraceComponent.of(TEST_1_FLOW).withTransactionId("test-1")
        .withStartTime(Instant.now().minusSeconds(10))
        .withLocation(TEST_1_FLOW_FLOW_REF)
        .withEventContextId("test-1-context-id")
        .withEndTime(Instant.now())
        .withTags(new HashMap<>());

    endTraceComponent.getTags().put(SemanticAttributes.MULE_APP_PROCESSOR_NAMESPACE.getKey(), "mule");
    endTraceComponent.getTags().put(SemanticAttributes.MULE_APP_PROCESSOR_NAME.getKey(), "flow");
    endTraceComponent.getTags().put("TEST_TAG_KEY", "test-tag-value");

    TransactionMeta transactionMeta = connection.getTransactionStore().endTransaction(endTraceComponent, (span -> {
    }));
    Assertions.assertThat(transactionMeta).isNotNull()
        .extracting("tags", InstanceOfAssertFactories.map(String.class, String.class))
        .containsEntry("TEST_TAG_KEY", "test-tag-value");
  }
}