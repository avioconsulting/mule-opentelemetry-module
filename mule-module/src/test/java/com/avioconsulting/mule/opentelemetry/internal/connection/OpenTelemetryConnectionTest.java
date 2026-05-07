package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.api.AppIdentifier;
import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.SpanProcessorConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.LoggingExporter;
import com.avioconsulting.mule.opentelemetry.api.notifications.MetricBaseNotificationData;
import com.avioconsulting.mule.opentelemetry.api.providers.NoopOpenTelemetryMetricsConfigProvider;
import com.avioconsulting.mule.opentelemetry.api.providers.OpenTelemetryMetricsConfigProvider;
import com.avioconsulting.mule.opentelemetry.api.providers.OpenTelemetryMetricsProvider;
import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.AbstractInternalTest;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryConfigWrapper;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryConfiguration;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.ErrorType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.avioconsulting.mule.opentelemetry.api.store.TransactionStore.TRACE_TRANSACTION_ID;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class OpenTelemetryConnectionTest extends AbstractInternalTest {

  @Test
  public void getInstance() {
    OpenTelemetryResource resource = mock(OpenTelemetryResource.class);
    ExporterConfiguration exporterConfig = mock(ExporterConfiguration.class);
    OpenTelemetryExporter exporter = mock(OpenTelemetryExporter.class);
    when(exporterConfig.getExporter()).thenReturn(exporter);
    SpanProcessorConfiguration spc = mock(SpanProcessorConfiguration.class);
    OpenTelemetryConfiguration configuration = mock(OpenTelemetryConfiguration.class);
    when(configuration.getResource()).thenReturn(resource);
    when(configuration.getExporterConfiguration()).thenReturn(exporterConfig);
    when(configuration.getSpanProcessorConfiguration()).thenReturn(spc);
    when(configuration.isTurnOffMetrics()).thenReturn(true);
    OpenTelemetryConfigWrapper wrapper = new OpenTelemetryConfigWrapper(configuration);
    OpenTelemetryConnection instance = OpenTelemetryConnection.getInstance(wrapper);
    verify(resource).getConfigMap();
    verify(exporter).getExporterProperties();
    verify(spc).getConfigMap();
  }

  @Test
  public void getTraceContext_returnsFallbackContext_whenTransactionMissing() {
    OpenTelemetryResource resource = mock(OpenTelemetryResource.class);
    ExporterConfiguration exporterConfig = mock(ExporterConfiguration.class);
    OpenTelemetryExporter exporter = mock(OpenTelemetryExporter.class);
    when(exporterConfig.getExporter()).thenReturn(exporter);
    SpanProcessorConfiguration spc = mock(SpanProcessorConfiguration.class);
    OpenTelemetryConfiguration configuration = mock(OpenTelemetryConfiguration.class);
    when(configuration.getResource()).thenReturn(resource);
    when(configuration.getExporterConfiguration()).thenReturn(exporterConfig);
    when(configuration.getSpanProcessorConfiguration()).thenReturn(spc);
    OpenTelemetryConfigWrapper wrapper = new OpenTelemetryConfigWrapper(configuration);
    OpenTelemetryConnection instance = OpenTelemetryConnection.getInstance(wrapper);

    String txId = "missing-tx-id";
    Map<String, Object> traceContext = instance.getTraceContext(txId);

    assertThat(traceContext).isNotNull();
    assertThat(traceContext).containsEntry(TRACE_TRANSACTION_ID, txId);
  }

  @Test
  public void getTraceContext_withLocation_returnsFallbackContext_whenTransactionMissing() {
    OpenTelemetryResource resource = mock(OpenTelemetryResource.class);
    ExporterConfiguration exporterConfig = mock(ExporterConfiguration.class);
    OpenTelemetryExporter exporter = mock(OpenTelemetryExporter.class);
    when(exporterConfig.getExporter()).thenReturn(exporter);
    SpanProcessorConfiguration spc = mock(SpanProcessorConfiguration.class);
    OpenTelemetryConfiguration configuration = mock(OpenTelemetryConfiguration.class);
    when(configuration.getResource()).thenReturn(resource);
    when(configuration.getExporterConfiguration()).thenReturn(exporterConfig);
    when(configuration.getSpanProcessorConfiguration()).thenReturn(spc);
    OpenTelemetryConfigWrapper wrapper = new OpenTelemetryConfigWrapper(configuration);
    OpenTelemetryConnection instance = OpenTelemetryConnection.getInstance(wrapper);

    String txId = "missing-tx-id-with-location";
    Map<String, Object> traceContext = instance.getTraceContext(txId, "flow/processors/0");

    assertThat(traceContext).isNotNull();
    assertThat(traceContext).containsEntry(TRACE_TRANSACTION_ID, txId);
  }

  @Test
  public void metrics_on_with_none_provider_and_another_impl() {
    OpenTelemetryResource resource = mock(OpenTelemetryResource.class);
    ExporterConfiguration exporterConfig = mock(ExporterConfiguration.class);
    OpenTelemetryExporter exporter = mock(OpenTelemetryExporter.class);
    when(exporterConfig.getExporter()).thenReturn(exporter);
    SpanProcessorConfiguration spc = mock(SpanProcessorConfiguration.class);
    OpenTelemetryConfiguration configuration = mock(OpenTelemetryConfiguration.class);
    when(configuration.getResource()).thenReturn(resource);
    when(configuration.getExporterConfiguration()).thenReturn(exporterConfig);
    when(configuration.getSpanProcessorConfiguration()).thenReturn(spc);

    // Keep metrics on
    when(configuration.isTurnOffMetrics()).thenReturn(false);
    when(configuration.getMetricsConfigProvider()).thenReturn(null);
    OpenTelemetryConfigWrapper wrapper = new OpenTelemetryConfigWrapper(configuration);

    OpenTelemetryConnection instance = OpenTelemetryConnection.getInstance(wrapper);

    TransactionMeta transactionMeta = mock(TransactionMeta.class);
    Exception exception = mock(Exception.class);
    Throwable npe = Assertions.catchThrowable(
        () -> instance.getMetricsProviders().captureFlowMetrics(transactionMeta, "flow", exception));
    Assertions.assertThat(npe).as(
        "Any exception from Metrics providers default settings (Enabled, None) with another provider on classpath")
        .isNull();
  }

  @Test
  public void metrics_off_with_none_provider_and_another_impl() {
    OpenTelemetryResource resource = mock(OpenTelemetryResource.class);
    ExporterConfiguration exporterConfig = mock(ExporterConfiguration.class);
    OpenTelemetryExporter exporter = mock(OpenTelemetryExporter.class);
    when(exporterConfig.getExporter()).thenReturn(exporter);
    SpanProcessorConfiguration spc = mock(SpanProcessorConfiguration.class);
    OpenTelemetryConfiguration configuration = mock(OpenTelemetryConfiguration.class);
    when(configuration.getResource()).thenReturn(resource);
    when(configuration.getExporterConfiguration()).thenReturn(exporterConfig);
    when(configuration.getSpanProcessorConfiguration()).thenReturn(spc);

    // Turn off metrics
    when(configuration.isTurnOffMetrics()).thenReturn(true);
    when(configuration.getMetricsConfigProvider()).thenReturn(null);

    OpenTelemetryConfigWrapper wrapper = new OpenTelemetryConfigWrapper(configuration);

    OpenTelemetryConnection instance = OpenTelemetryConnection.getInstance(wrapper);

    TransactionMeta transactionMeta = mock(TransactionMeta.class);
    Exception exception = mock(Exception.class);
    Throwable npe = Assertions.catchThrowable(
        () -> instance.getMetricsProviders().captureFlowMetrics(transactionMeta, "flow", exception));
    Assertions.assertThat(npe).as(
        "Any exception from Metrics providers default settings (Enabled, None) with another provider on classpath")
        .isNull();
  }

  @Test
  public void metrics_on_with_NoOp_provider_and_another_impl() {
    OpenTelemetryResource resource = mock(OpenTelemetryResource.class);
    ExporterConfiguration exporterConfig = mock(ExporterConfiguration.class);
    OpenTelemetryExporter exporter = mock(OpenTelemetryExporter.class);
    when(exporterConfig.getExporter()).thenReturn(exporter);
    SpanProcessorConfiguration spc = mock(SpanProcessorConfiguration.class);
    OpenTelemetryConfiguration configuration = mock(OpenTelemetryConfiguration.class);
    when(configuration.getResource()).thenReturn(resource);
    when(configuration.getExporterConfiguration()).thenReturn(exporterConfig);
    when(configuration.getSpanProcessorConfiguration()).thenReturn(spc);

    // Keep metrics on
    when(configuration.isTurnOffMetrics()).thenReturn(false);
    when(configuration.getMetricsConfigProvider()).thenReturn(new NoopOpenTelemetryMetricsConfigProvider());
    OpenTelemetryConfigWrapper wrapper = new OpenTelemetryConfigWrapper(configuration);

    OpenTelemetryConnection instance = OpenTelemetryConnection.getInstance(wrapper);

    TransactionMeta transactionMeta = mock(TransactionMeta.class);
    Exception exception = mock(Exception.class);
    Throwable possibleClassCast = Assertions.catchThrowable(
        () -> instance.getMetricsProviders().captureFlowMetrics(transactionMeta, "flow", exception));
    Assertions.assertThat(possibleClassCast).as(
        "Any exception from Metrics providers default settings (Enabled, None) with another provider on classpath")
        .isNull();
  }

  @Test
  public void metrics_off_with_NoOp_provider_and_another_impl() {
    OpenTelemetryResource resource = mock(OpenTelemetryResource.class);
    ExporterConfiguration exporterConfig = mock(ExporterConfiguration.class);
    OpenTelemetryExporter exporter = mock(OpenTelemetryExporter.class);
    when(exporterConfig.getExporter()).thenReturn(exporter);
    SpanProcessorConfiguration spc = mock(SpanProcessorConfiguration.class);
    OpenTelemetryConfiguration configuration = mock(OpenTelemetryConfiguration.class);
    when(configuration.getResource()).thenReturn(resource);
    when(configuration.getExporterConfiguration()).thenReturn(exporterConfig);
    when(configuration.getSpanProcessorConfiguration()).thenReturn(spc);

    // Keep metrics on
    when(configuration.isTurnOffMetrics()).thenReturn(true);
    when(configuration.getMetricsConfigProvider()).thenReturn(new NoopOpenTelemetryMetricsConfigProvider());
    OpenTelemetryConfigWrapper wrapper = new OpenTelemetryConfigWrapper(configuration);

    OpenTelemetryConnection instance = OpenTelemetryConnection.getInstance(wrapper);

    TransactionMeta transactionMeta = mock(TransactionMeta.class);
    Exception exception = mock(Exception.class);
    Throwable possibleClassCast = Assertions.catchThrowable(
        () -> instance.getMetricsProviders().captureFlowMetrics(transactionMeta, "flow", exception));
    Assertions.assertThat(possibleClassCast).as(
        "Any exception from Metrics providers default settings (Enabled, None) with another provider on classpath")
        .isNull();
  }

  @Test
  public void metrics_on_with_test_provider() {
    OpenTelemetryResource resource = mock(OpenTelemetryResource.class);
    ExporterConfiguration exporterConfig = mock(ExporterConfiguration.class);
    OpenTelemetryExporter exporter = mock(OpenTelemetryExporter.class);
    when(exporterConfig.getExporter()).thenReturn(exporter);
    SpanProcessorConfiguration spc = mock(SpanProcessorConfiguration.class);
    OpenTelemetryConfiguration configuration = mock(OpenTelemetryConfiguration.class);
    when(configuration.getResource()).thenReturn(resource);
    when(configuration.getExporterConfiguration()).thenReturn(exporterConfig);
    when(configuration.getSpanProcessorConfiguration()).thenReturn(spc);

    // Keep metrics on
    when(configuration.isTurnOffMetrics()).thenReturn(false);

    Map<String, TransactionMeta> store = new HashMap<>();
    TestOpenTelemetryMetricsConfigProvider configProvider = new TestOpenTelemetryMetricsConfigProvider(store);
    when(configuration.getMetricsConfigProvider()).thenReturn(configProvider);
    OpenTelemetryConfigWrapper wrapper = new OpenTelemetryConfigWrapper(configuration);

    OpenTelemetryMetricsProviderCollection metricsProviderCollection = new OpenTelemetryMetricsProviderCollection();
    metricsProviderCollection.add(new TestOpenTelemetryMetricsProvider());

    OpenTelemetryConnection instance = OpenTelemetryConnection.getInstance(wrapper);
    OpenTelemetryConnection spy = spy(instance);
    // Use ServiceLoader instead of spy to set this,
    // but classloader isolation doesn't let test classes to be on the app
    // classpath, unless they are moved to main source
    // If a test-util like module is created, rewrite this - future feat.
    when(spy.getMetricsProviders()).thenReturn(metricsProviderCollection);
    spy.getMetricsProviders().initialize(configProvider, null);

    TransactionMeta transactionMeta = mock(TransactionMeta.class);
    Exception exception = mock(Exception.class);
    Throwable anyEx = Assertions.catchThrowable(
        () -> spy.getMetricsProviders().captureFlowMetrics(transactionMeta, "flow", exception));
    Assertions.assertThat(anyEx).as(
        "Any exception from Metrics providers default settings (Enabled, None) with another provider on classpath")
        .isNull();
    Assertions.assertThat(store.get("flow")).isEqualTo(transactionMeta);
  }

  /**
   * H8: {@code endProcessorSpan} must not throw a NullPointerException when
   * {@code error.getCause()} returns {@code null} (e.g., synthetic Mule errors
   * that have no underlying Java exception).
   */
  @Test
  public void endProcessorSpan_doesNotThrowNPE_whenErrorCauseIsNull() {
    // Arrange — build a minimal connection with a logging exporter
    com.avioconsulting.mule.opentelemetry.api.config.exporter.LoggingExporter loggingExporter = new LoggingExporter();
    ExporterConfiguration exporterConfiguration = new ExporterConfiguration();
    exporterConfiguration.setExporter(loggingExporter);
    com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration configuration = new com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration();
    configuration.setExporterConfiguration(exporterConfiguration);
    OpenTelemetryConfigWrapper wrapper = new OpenTelemetryConfigWrapper(configuration);
    OpenTelemetryConnection conn = OpenTelemetryConnection.getInstance(wrapper);

    Tracer tracer = GlobalOpenTelemetry.get().getTracer("h8-test", "v1");
    Instant now = Instant.now();

    // Start a root transaction
    String txId = "h8-tx";
    String flowName = "h8-flow";
    String location = flowName + "/processors/0";
    TraceComponent startTc = TraceComponent.of(flowName, new HashMap<>())
        .withTransactionId(txId)
        .withSpanName(flowName)
        .withStartTime(now)
        .withLocation(location)
        .withEventContextId(txId + "-ctx");
    SpanBuilder rootSpan = tracer.spanBuilder(flowName).setSpanKind(SpanKind.SERVER).setStartTimestamp(now);
    conn.getTransactionStore().startTransaction(startTc, flowName, rootSpan);

    // Add a processor span so endProcessorSpan has something to end
    TraceComponent processorTc = TraceComponent.of(flowName, new HashMap<>())
        .withTransactionId(txId)
        .withSpanName("logger")
        .withStartTime(now)
        .withLocation(location)
        .withEventContextId(txId + "-ctx");
    conn.getTransactionStore().addProcessorSpan(flowName, processorTc,
        tracer.spanBuilder("logger").setSpanKind(SpanKind.INTERNAL));

    // Build an Error whose getCause() returns null (synthetic error, no Java cause)
    Error syntheticError = mock(Error.class);
    when(syntheticError.getCause()).thenReturn(null);
    when(syntheticError.getDescription()).thenReturn("MULE:CONNECTIVITY");
    ErrorType errorType = mock(ErrorType.class);
    when(errorType.toString()).thenReturn("MULE:CONNECTIVITY");
    when(syntheticError.getErrorType()).thenReturn(errorType);

    TraceComponent endTc = TraceComponent.of(flowName, new HashMap<>())
        .withTransactionId(txId)
        .withSpanName("logger")
        .withStartTime(now)
        .withEndTime(Instant.now())
        .withLocation(location)
        .withEventContextId(txId + "-ctx");

    // Act & Assert — must not throw NPE
    assertThatCode(() -> conn.endProcessorSpan(endTc, syntheticError))
        .doesNotThrowAnyException();
  }

  public static class TestOpenTelemetryMetricsConfigProvider implements OpenTelemetryMetricsConfigProvider {
    private final Map<String, TransactionMeta> store;

    public TestOpenTelemetryMetricsConfigProvider(Map<String, TransactionMeta> store) {
      this.store = store;
    }

    public Map<String, TransactionMeta> getStore() {
      return store;
    }

    @Override
    public void initialise(AppIdentifier appIdentifier) {

    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void turnOffMetrics(boolean turnOffMetrics) {

    }
  }

  public static class TestOpenTelemetryMetricsProvider
      implements OpenTelemetryMetricsProvider<TestOpenTelemetryMetricsConfigProvider> {

    private Map<String, TransactionMeta> store;

    @Override
    public void initialize(TestOpenTelemetryMetricsConfigProvider configProvider, OpenTelemetry openTelemetry) {
      this.store = configProvider.getStore();
    }

    @Override
    public void stop() {

    }

    @Override
    public void captureProcessorMetrics(Component component, Error error, String location, SpanMeta spanMeta) {
      store.put(location, spanMeta);
    }

    @Override
    public void captureFlowMetrics(TransactionMeta transactionMeta, String flowName, Exception exception) {
      store.put(flowName, transactionMeta);
    }

    @Override
    public void captureCustomMetric(MetricBaseNotificationData metricNotification) {
      store.put(metricNotification.getMetricName(), null);
    }
  }
}
