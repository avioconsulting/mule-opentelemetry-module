package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.runtime;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJob;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchStep;

import java.util.List;
import java.util.stream.Collectors;

public class BatchJobAdapter implements BatchJob {
  private final com.mulesoft.mule.runtime.module.batch.api.BatchJob delegate;
  private final List<BatchStep> steps;

  public BatchJobAdapter(com.mulesoft.mule.runtime.module.batch.api.BatchJob delegate) {
    this.delegate = delegate;
    steps = delegate.getSteps().stream().map(BatchStepAdapter::from).collect(Collectors.toList());
  }

  public static BatchJob from(com.mulesoft.mule.runtime.module.batch.api.BatchJob delegate) {
    return delegate == null ? null : new BatchJobAdapter(delegate);
  }

  @Override
  public List<BatchStep> getSteps() {
    return steps;
  }

  @Override
  public int getMaxFailedRecords() {
    return delegate.getMaxFailedRecords();
  }

  @Override
  public String getName() {
    return delegate.getName();
  }
}
