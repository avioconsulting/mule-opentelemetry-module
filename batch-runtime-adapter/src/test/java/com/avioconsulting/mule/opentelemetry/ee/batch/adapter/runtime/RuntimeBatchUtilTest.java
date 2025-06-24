package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.runtime;

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
public class RuntimeBatchUtilTest {

  private RuntimeBatchUtil runtimeBatchUtil;

  // Create mocks that implement both their respective interfaces and Component
  private com.mulesoft.mule.runtime.module.batch.api.BatchStep mockBatchStep;
  private com.mulesoft.mule.runtime.module.batch.api.BatchJob mockBatchJob;

  @Mock
  private com.mulesoft.mule.runtime.module.batch.api.record.Record mockRecord;

  @Mock
  private Component mockComponent;

  @Before
  public void setUp() {
    runtimeBatchUtil = new RuntimeBatchUtil();

    // Initialize mocks that implement both their respective interfaces and
    // Component
    mockBatchStep = mock(com.mulesoft.mule.runtime.module.batch.api.BatchStep.class,
        withSettings().extraInterfaces(Component.class));
    mockBatchJob = mock(com.mulesoft.mule.runtime.module.batch.api.BatchJob.class,
        withSettings().extraInterfaces(Component.class));
  }

  @Test
  public void testIsBatchStepWithBatchStep() {
    // Use the mock that implements both BatchStep and Component
    Component batchStepComponent = (Component) mockBatchStep;

    boolean result = runtimeBatchUtil.isBatchStep(batchStepComponent);
    assertThat(result).as("Should return true for BatchStep").isTrue();
  }

  @Test
  public void testIsBatchStepWithNonBatchStep() {
    boolean result = runtimeBatchUtil.isBatchStep(mockComponent);
    assertThat(result).as("Should return false for non-BatchStep").isFalse();
  }

  @Test
  public void testToRecordWithRecord() {
    Record result = runtimeBatchUtil.toRecord(mockRecord);
    assertThat(result).as("Should not return null for Record").isNotNull();
    assertThat(result).as("Should return an instance of RecordAdapter").isInstanceOf(RecordAdapter.class);
  }

  @Test
  public void testToRecordWithNonRecord() {
    Record result = runtimeBatchUtil.toRecord(new Object());
    assertThat(result).as("Should return null for non-Record").isNull();
  }

  @Test
  public void testToRecords() {
    List<Object> records = Arrays.asList(mockRecord, mockRecord);
    List<Record> result = runtimeBatchUtil.toRecords(records);

    assertThat(result).as("Should not return null").isNotNull();
    assertThat(result).as("Should have 2 records").hasSize(2);
    assertThat(result.get(0)).as("Should contain RecordAdapter instances").isInstanceOf(RecordAdapter.class);
  }

  @Test
  public void testToBatchStepWithBatchStep() {
    // Use the mock that implements both BatchStep and Component
    Component batchStepComponent = (Component) mockBatchStep;

    BatchStep result = runtimeBatchUtil.toBatchStep(batchStepComponent);
    assertThat(result).as("Should not return null for BatchStep").isNotNull();
    assertThat(result).as("Should return an instance of BatchStepAdapter").isInstanceOf(BatchStepAdapter.class);
  }

  @Test
  public void testToBatchStepWithNonBatchStep() {
    BatchStep result = runtimeBatchUtil.toBatchStep(mockComponent);
    assertThat(result).as("Should return null for non-BatchStep").isNull();
  }

  @Test
  public void testToBatchJobWithBatchJob() {
    // Cast mockBatchJob to Component for the test
    Component batchJobComponent = (Component) mockBatchJob;

    BatchJob result = runtimeBatchUtil.toBatchJob(batchJobComponent);
    assertThat(result).as("Should not return null for BatchJob").isNotNull();
    assertThat(result).as("Should return an instance of BatchJobAdapter").isInstanceOf(BatchJobAdapter.class);
  }

  @Test
  public void testToBatchJobWithNonBatchJob() {
    BatchJob result = runtimeBatchUtil.toBatchJob(mockComponent);
    assertThat(result).as("Should return null for non-BatchJob").isNull();
  }
}
