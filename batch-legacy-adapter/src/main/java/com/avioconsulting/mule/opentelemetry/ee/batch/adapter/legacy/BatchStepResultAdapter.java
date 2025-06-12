package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.legacy;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchStepResult;

public class BatchStepResultAdapter implements BatchStepResult {
  private final com.mulesoft.mule.runtime.module.batch.api.BatchStepResult delegate;

  private BatchStepResultAdapter(com.mulesoft.mule.runtime.module.batch.api.BatchStepResult delegate) {
    this.delegate = delegate;
  }

  public static BatchStepResult from(com.mulesoft.mule.runtime.module.batch.api.BatchStepResult delegate) {
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
