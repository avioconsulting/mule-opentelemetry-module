package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.runtime;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchStepResult;

public class BatchStepResultAdapter implements BatchStepResult {
  private final com.mulesoft.mule.runtime.module.batch.api.extension.structure.BatchStepResult delegate;

  public BatchStepResultAdapter(
      com.mulesoft.mule.runtime.module.batch.api.extension.structure.BatchStepResult delegate) {
    this.delegate = delegate;
  }

  public static BatchStepResult from(
      com.mulesoft.mule.runtime.module.batch.api.extension.structure.BatchStepResult delegate) {
    return delegate == null ? null : new BatchStepResultAdapter(delegate);
  }

  @Override
  public long getReceivedRecords() {
    return delegate.getReceivedRecords();
  }

  @Override
  public long getSuccessfulRecords() {
    return delegate.getSuccessfulRecords();
  }

  @Override
  public long getFailedRecords() {
    return delegate.getFailedRecords();
  }
}
