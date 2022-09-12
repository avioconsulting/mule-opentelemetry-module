package com.avioconsulting.mule.opentelemetry.internal.connection;

import com.avioconsulting.mule.opentelemetry.api.config.OpenTelemetryResource;
import com.avioconsulting.mule.opentelemetry.api.config.SpanProcessorConfiguration;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryConfigWrapper;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class OpenTelemetryConnectionTest {

  @Test
  public void getInstance() {
    OpenTelemetryResource resource = mock(OpenTelemetryResource.class);
    OpenTelemetryExporter exporter = mock(OpenTelemetryExporter.class);
    SpanProcessorConfiguration spc = mock(SpanProcessorConfiguration.class);
    OpenTelemetryConfigWrapper wrapper = new OpenTelemetryConfigWrapper(resource, exporter, spc);
    OpenTelemetryConnection instance = OpenTelemetryConnection.getInstance(wrapper);
    verify(resource).getConfigMap();
    verify(exporter).getExporterProperties();
    verify(spc).getConfigMap();
  }
}