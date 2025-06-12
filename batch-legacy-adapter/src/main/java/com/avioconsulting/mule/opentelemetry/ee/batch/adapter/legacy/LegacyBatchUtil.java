package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.legacy;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJob;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchStep;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchUtil;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.Record;
import org.mule.runtime.api.component.Component;

import java.util.List;
import java.util.stream.Collectors;

public class LegacyBatchUtil implements BatchUtil {
  @Override
  public boolean isBatchStep(Component component) {
    return component instanceof com.mulesoft.mule.runtime.module.batch.api.BatchStep;
  }

  @Override
  public Record toRecord(Object record) {
    if (record instanceof com.mulesoft.mule.runtime.module.batch.api.record.Record) {
      return RecordAdapter.from((com.mulesoft.mule.runtime.module.batch.api.record.Record) record);
    }
    return null;
  }

  @Override
  public List<Record> toRecords(List<Object> records) {
    return records.stream().map(this::toRecord).collect(Collectors.toList());
  }

  @Override
  public BatchStep toBatchStep(Component component) {
    if (component instanceof com.mulesoft.mule.runtime.module.batch.api.BatchStep) {
      return BatchStepAdapter.from((com.mulesoft.mule.runtime.module.batch.api.BatchStep) component);
    }
    return null;
  }

  @Override
  public BatchJob toBatchJob(Component component) {
    if (component instanceof com.mulesoft.mule.runtime.module.batch.api.BatchJob) {
      return BatchJobAdapter.from((com.mulesoft.mule.runtime.module.batch.api.BatchJob) component);
    }
    return null;
  }
}
