package com.avioconsulting.mule.opentelemetry.internal.config;

import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import com.avioconsulting.mule.opentelemetry.internal.AbstractInternalTest;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.notifications.listeners.MuleMessageProcessorNotificationListener;
import com.avioconsulting.mule.opentelemetry.internal.notifications.listeners.MulePipelineMessageNotificationListener;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentRegistryService;
import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.test.util.auth.ScopedSystemProperties;
import io.opentelemetry.api.trace.Span;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.NotificationListenerRegistry;
import org.mule.runtime.core.api.el.ExpressionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.*;

@RunWith(MockitoJUnitRunner.class)
public class OpenTelemetryExtensionConfigurationTest extends AbstractInternalTest {

  @Rule
  public final ScopedSystemProperties properties = new ScopedSystemProperties();

  @Mock
  NotificationListenerRegistry notificationListenerRegistry;
  @Mock
  MuleNotificationProcessor muleNotificationProcessor;
  @Mock
  TraceLevelConfiguration traceLevelConfiguration;

  @Mock
  OpenTelemetryExporter exporter;
  @Mock
  ExporterConfiguration exporterConfiguration;
  @Mock
  ExpressionManager expressionManager;
  @InjectMocks
  OpenTelemetryExtensionConfiguration extensionConfiguration;

  @Before
  public void setUp() throws Exception {
    when(exporterConfiguration.getExporter()).thenReturn(exporter);
    extensionConfiguration.setExporterConfiguration(exporterConfiguration);
    when(expressionManager.evaluate(anyString(), any(DataType.class))).thenReturn(TypedValue.of("TestValue"));
  }
  //
  // @Test
  // public void getMetricInstrumentDefinitionMap_OtelKeysForbidden() {
  // List<CustomMetricInstrumentDefinition> instruments = new ArrayList<>();
  // instruments.add(new
  // CustomMetricInstrumentDefinition().setMetricName("valid.name"));
  // instruments.add(new
  // CustomMetricInstrumentDefinition().setMetricName("otel.name"));
  // OpenTelemetryExtensionConfiguration configuration = new
  // OpenTelemetryExtensionConfiguration()
  // .setCustomMetricInstruments(instruments);
  // MuleRuntimeException muleRuntimeException = catchThrowableOfType(
  // () -> configuration.getMetricInstrumentDefinitionMap(),
  // MuleRuntimeException.class);
  // assertThat(muleRuntimeException)
  // .isNotNull()
  // .hasMessage("Instrument names cannot use reserved namespaces - otel.*");
  // }
  //
  // @Test
  // public void getMetricInstrumentDefinitionMap() {
  // List<CustomMetricInstrumentDefinition> instruments = new ArrayList<>();
  // CustomMetricInstrumentDefinition instrument1 = new
  // CustomMetricInstrumentDefinition()
  // .setMetricName("custom.instrument.1");
  // instruments.add(instrument1);
  // CustomMetricInstrumentDefinition instrument2 = new
  // CustomMetricInstrumentDefinition()
  // .setMetricName("custom.instrument.2");
  // instruments.add(instrument2);
  // OpenTelemetryExtensionConfiguration configuration = new
  // OpenTelemetryExtensionConfiguration()
  // .setCustomMetricInstruments(instruments);
  // assertThat(configuration.getMetricInstrumentDefinitionMap())
  // .containsEntry("custom.instrument.1", instrument1)
  // .containsEntry("custom.instrument.2", instrument2);
  // }

  @Test
  public void isTurnOffTracing_fromProperties() {
    OpenTelemetryExtensionConfiguration configuration = new OpenTelemetryExtensionConfiguration()
        .setTurnOffTracing(false);
    System.setProperty(OpenTelemetryExtensionConfiguration.PROP_MULE_OTEL_TRACING_DISABLED, "true");
    assertThat(configuration.isTurnOffTracing()).isTrue();
    System.clearProperty(OpenTelemetryExtensionConfiguration.PROP_MULE_OTEL_TRACING_DISABLED);
  }

  @Test
  public void isTurnOffTracing_EnabledFromConfig() {
    OpenTelemetryExtensionConfiguration configuration = new OpenTelemetryExtensionConfiguration()
        .setTurnOffTracing(true);
    System.setProperty(OpenTelemetryExtensionConfiguration.PROP_MULE_OTEL_TRACING_DISABLED, "false");
    assertThat(configuration.isTurnOffTracing()).isFalse();
    System.clearProperty(OpenTelemetryExtensionConfiguration.PROP_MULE_OTEL_TRACING_DISABLED);
  }

  @Test
  public void isTurnOffTracing_fromConfig() {
    OpenTelemetryExtensionConfiguration configuration = new OpenTelemetryExtensionConfiguration()
        .setTurnOffTracing(true);
    assertThat(configuration.isTurnOffTracing()).isTrue();
  }

  @Test
  public void isTurnOffTracing_defaultFalse() {
    OpenTelemetryExtensionConfiguration configuration = new OpenTelemetryExtensionConfiguration();
    assertThat(configuration.isTurnOffTracing()).isFalse();
  }

  @Test
  public void verifyStartActivities() throws MuleException {
    assertThat(extensionConfiguration.notificationListenerRegistry).isNotNull();
    MuleNotificationProcessor spy = spy(muleNotificationProcessor);
    extensionConfiguration.muleNotificationProcessor = spy;
    doNothing().when(spy).init(any(OpenTelemetryConnection.class), any(TraceLevelConfiguration.class));
    extensionConfiguration.start();
    verify(notificationListenerRegistry).registerListener(any(MuleMessageProcessorNotificationListener.class));
    verify(notificationListenerRegistry).registerListener(any(MulePipelineMessageNotificationListener.class));
    // verify(notificationListenerRegistry).registerListener(any(MetricEventNotificationListener.class),
    // any(Predicate.class));
    verify(spy).init(any(OpenTelemetryConnection.class), any(TraceLevelConfiguration.class));
    extensionConfiguration.stop();
  }

  @Test
  public void startupFailureClosesTheAlreadyCreatedConnection() throws Exception {
    doThrow(new IllegalStateException("startup failure")).when(traceLevelConfiguration).initMuleComponentsMap();
    org.assertj.core.api.Assertions.assertThatThrownBy(extensionConfiguration::start)
        .isInstanceOf(IllegalStateException.class).hasMessage("startup failure");
    assertThat(extensionConfiguration.getOpenTelemetryConnection().isClosed()).isTrue();
    extensionConfiguration.stop();
  }

  @Test
  public void restartExportsBatchChildSpansThroughTheNewConnection() throws Exception {
    properties.set("otel.traces.exporter", "delegatedLogging");
    properties.set("otel.traces.sampler", "always_on");
    properties.set("otel.metrics.exporter", "none");
    properties.set("otel.logs.exporter", "none");
    when(muleNotificationProcessor.getComponentRegistryService())
        .thenReturn(mock(ComponentRegistryService.class));
    DelegatedLoggingSpanTestExporter.spanQueue.clear();
    try {
      extensionConfiguration.start();
      OpenTelemetryConnection oldConnection = extensionConfiguration.getOpenTelemetryConnection();
      runBatchAndFlush(oldConnection, "before-restart");
      assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
          .extracting(DelegatedLoggingSpanTestExporter.Span::getSpanName)
          .contains("before-restart", BATCH_STEP_TAG + ":step", "processor");

      extensionConfiguration.stop();
      assertThat(oldConnection.isClosed()).isTrue();
      // Do not reset the singleton here: this must exercise the real restart path.
      extensionConfiguration.start();
      OpenTelemetryConnection newConnection = extensionConfiguration.getOpenTelemetryConnection();
      assertThat(newConnection).isNotSameAs(oldConnection);
      assertThat(newConnection._getSdkForTest()).isNotSameAs(oldConnection._getSdkForTest());
      DelegatedLoggingSpanTestExporter.spanQueue.clear();
      Span control = newConnection.spanBuilder("new-sdk-control").startSpan();
      control.end();
      runBatchAndFlush(newConnection, "after-restart");

      assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
          .extracting(DelegatedLoggingSpanTestExporter.Span::getSpanName)
          .as("the recreated SDK exports control and batch root/processor spans")
          .contains("new-sdk-control", "after-restart", "processor");
      assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
          .extracting(DelegatedLoggingSpanTestExporter.Span::getSpanName)
          .as("batch step spans must also use the recreated SDK")
          .contains(BATCH_STEP_TAG + ":step");
    } finally {
      extensionConfiguration.stop();
      OpenTelemetryConnection._resetForTest();
      DelegatedLoggingSpanTestExporter.spanQueue.clear();
    }
  }

  private void runBatchAndFlush(OpenTelemetryConnection connection, String transactionId) {
    completeBatchAndFlush(connection, startBatch(connection, transactionId));
  }

  @Test
  public void simultaneousConnectionsKeepSameIdBatchTransactionsAndRegistriesIsolated() {
    configureBatchExporter();
    ComponentRegistryService firstRegistry = mock(ComponentRegistryService.class);
    ComponentRegistryService secondRegistry = mock(ComponentRegistryService.class);
    OpenTelemetryConnection first = OpenTelemetryConnection
        .getInstance(new OpenTelemetryConfigWrapper(extensionConfiguration))
        .setComponentRegistryService(firstRegistry);
    OpenTelemetryConnection second = null;
    DelegatedLoggingSpanTestExporter.spanQueue.clear();
    try {
      second = OpenTelemetryConnection.getInstance(new OpenTelemetryConfigWrapper(extensionConfiguration))
          .setComponentRegistryService(secondRegistry);
      assertThat(second._getSdkForTest()).isNotSameAs(first._getSdkForTest());
      assertThat(second.getTransactionStore()).isNotSameAs(first.getTransactionStore());
      TraceComponent firstBatch = startBatch(first, "shared-id");
      String firstTraceId = first.getTransactionStore().getTransactionContext("shared-id", null).getTraceId();
      assertThat(second.getTransactionStore().getTransactionContext("shared-id", null).getTraceId())
          .isEqualTo(io.opentelemetry.api.trace.TraceId.getInvalid());
      TraceComponent secondBatch = startBatch(second, "shared-id");
      String secondTraceId = second.getTransactionStore().getTransactionContext("shared-id", null).getTraceId();
      assertThat(secondTraceId).isNotEqualTo(firstTraceId);
      completeBatchAndFlush(second, secondBatch);
      verify(secondRegistry, atLeastOnce()).findComponentByLocation("batch-job/steps/0");
      verifyZeroInteractions(firstRegistry);
      assertBatchParents(secondTraceId, "shared-id");
      assertThat(first.getTransactionStore().getTransactionContext("shared-id", null).getTraceId())
          .isEqualTo(firstTraceId);
      completeBatchAndFlush(first, firstBatch);
      verify(firstRegistry, atLeastOnce()).findComponentByLocation("batch-job/steps/0");
      assertBatchParents(firstTraceId, "shared-id");
    } finally {
      first.invalidate();
      if (second != null) {
        second.invalidate();
      }
      DelegatedLoggingSpanTestExporter.spanQueue.clear();
    }
  }

  @Test
  public void restartDoesNotReuseAnOldInFlightBatchTransaction() throws Exception {
    configureBatchExporter();
    ComponentRegistryService oldRegistry = mock(ComponentRegistryService.class);
    ComponentRegistryService newRegistry = mock(ComponentRegistryService.class);
    when(muleNotificationProcessor.getComponentRegistryService()).thenReturn(oldRegistry, newRegistry);
    DelegatedLoggingSpanTestExporter.spanQueue.clear();
    OpenTelemetryConnection oldConnection = null;
    TraceComponent oldBatch = null;
    try {
      extensionConfiguration.start();
      oldConnection = extensionConfiguration.getOpenTelemetryConnection();
      oldBatch = startBatch(oldConnection, "in-flight-id");
      String oldTraceId = oldConnection.getTransactionStore().getTransactionContext("in-flight-id", null)
          .getTraceId();
      extensionConfiguration.stop();
      assertThat(oldConnection.isClosed()).isTrue();
      extensionConfiguration.start();
      OpenTelemetryConnection replacement = extensionConfiguration.getOpenTelemetryConnection();
      assertThat(replacement.getTransactionStore()).isNotSameAs(oldConnection.getTransactionStore());
      assertThat(replacement.getComponentRegistryService()).isSameAs(newRegistry);
      assertThat(replacement.getTransactionStore().getTransactionContext("in-flight-id", null).getTraceId())
          .isEqualTo(io.opentelemetry.api.trace.TraceId.getInvalid());
      TraceComponent replacementBatch = startBatch(replacement, "in-flight-id");
      String newTraceId = replacement.getTransactionStore().getTransactionContext("in-flight-id", null)
          .getTraceId();
      assertThat(newTraceId).isNotEqualTo(oldTraceId);
      completeBatchAndFlush(replacement, replacementBatch);
      verify(newRegistry, atLeastOnce()).findComponentByLocation("batch-job/steps/0");
      verifyZeroInteractions(oldRegistry);
      assertBatchParents(newTraceId, "in-flight-id");
      assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
          .extracting(DelegatedLoggingSpanTestExporter.Span::getTraceId).doesNotContain(oldTraceId);
      assertThat(oldConnection.getTransactionStore().getTransactionContext("in-flight-id", null).getTraceId())
          .isEqualTo(oldTraceId);
    } finally {
      if (oldBatch != null) {
        oldBatch.withEndTime(Instant.now());
        oldConnection.getTransactionStore().endTransaction(oldBatch, null);
      }
      extensionConfiguration.stop();
      DelegatedLoggingSpanTestExporter.spanQueue.clear();
    }
  }

  private void configureBatchExporter() {
    properties.set("otel.traces.exporter", "delegatedLogging");
    properties.set("otel.traces.sampler", "always_on");
    properties.set("otel.metrics.exporter", "none");
    properties.set("otel.logs.exporter", "none");
  }

  private void assertBatchParents(String traceId, String rootName) {
    DelegatedLoggingSpanTestExporter.Span root = exportedSpan(traceId, rootName);
    DelegatedLoggingSpanTestExporter.Span step = exportedSpan(traceId, BATCH_STEP_TAG + ":step");
    DelegatedLoggingSpanTestExporter.Span record = exportedSpan(traceId, BATCH_STEP_RECORD_TAG);
    DelegatedLoggingSpanTestExporter.Span processor = exportedSpan(traceId, "processor");
    assertThat(root.getParentSpanContext().getSpanId()).isEqualTo(io.opentelemetry.api.trace.SpanId.getInvalid());
    assertThat(step.getParentSpanContext().getSpanId()).isEqualTo(root.getSpanId());
    assertThat(record.getParentSpanContext().getSpanId()).isEqualTo(step.getSpanId());
    assertThat(processor.getParentSpanContext().getSpanId()).isEqualTo(record.getSpanId());
  }

  private DelegatedLoggingSpanTestExporter.Span exportedSpan(String traceId, String name) {
    java.util.List<DelegatedLoggingSpanTestExporter.Span> matches = DelegatedLoggingSpanTestExporter.spanQueue
        .stream()
        .filter(span -> traceId.equals(span.getTraceId()) && name.equals(span.getSpanName()))
        .collect(java.util.stream.Collectors.toList());
    assertThat(matches).as("exactly one exported %s span for trace %s", name, traceId).hasSize(1);
    return matches.get(0);
  }

  private TraceComponent startBatch(OpenTelemetryConnection connection, String transactionId) {
    String stepLocation = "batch-job/steps/0";
    TraceComponent batch = TraceComponent.of(transactionId, new HashMap<>())
        .withTransactionId(transactionId)
        .withSpanName(transactionId)
        .withStartTime(Instant.now())
        .withEventContextId(transactionId);
    batch.addTag(MULE_BATCH_JOB_NAME.getKey(), transactionId);
    batch.addTag(MULE_BATCH_JOB_STEPS.getKey(), "step|" + stepLocation);
    connection.startTransaction(batch);
    return batch;
  }

  private void completeBatchAndFlush(OpenTelemetryConnection connection, TraceComponent batch) {
    String transactionId = batch.getTransactionId();
    String stepLocation = "batch-job/steps/0";
    TraceComponent processor = TraceComponent.of("processor", new HashMap<>())
        .withTransactionId(transactionId)
        .withSpanName("processor")
        .withStartTime(Instant.now())
        .withLocation(stepLocation + "/processors/0")
        .withEventContextId(transactionId)
        .withSiblings(0);
    processor.addTag(MULE_BATCH_JOB_STEP_NAME.getKey(), "step");
    SpanMeta processorSpan = connection.getTransactionStore().addProcessorSpan(stepLocation,
        processor, connection.spanBuilder("processor"));
    assertThat(processorSpan).isNotNull();
    processor.withEndTime(Instant.now());
    connection.getTransactionStore().endProcessorSpan(transactionId, processor, null, processor.getEndTime());
    // Mirror the separate batch record-end notification before completing the job.
    TraceComponent record = TraceComponent.of(BATCH_STEP_RECORD_TAG, new HashMap<>())
        .withTransactionId(transactionId)
        .withSpanName(BATCH_STEP_RECORD_TAG)
        .withLocation(stepLocation + "/record")
        .withEventContextId(transactionId)
        .withEndTime(Instant.now());
    record.addTag(MULE_BATCH_JOB_STEP_NAME.getKey(), "step");
    assertThat(connection.getTransactionStore().endProcessorSpan(transactionId, record, null, record.getEndTime()))
        .as("the record-end notification finds its span").isNotNull();
    batch.withEndTime(Instant.now());
    connection.getTransactionStore().endTransaction(batch, null);
    io.opentelemetry.sdk.common.CompletableResultCode result = connection._getSdkForTest()
        .getSdkTracerProvider().forceFlush();
    result.join(5, TimeUnit.SECONDS);
    assertThat(result.isSuccess()).as("span export must complete successfully").isTrue();
  }
}
