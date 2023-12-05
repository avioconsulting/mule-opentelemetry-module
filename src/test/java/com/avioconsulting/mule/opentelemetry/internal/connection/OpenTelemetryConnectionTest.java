package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.api.config.ExporterConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.SpanProcessorConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryConfigWrapper;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryConfiguration;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class OpenTelemetryConnectionTest {

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
    OpenTelemetryConfigWrapper wrapper = new OpenTelemetryConfigWrapper(configuration);
    OpenTelemetryConnection instance = OpenTelemetryConnection.getInstance(wrapper);
    verify(resource).getConfigMap();
    verify(exporter).getExporterProperties();
    verify(spc).getConfigMap();
  }
}