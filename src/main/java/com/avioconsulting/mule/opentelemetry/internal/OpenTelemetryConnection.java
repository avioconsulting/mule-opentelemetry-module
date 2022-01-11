package com.avioconsulting.mule.opentelemetry.internal;


public final class OpenTelemetryConnection {

  private final String id;

  public OpenTelemetryConnection(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public void invalidate() {
    // do something to invalidate this connection!
  }
}
