package com.avioconsulting.mule.opentelemetry.api.ee.batch;

public interface BatchStepResult {
  long getReceivedRecords();

  long getSuccessfulRecords();

  long getFailedRecords();
}
