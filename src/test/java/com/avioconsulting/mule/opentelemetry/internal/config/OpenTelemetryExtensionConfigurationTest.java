package com.avioconsulting.mule.opentelemetry.internal.config;

import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.TraceLevelConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import com.avioconsulting.mule.opentelemetry.internal.notifications.listeners.MuleMessageProcessorNotificationListener;
import com.avioconsulting.mule.opentelemetry.internal.notifications.listeners.MulePipelineMessageNotificationListener;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.notification.NotificationListenerRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class OpenTelemetryExtensionConfigurationTest {

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
  @InjectMocks
  OpenTelemetryExtensionConfiguration extensionConfiguration;

  @Before
  public void setUp() throws Exception {
    extensionConfiguration.setExporterConfiguration(exporterConfiguration);
  }

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
    verify(spy).init(any(OpenTelemetryConnection.class), any(TraceLevelConfiguration.class));
  }
}