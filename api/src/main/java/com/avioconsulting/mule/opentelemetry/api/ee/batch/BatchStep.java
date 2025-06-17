package com.avioconsulting.mule.opentelemetry.api.ee.batch;

import org.mule.runtime.api.component.Component;

public interface BatchStep {
  String getName();

  Component getComponent();
}
