package com.avioconsulting.mule.opentelemetry.api.ee.batch;

public interface BatchJobResult {
  BatchStepResult getResultForStep(String stepName);

  long getElapsedTimeInMillis();

  long getSuccessfulRecords();

  long getFailedRecords();

  long getTotalRecords();

  long getLoadedRecords();

  long getProcessedRecords();

  String getBatchJobInstanceId();

  boolean isFailedOnInputPhase();

  Exception getInputPhaseException();

  boolean isFailedOnLoadingPhase();

  Exception getLoadingPhaseException();

  boolean isFailedOnCompletePhase();

  Exception getOnCompletePhaseException();
}
