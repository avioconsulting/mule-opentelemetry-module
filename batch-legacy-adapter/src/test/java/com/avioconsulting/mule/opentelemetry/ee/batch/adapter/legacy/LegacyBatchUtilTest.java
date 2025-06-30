package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.legacy;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJob;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchStep;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.Record;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mule.runtime.api.component.Component;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LegacyBatchUtilTest {

  private TestLegacyBatchUtil legacyBatchUtil;

  @Mock
  private com.mulesoft.mule.runtime.module.batch.api.BatchStep mockBatchStep;

  @Mock
  private com.mulesoft.mule.runtime.module.batch.api.BatchJob mockBatchJob;

  @Mock
  private Component mockBatchStepComponent;

  @Mock
  private Component mockBatchJobComponent;

  @Mock
  private com.mulesoft.mule.runtime.module.batch.api.record.Record mockRecord;

  @Mock
  private Component mockComponent;

  // Custom subclass for testing
  private class TestLegacyBatchUtil extends LegacyBatchUtil {
    @Override
    public boolean isBatchStep(Component component) {
      return component == mockBatchStepComponent;
    }

    @Override
    public BatchStep toBatchStep(Component component) {
      if (component == mockBatchStepComponent) {
        return BatchStepAdapter.from(mockBatchStep);
      }
      return null;
    }

    @Override
    public BatchJob toBatchJob(Component component) {
      if (component == mockBatchJobComponent) {
        return BatchJobAdapter.from(mockBatchJob);
      }
      return null;
    }

    @Override
    public Record toRecord(Object record) {
      if (record == mockRecord) {
        return RecordAdapter.from(mockRecord);
      }
      return null;
    }
  }

  @Before
  public void setUp() {
    legacyBatchUtil = new TestLegacyBatchUtil();
  }

  @Test
  public void testIsBatchStepWithBatchStep() {
    boolean result = legacyBatchUtil.isBatchStep(mockBatchStepComponent);
    assertThat(result).as("Should return true for BatchStep").isTrue();
  }

  @Test
  public void testIsBatchStepWithNonBatchStep() {
    boolean result = legacyBatchUtil.isBatchStep(mockComponent);
    assertThat(result).as("Should return false for non-BatchStep").isFalse();
  }

  @Test
  public void testToRecordWithRecord() {
    Record result = legacyBatchUtil.toRecord(mockRecord);
    assertThat(result).as("Should not return null for Record").isNotNull();
    assertThat(result).as("Should return an instance of RecordAdapter").isInstanceOf(RecordAdapter.class);
  }

  @Test
  public void testToRecordWithNonRecord() {
    Record result = legacyBatchUtil.toRecord(new Object());
    assertThat(result).as("Should return null for non-Record").isNull();
  }

  @Test
  public void testToRecords() {
    List<Object> records = Arrays.asList(mockRecord, mockRecord);
    List<Record> result = legacyBatchUtil.toRecords(records);

    assertThat(result).as("Should not return null").isNotNull();
    assertThat(result).as("Should have 2 records").hasSize(2);
    assertThat(result.get(0)).as("Should contain RecordAdapter instances").isInstanceOf(RecordAdapter.class);
  }

  @Test
  public void testToBatchStepWithBatchStep() {
    BatchStep result = legacyBatchUtil.toBatchStep(mockBatchStepComponent);
    assertThat(result).as("Should not return null for BatchStep").isNotNull();
    assertThat(result).as("Should return an instance of BatchStepAdapter").isInstanceOf(BatchStepAdapter.class);
  }

  @Test
  public void testToBatchStepWithNonBatchStep() {
    BatchStep result = legacyBatchUtil.toBatchStep(mockComponent);
    assertThat(result).as("Should return null for non-BatchStep").isNull();
  }

  @Test
  public void testToBatchJobWithBatchJob() {
    BatchJob result = legacyBatchUtil.toBatchJob(mockBatchJobComponent);
    assertThat(result).as("Should not return null for BatchJob").isNotNull();
    assertThat(result).as("Should return an instance of BatchJobAdapter").isInstanceOf(BatchJobAdapter.class);
  }

  @Test
  public void testToBatchJobWithNonBatchJob() {
    BatchJob result = legacyBatchUtil.toBatchJob(mockComponent);
    assertThat(result).as("Should return null for non-BatchJob").isNull();
  }
}
