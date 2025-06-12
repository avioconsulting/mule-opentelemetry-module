package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.legacy;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJobResult;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchStepResult;

public class BatchJobResultAdapter implements BatchJobResult {
  private final com.mulesoft.mule.runtime.module.batch.api.BatchJobResult delegate;

  private BatchJobResultAdapter(com.mulesoft.mule.runtime.module.batch.api.BatchJobResult delegate) {
    this.delegate = delegate;
  }

  public static BatchJobResult from(com.mulesoft.mule.runtime.module.batch.api.BatchJobResult delegate) {
    return delegate == null ? null : new BatchJobResultAdapter(delegate);
  }

  @Override
  public BatchStepResult getResultForStep(String stepName) {
    return BatchStepResultAdapter.from(delegate.getResultForStep(stepName));
  }

  @Override
  public long getElapsedTimeInMillis() {
    return delegate.getElapsedTimeInMillis();
  }

  @Override
  public long getSuccessfulRecords() {
    return delegate.getSuccessfulRecords();
  }

  @Override
  public long getFailedRecords() {
    return delegate.getFailedRecords();
  }

  @Override
  public long getTotalRecords() {
    return delegate.getTotalRecords();
  }

  @Override
  public long getLoadedRecords() {
    return delegate.getLoadedRecords();
  }

  @Override
  public long getProcessedRecords() {
    return delegate.getProcessedRecords();
  }

  @Override
  public String getBatchJobInstanceId() {
    return delegate.getBatchJobInstanceId();
  }

  @Override
  public boolean isFailedOnInputPhase() {
    return delegate.isFailedOnInputPhase();
  }

  @Override
  public Exception getInputPhaseException() {
    return delegate.getInputPhaseException();
  }

  @Override
  public boolean isFailedOnLoadingPhase() {
    return delegate.isFailedOnLoadingPhase();
  }

  @Override
  public Exception getLoadingPhaseException() {
    return delegate.getLoadingPhaseException();
  }

  @Override
  public boolean isFailedOnCompletePhase() {
    return delegate.isFailedOnCompletePhase();
  }

  @Override
  public Exception getOnCompletePhaseException() {
    return delegate.getOnCompletePhaseException();
  }
}
