package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.legacy;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJobInstance;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJobInstanceStatus;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJobResult;

import java.util.Date;

import static com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJobInstanceStatus.*;

public class BatchJobInstanceAdapter
    implements BatchJobInstance {

  private final com.mulesoft.mule.runtime.module.batch.api.BatchJobInstance delegate;

  private BatchJobInstanceAdapter(Object delegate) {
    this.delegate = (com.mulesoft.mule.runtime.module.batch.api.BatchJobInstance) delegate;
  }

  public static BatchJobInstance from(com.mulesoft.mule.runtime.module.batch.api.BatchJobInstance delegate) {
    return delegate == null ? null : new BatchJobInstanceAdapter(delegate);
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public BatchJobResult getResult() {
    return BatchJobResultAdapter.from(delegate.getResult());
  }

  @Override
  public BatchJobInstanceStatus getStatus() {
    com.mulesoft.mule.runtime.module.batch.api.BatchJobInstanceStatus delegateStatus = delegate.getStatus();
    switch (delegateStatus) {
      case LOADING:
        return LOADING;
      case FAILED_LOADING:
        return FAILED_LOADING;
      case EXECUTING:
        return EXECUTING;
      case STOPPED:
        return BatchJobInstanceStatus.STOPPED;
      case FAILED_INPUT:
        return BatchJobInstanceStatus.FAILED_INPUT;
      case FAILED_ON_COMPLETE:
        return BatchJobInstanceStatus.FAILED_ON_COMPLETE;
      case FAILED_PROCESS_RECORDS:
        return BatchJobInstanceStatus.FAILED_PROCESS_RECORDS;
      case SUCCESSFUL:
        return BatchJobInstanceStatus.SUCCESSFUL;
      case CANCELLED:
        return BatchJobInstanceStatus.CANCELLED;
      default:
        throw new IllegalStateException("Unknown batch job instance status: " + delegateStatus);
    }
  }

  @Override
  public long getRecordCount() {
    return delegate.getRecordCount();
  }

  @Override
  public String getOwnerJobName() {
    return delegate.getOwnerJobName();
  }

  @Override
  public Date getCreationTime() {
    return delegate.getCreationTime();
  }
}
