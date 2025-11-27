package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.api.AppIdentifier;
import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.SpanProcessorConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import com.avioconsulting.mule.opentelemetry.api.notifications.MetricBaseNotificationData;
import com.avioconsulting.mule.opentelemetry.api.providers.NoopOpenTelemetryMetricsConfigProvider;
import com.avioconsulting.mule.opentelemetry.api.providers.OpenTelemetryMetricsConfigProvider;
import com.avioconsulting.mule.opentelemetry.api.providers.OpenTelemetryMetricsProvider;
import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import com.avioconsulting.mule.opentelemetry.internal.AbstractInternalTest;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryConfigWrapper;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryConfiguration;
import io.opentelemetry.api.OpenTelemetry;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.message.Error;

import java.util.HashMap;
import java.util.Map;

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