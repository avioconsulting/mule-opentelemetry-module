package com.avioconsulting.mule.opentelemetry.internal.config;

import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import com.avioconsulting.mule.opentelemetry.internal.AbstractInternalTest;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.notifications.listeners.MuleMessageProcessorNotificationListener;
import com.avioconsulting.mule.opentelemetry.internal.notifications.listeners.MulePipelineMessageNotificationListener;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.junit.Before;
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

@RunWith(MockitoJUnitRunner.class)
public class OpenTelemetryExtensionConfigurationTest extends AbstractInternalTest {

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
  }
}