package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.legacy;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJobResult;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchStepResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BatchJobResultAdapterTest {

  @Mock
  private com.mulesoft.mule.runtime.module.batch.api.BatchJobResult mockBatchJobResult;

  @Mock
  private com.mulesoft.mule.runtime.module.batch.api.BatchStepResult mockBatchStepResult;

  @Mock
  private Exception mockException;

  @Before
  public void setUp() {
    lenient().when(mockBatchJobResult.getResultForStep("testStep")).thenReturn(mockBatchStepResult);
    lenient().when(mockBatchJobResult.getElapsedTimeInMillis()).thenReturn(1000L);
    lenient().when(mockBatchJobResult.getSuccessfulRecords()).thenReturn(80L);
    lenient().when(mockBatchJobResult.getFailedRecords()).thenReturn(20L);
    lenient().when(mockBatchJobResult.getTotalRecords()).thenReturn(100L);
    lenient().when(mockBatchJobResult.getLoadedRecords()).thenReturn(100L);
    lenient().when(mockBatchJobResult.getProcessedRecords()).thenReturn(100L);
    lenient().when(mockBatchJobResult.getBatchJobInstanceId()).thenReturn("test-instance-id");
    lenient().when(mockBatchJobResult.isFailedOnInputPhase()).thenReturn(false);
    lenient().when(mockBatchJobResult.getInputPhaseException()).thenReturn(mockException);
    lenient().when(mockBatchJobResult.isFailedOnLoadingPhase()).thenReturn(false);
    lenient().when(mockBatchJobResult.getLoadingPhaseException()).thenReturn(mockException);
    lenient().when(mockBatchJobResult.isFailedOnCompletePhase()).thenReturn(false);
    lenient().when(mockBatchJobResult.getOnCompletePhaseException()).thenReturn(mockException);
  }

  @Test
  public void testFromWithNullDelegate() {
    BatchJobResult result = BatchJobResultAdapter.from(null);
    assertThat(result).as("Should return null when delegate is null").isNull();
  }

  @Test
  public void testFromWithValidDelegate() {
    BatchJobResult result = BatchJobResultAdapter.from(mockBatchJobResult);
    assertThat(result).as("Should not return null when delegate is valid").isNotNull();
    assertThat(result).as("Should return an instance of BatchJobResultAdapter")
        .isInstanceOf(BatchJobResultAdapter.class);
  }

  @Test
  public void testGetResultForStep() {
    BatchJobResult adapter = BatchJobResultAdapter.from(mockBatchJobResult);
    BatchStepResult result = adapter.getResultForStep("testStep");

    assertThat(result).as("Step result should not be null").isNotNull();
    verify(mockBatchJobResult, times(1)).getResultForStep("testStep");
  }

  @Test
  public void testGetElapsedTimeInMillis() {
    BatchJobResult adapter = BatchJobResultAdapter.from(mockBatchJobResult);
    long elapsedTime = adapter.getElapsedTimeInMillis();

    assertThat(elapsedTime).as("Elapsed time should be 1000ms").isEqualTo(1000L);
    verify(mockBatchJobResult, times(1)).getElapsedTimeInMillis();
  }

  @Test
  public void testGetSuccessfulRecords() {
    BatchJobResult adapter = BatchJobResultAdapter.from(mockBatchJobResult);
    long successfulRecords = adapter.getSuccessfulRecords();

    assertThat(successfulRecords).as("Successful records should be 80").isEqualTo(80L);
    verify(mockBatchJobResult, times(1)).getSuccessfulRecords();
  }

  @Test
  public void testGetFailedRecords() {
    BatchJobResult adapter = BatchJobResultAdapter.from(mockBatchJobResult);
    long failedRecords = adapter.getFailedRecords();

    assertThat(failedRecords).as("Failed records should be 20").isEqualTo(20L);
    verify(mockBatchJobResult, times(1)).getFailedRecords();
  }

  @Test
  public void testGetTotalRecords() {
    BatchJobResult adapter = BatchJobResultAdapter.from(mockBatchJobResult);
    long totalRecords = adapter.getTotalRecords();

    assertThat(totalRecords).as("Total records should be 100").isEqualTo(100L);
    verify(mockBatchJobResult, times(1)).getTotalRecords();
  }

  @Test
  public void testGetLoadedRecords() {
    BatchJobResult adapter = BatchJobResultAdapter.from(mockBatchJobResult);
    long loadedRecords = adapter.getLoadedRecords();

    assertThat(loadedRecords).as("Loaded records should be 100").isEqualTo(100L);
    verify(mockBatchJobResult, times(1)).getLoadedRecords();
  }

  @Test
  public void testGetProcessedRecords() {
    BatchJobResult adapter = BatchJobResultAdapter.from(mockBatchJobResult);
    long processedRecords = adapter.getProcessedRecords();

    assertThat(processedRecords).as("Processed records should be 100").isEqualTo(100L);
    verify(mockBatchJobResult, times(1)).getProcessedRecords();
  }

  @Test
  public void testGetBatchJobInstanceId() {
    BatchJobResult adapter = BatchJobResultAdapter.from(mockBatchJobResult);
    String instanceId = adapter.getBatchJobInstanceId();

    assertThat(instanceId).as("Instance ID should be 'test-instance-id'").isEqualTo("test-instance-id");
    verify(mockBatchJobResult, times(1)).getBatchJobInstanceId();
  }

  @Test
  public void testIsFailedOnInputPhase() {
    BatchJobResult adapter = BatchJobResultAdapter.from(mockBatchJobResult);
    boolean failed = adapter.isFailedOnInputPhase();

    assertThat(failed).as("Should not be failed on input phase").isFalse();
    verify(mockBatchJobResult, times(1)).isFailedOnInputPhase();
  }

  @Test
  public void testGetInputPhaseException() {
    BatchJobResult adapter = BatchJobResultAdapter.from(mockBatchJobResult);
    Exception exception = adapter.getInputPhaseException();

    assertThat(exception).as("Exception should be the same").isSameAs(mockException);
    verify(mockBatchJobResult, times(1)).getInputPhaseException();
  }

  @Test
  public void testIsFailedOnLoadingPhase() {
    BatchJobResult adapter = BatchJobResultAdapter.from(mockBatchJobResult);
    boolean failed = adapter.isFailedOnLoadingPhase();

    assertThat(failed).as("Should not be failed on loading phase").isFalse();
    verify(mockBatchJobResult, times(1)).isFailedOnLoadingPhase();
  }

  @Test
  public void testGetLoadingPhaseException() {
    BatchJobResult adapter = BatchJobResultAdapter.from(mockBatchJobResult);
    Exception exception = adapter.getLoadingPhaseException();

    assertThat(exception).as("Exception should be the same").isSameAs(mockException);
    verify(mockBatchJobResult, times(1)).getLoadingPhaseException();
  }

  @Test
  public void testIsFailedOnCompletePhase() {
    BatchJobResult adapter = BatchJobResultAdapter.from(mockBatchJobResult);
    boolean failed = adapter.isFailedOnCompletePhase();

    assertThat(failed).as("Should not be failed on complete phase").isFalse();
    verify(mockBatchJobResult, times(1)).isFailedOnCompletePhase();
  }

  @Test
  public void testGetOnCompletePhaseException() {
    BatchJobResult adapter = BatchJobResultAdapter.from(mockBatchJobResult);
    Exception exception = adapter.getOnCompletePhaseException();

    assertThat(exception).as("Exception should be the same").isSameAs(mockException);
    verify(mockBatchJobResult, times(1)).getOnCompletePhaseException();
  }
}
