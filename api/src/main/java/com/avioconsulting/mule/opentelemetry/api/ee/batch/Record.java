package com.avioconsulting.mule.opentelemetry.api.ee.batch;

import org.mule.runtime.api.metadata.TypedValue;

import java.util.Map;

public interface Record {

  TypedValue<Object> getPayload();

  TypedValue<Object> getAttributes();

  TypedValue<?> getVariable(String key);

  Map<String, TypedValue<?>> getAllVariables();

  int getFailedStepsCount();

  Exception getExceptionForStep(String stepName);

  Map<String, Exception> getStepExceptions();

  boolean hasErrors();

  String getCurrentStepId();
}
