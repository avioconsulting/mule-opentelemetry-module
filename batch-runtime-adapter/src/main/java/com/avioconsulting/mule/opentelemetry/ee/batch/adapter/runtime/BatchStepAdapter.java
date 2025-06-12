package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.runtime;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchStep;
import org.mule.runtime.api.component.Component;

public class BatchStepAdapter implements BatchStep {

  private final com.mulesoft.mule.runtime.module.batch.api.BatchStep delegate;

  private BatchStepAdapter(com.mulesoft.mule.runtime.module.batch.api.BatchStep delegate) {
    this.delegate = delegate;
  }

  public static BatchStep from(com.mulesoft.mule.runtime.module.batch.api.BatchStep delegate) {
    return delegate == null ? null : new BatchStepAdapter(delegate);
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public Component getComponent() {
    return (Component) delegate;
  }
}
