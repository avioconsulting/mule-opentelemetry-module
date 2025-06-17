package com.avioconsulting.mule.opentelemetry.api.ee.batch;

import java.util.List;

public interface BatchJob {
  List<BatchStep> getSteps();

  int getMaxFailedRecords();

  String getName();
}
