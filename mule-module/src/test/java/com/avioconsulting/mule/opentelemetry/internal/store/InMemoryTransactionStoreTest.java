package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.LoggingExporter;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes;
import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
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

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

public class InMemoryTransactionStoreTest {

  public static final String TEST_1_FLOW_FLOW_REF = "/test-1-flow/flow-ref";
  public static final DefaultComponentLocation COMPONENT_LOCATION = DefaultComponentLocation
      .fromSingleComponent(TEST_1_FLOW_FLOW_REF);
  public static final String TEST_1_FLOW = "test-1-flow";
  OpenTelemetryConnection connection;
  private Tracer tracer;

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

    this.tracer = GlobalOpenTelemetry.get().getTracer("test", "v1");
    Instant startTimestamp = Instant.now();
    SpanBuilder spanBuilder = tracer.spanBuilder("test-transaction")
        .setSpanKind(SpanKind.SERVER)
        .setStartTimestamp(startTimestamp);
    TraceComponent traceComponent = TraceComponent.of("test-1")
        .withTransactionId("test-1")
        .withSpanName("GET /api/*")
        .withStartTime(startTimestamp)
        .withLocation(TEST_1_FLOW_FLOW_REF)
        .withEventContextId("test-1-context-id")
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
    assertThat(transactionMeta).isNotNull()
        .extracting("tags", InstanceOfAssertFactories.map(String.class, String.class))
        .containsEntry("TEST_TAG_KEY", "test-tag-value");
  }

  private void processAPIKitRouterComponent() {
    String name = "router:router";
    TraceComponent apikitFlow = TraceComponent.of(name)
        .withTransactionId("test-1")
        .withSpanName(name)
        .withStartTime(Instant.now().minusSeconds(10))
        .withLocation(TEST_1_FLOW + "processors/0")
        .withEventContextId("test-1-context-id")
        .withEndTime(Instant.now())
        .withTags(new HashMap<>());
    apikitFlow.getTags().put(SemanticAttributes.MULE_APP_PROCESSOR_NAMESPACE.getKey(), "apikit");
    apikitFlow.getTags().put(SemanticAttributes.MULE_APP_PROCESSOR_NAME.getKey(), "router");
    apikitFlow.getTags().put(SemanticAttributes.MULE_APP_PROCESSOR_CONFIG_REF.getKey(), "order-exp-config");
    SpanBuilder spanBuilder = tracer.spanBuilder(apikitFlow.getSpanName())
        .setSpanKind(SpanKind.INTERNAL)
        .setStartTimestamp(apikitFlow.getStartTime());
    connection.getTransactionStore().addProcessorSpan(null, apikitFlow, spanBuilder);
  }

  @Test
  public void resetting_span_name_is_updated_in_transaction() {
    // Add APIKit router component to set the apikit config name on root transaction
    processAPIKitRouterComponent();

    // Process an APIKit Flow span to trigger http route renaming
    String name = "get:\\orders\\(orderId):order-exp-config";
    TraceComponent apikitFlow = TraceComponent.of(name)
        .withTransactionId("test-1")
        .withSpanName(name)
        .withStartTime(Instant.now().minusSeconds(10))
        .withLocation(name)
        .withEventContextId("test-1-context-id")
        .withEndTime(Instant.now())
        .withTags(new HashMap<>());
    apikitFlow.getTags().put(SemanticAttributes.MULE_APP_PROCESSOR_NAMESPACE.getKey(), "mule");
    apikitFlow.getTags().put(SemanticAttributes.MULE_APP_PROCESSOR_NAME.getKey(), "flow");
    apikitFlow.getTags().put(SemanticAttributes.MULE_APP_FLOW_NAME.getKey(), name);
    SpanBuilder spanBuilder = tracer.spanBuilder(apikitFlow.getSpanName())
        .setSpanKind(SpanKind.INTERNAL)
        .setStartTimestamp(apikitFlow.getStartTime());
    SpanMeta spanMeta = connection.getTransactionStore().addProcessorSpan(null, apikitFlow, spanBuilder);
    assertThat(spanMeta).isNotNull();

    // There is no method exposed for checking transaction tags.
    // End transaction method returns the transaction meta, so end the root
    // transaction to verify tags.

    TraceComponent traceComponent = TraceComponent.of(TEST_1_FLOW)
        .withTransactionId("test-1")
        .withSpanName("GET /api/*")
        .withEndTime(Instant.now())
        .withLocation(TEST_1_FLOW_FLOW_REF)
        .withTags(new HashMap<>());
    TransactionMeta transactionMeta = connection.getTransactionStore().endTransaction(traceComponent, span -> {
    });
    assertThat(transactionMeta).isNotNull()
        .extracting("tags", InstanceOfAssertFactories.map(String.class, String.class))
        .containsEntry("http.route", "/api/orders/{orderId}");

  }
}