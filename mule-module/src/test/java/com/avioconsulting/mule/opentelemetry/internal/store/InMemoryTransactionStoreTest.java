package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.traces.TransactionContext;
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

import static com.avioconsulting.mule.opentelemetry.api.store.TransactionStore.TRACE_TRANSACTION_ID;
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
    TraceComponent traceComponent = TraceComponent.of("test-1", new HashMap<>())
        .withTransactionId("test-1")
        .withSpanName("GET /api/*")
        .withStartTime(startTimestamp)
        .withLocation(TEST_1_FLOW_FLOW_REF)
        .withEventContextId("test-1-context-id");
    connection.getTransactionStore().startTransaction(traceComponent, TEST_1_FLOW, spanBuilder);
    connection.getTransactionStore().addProcessorSpan(TEST_1_FLOW, traceComponent,
        tracer.spanBuilder(TEST_1_FLOW_FLOW_REF).setSpanKind(SpanKind.INTERNAL));
  }

  /**
   * Verifies that {@code endTransaction} does not throw a NullPointerException
   * when the
   * stored transaction has a null {@code rootSpanName} (e.g., caused by a pool
   * reset race
   * condition). With the {@code Objects.equals()} fix it must gracefully take the
   * child-transaction branch instead of crashing.
   */
  @Test
  public void endTransaction_doesNotThrowNPE_whenRootFlowNameIsNull() {
    // Start a transaction with a null rootName to simulate a pool-race scenario
    Instant startTimestamp = Instant.now();
    SpanBuilder spanBuilder = tracer.spanBuilder("null-name-tx")
        .setSpanKind(SpanKind.SERVER)
        .setStartTimestamp(startTimestamp);
    TraceComponent startComponent = TraceComponent.of(null, new HashMap<>())
        .withTransactionId("null-name-tx")
        .withSpanName("null-name-tx")
        .withStartTime(startTimestamp)
        .withLocation(TEST_1_FLOW_FLOW_REF)
        .withEventContextId("null-name-ctx");
    // startTransaction stores FlowTransaction with rootSpanName = null
    connection.getTransactionStore().startTransaction(startComponent, null, spanBuilder);

    // End event arrives with a non-null name
    TraceComponent endComponent = TraceComponent.of(TEST_1_FLOW, new HashMap<>())
        .withTransactionId("null-name-tx")
        .withStartTime(startTimestamp)
        .withEndTime(Instant.now())
        .withLocation(TEST_1_FLOW_FLOW_REF)
        .withEventContextId("null-name-ctx");

    // Must not throw NullPointerException
    assertThatCode(() -> connection.getTransactionStore().endTransaction(endComponent, span -> {
    })).doesNotThrowAnyException();
  }

  @Test
  public void endTransaction_return_new_tags_in_meta() {
    TraceComponent endTraceComponent = TraceComponent.of(TEST_1_FLOW, new HashMap<>()).withTransactionId("test-1")
        .withStartTime(Instant.now().minusSeconds(10))
        .withLocation(TEST_1_FLOW_FLOW_REF)
        .withEventContextId("test-1-context-id")
        .withEndTime(Instant.now());

    endTraceComponent.addTag(SemanticAttributes.MULE_APP_PROCESSOR_NAMESPACE.getKey(), "mule");
    endTraceComponent.addTag(SemanticAttributes.MULE_APP_PROCESSOR_NAME.getKey(), "flow");
    endTraceComponent.addTag("TEST_TAG_KEY", "test-tag-value");

    TransactionMeta transactionMeta = connection.getTransactionStore().endTransaction(endTraceComponent, (span -> {
    }));
    assertThat(transactionMeta).isNotNull()
        .extracting("tags", InstanceOfAssertFactories.map(String.class, String.class))
        .containsEntry("TEST_TAG_KEY", "test-tag-value");
  }

  /**
   * H1: {@code addTransactionTags} must not throw when the transaction ID is
   * unknown (i.e. the transaction was never started or has already been removed).
   */
  @Test
  public void addTransactionTags_doesNotThrowNPE_whenTransactionNotFound() {
    assertThatCode(() -> connection.getTransactionStore()
        .addTransactionTags("non-existent-tx-id", "mule.app",
            java.util.Collections.singletonMap("key", "value")))
                .doesNotThrowAnyException();
  }

  /**
   * H2: {@code getTransactionContext} with a {@code null} componentLocation must
   * not throw when the transaction ID is unknown.
   */
  @Test
  public void getTransactionContext_returnsFallbackContext_whenTransactionNotFoundAndComponentLocationIsNull() {
    String txId = "non-existent-tx-id";
    TransactionContext result = ((com.avioconsulting.mule.opentelemetry.internal.store.InMemoryTransactionStore) connection
        .getTransactionStore()).getTransactionContext(txId, null);
    assertThat(result).isNotNull();
    assertThat(result.getTraceContextMap()).containsEntry(TRACE_TRANSACTION_ID, txId);
  }

  @Test
  public void getTraceContext_returnsFallbackMap_whenTransactionNotFound() {
    String txId = "missing-tx-id";
    Map<String, Object> traceContext = connection.getTraceContext(txId);
    assertThat(traceContext).isNotNull();
    assertThat(traceContext).containsEntry(TRACE_TRANSACTION_ID, txId);
  }

  @Test
  public void getTraceContext_withComponentLocation_returnsFallbackMap_whenTransactionNotFound() {
    String txId = "missing-tx-id-with-location";
    Map<String, Object> traceContext = connection.getTraceContext(txId, "flow/processors/0");
    assertThat(traceContext).isNotNull();
    assertThat(traceContext).containsEntry(TRACE_TRANSACTION_ID, txId);
  }

  private void processAPIKitRouterComponent() {
    String name = "router:router";
    TraceComponent apikitFlow = TraceComponent.of(name, new HashMap<>())
        .withTransactionId("test-1")
        .withSpanName(name)
        .withStartTime(Instant.now().minusSeconds(10))
        .withLocation(TEST_1_FLOW + "processors/0")
        .withEventContextId("test-1-context-id")
        .withEndTime(Instant.now());
    apikitFlow.addTag(SemanticAttributes.MULE_APP_PROCESSOR_NAMESPACE.getKey(), "apikit");
    apikitFlow.addTag(SemanticAttributes.MULE_APP_PROCESSOR_NAME.getKey(), "router");
    apikitFlow.addTag(SemanticAttributes.MULE_APP_PROCESSOR_CONFIG_REF.getKey(), "order-exp-config");
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
    TraceComponent apikitFlow = TraceComponent.of(name, new HashMap<>())
        .withTransactionId("test-1")
        .withSpanName(name)
        .withStartTime(Instant.now().minusSeconds(10))
        .withLocation(name)
        .withEventContextId("test-1-context-id")
        .withEndTime(Instant.now());
    apikitFlow.addTag(SemanticAttributes.MULE_APP_PROCESSOR_NAMESPACE.getKey(), "mule");
    apikitFlow.addTag(SemanticAttributes.MULE_APP_PROCESSOR_NAME.getKey(), "flow");
    apikitFlow.addTag(SemanticAttributes.MULE_APP_FLOW_NAME.getKey(), name);
    SpanBuilder spanBuilder = tracer.spanBuilder(apikitFlow.getSpanName())
        .setSpanKind(SpanKind.INTERNAL)
        .setStartTimestamp(apikitFlow.getStartTime());
    SpanMeta spanMeta = connection.getTransactionStore().addProcessorSpan(null, apikitFlow, spanBuilder);
    assertThat(spanMeta).isNotNull();

    // There is no method exposed for checking transaction tags.
    // End transaction method returns the transaction meta, so end the root
    // transaction to verify tags.

    TraceComponent traceComponent = TraceComponent.of(TEST_1_FLOW, new HashMap<>())
        .withTransactionId("test-1")
        .withSpanName("GET /api/*")
        .withEndTime(Instant.now())
        .withLocation(TEST_1_FLOW_FLOW_REF);
    TransactionMeta transactionMeta = connection.getTransactionStore().endTransaction(traceComponent, span -> {
    });
    assertThat(transactionMeta).isNotNull()
        .extracting("tags", InstanceOfAssertFactories.map(String.class, String.class))
        .containsEntry("http.route", "/api/orders/{orderId}");

  }
}
