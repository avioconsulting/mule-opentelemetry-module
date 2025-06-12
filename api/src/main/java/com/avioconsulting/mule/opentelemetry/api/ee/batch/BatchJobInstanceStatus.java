package com.avioconsulting.mule.opentelemetry.api.ee.batch;

public enum BatchJobInstanceStatus {
  LOADING(false, true), FAILED_LOADING(true, false), EXECUTING(false, true), STOPPED(false, false), FAILED_INPUT(true,
      false), FAILED_ON_COMPLETE(true,
          false), FAILED_PROCESS_RECORDS(true, false), SUCCESSFUL(false, false), CANCELLED(false, false);

  private final boolean failure;
  private final boolean executable;

  private BatchJobInstanceStatus(boolean failure, boolean executable) {
    this.failure = failure;
    this.executable = executable;
  }

  public boolean isFailure() {
    return this.failure;
  }

  public boolean isExecutable() {
    return this.executable;
  }
}
