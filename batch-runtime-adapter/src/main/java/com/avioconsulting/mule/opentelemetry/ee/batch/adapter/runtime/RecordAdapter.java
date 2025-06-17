package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.runtime;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.Record;
import org.mule.runtime.api.metadata.TypedValue;

import java.util.Map;

public class RecordAdapter implements Record {
  private final com.mulesoft.mule.runtime.module.batch.api.record.Record delegate;

  private RecordAdapter(com.mulesoft.mule.runtime.module.batch.api.record.Record delegate) {
    this.delegate = delegate;
  }

  public static Record from(com.mulesoft.mule.runtime.module.batch.api.record.Record delegate) {
    return delegate == null ? null : new RecordAdapter(delegate);
  }

  @Override
  public TypedValue<Object> getPayload() {
    return delegate.getPayload();
  }

  @Override
  public TypedValue<Object> getAttributes() {
    return delegate.getAttributes();
  }

  @Override
  public TypedValue<?> getVariable(String key) {
    return delegate.getVariable(key);
  }

  @Override
  public Map<String, TypedValue<?>> getAllVariables() {
    return delegate.getAllVariables();
  }

  @Override
  public int getFailedStepsCount() {
    return delegate.getFailedStepsCount();
  }

  @Override
  public Exception getExceptionForStep(String stepName) {
    return delegate.getExceptionForStep(stepName);
  }

  @Override
  public Map<String, Exception> getStepExceptions() {
    return delegate.getStepExceptions();
  }

  @Override
  public boolean hasErrors() {
    return delegate.hasErrors();
  }

  @Override
  public String getCurrentStepId() {
    return delegate.getCurrentStepId();
  }

}
