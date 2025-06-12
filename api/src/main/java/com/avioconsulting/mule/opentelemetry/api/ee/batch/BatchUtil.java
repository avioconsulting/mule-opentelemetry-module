package com.avioconsulting.mule.opentelemetry.api.ee.batch;

import org.mule.runtime.api.component.Component;

import java.util.List;

public interface BatchUtil {
  boolean isBatchStep(Component component);

  Record toRecord(Object record);

  List<Record> toRecords(List<Object> records);

  BatchStep toBatchStep(Component component);

  BatchJob toBatchJob(Component component);
}
