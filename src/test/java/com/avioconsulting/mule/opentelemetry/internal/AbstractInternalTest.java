package com.avioconsulting.mule.opentelemetry.internal;

import com.avioconsulting.mule.opentelemetry.internal.connection.OpenTelemetryConnection;
import org.junit.Before;

public abstract class AbstractInternalTest {

  @Before
  public void setupTests() {
    OpenTelemetryConnection.resetForTest();
  }
}
