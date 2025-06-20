package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.legacy;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchStepResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BatchStepResultAdapterTest {

  @Mock
  private com.mulesoft.mule.runtime.module.batch.api.BatchStepResult mockBatchStepResult;

  @Before
  public void setUp() {
    lenient().when(mockBatchStepResult.getReceivedRecords()).thenReturn(100L);
    lenient().when(mockBatchStepResult.getSuccessfulRecords()).thenReturn(80L);
    lenient().when(mockBatchStepResult.getFailedRecords()).thenReturn(20L);
  }

  @Test
  public void testFromWithNullDelegate() {
    BatchStepResult result = BatchStepResultAdapter.from(null);
    assertThat(result).as("Should return null when delegate is null").isNull();
  }

  @Test
  public void testFromWithValidDelegate() {
    BatchStepResult result = BatchStepResultAdapter.from(mockBatchStepResult);
    assertThat(result).as("Should not return null when delegate is valid").isNotNull();
    assertThat(result).as("Should return an instance of BatchStepResultAdapter")
        .isInstanceOf(BatchStepResultAdapter.class);
  }

  @Test
  public void testGetReceivedRecords() {
    BatchStepResult adapter = BatchStepResultAdapter.from(mockBatchStepResult);
    long receivedRecords = adapter.getReceivedRecords();

    assertThat(receivedRecords).as("Received records should be 100").isEqualTo(100L);
    verify(mockBatchStepResult, times(1)).getReceivedRecords();
  }

  @Test
  public void testGetSuccessfulRecords() {
    BatchStepResult adapter = BatchStepResultAdapter.from(mockBatchStepResult);
    long successfulRecords = adapter.getSuccessfulRecords();

    assertThat(successfulRecords).as("Successful records should be 80").isEqualTo(80L);
    verify(mockBatchStepResult, times(1)).getSuccessfulRecords();
  }

  @Test
  public void testGetFailedRecords() {
    BatchStepResult adapter = BatchStepResultAdapter.from(mockBatchStepResult);
    long failedRecords = adapter.getFailedRecords();

    assertThat(failedRecords).as("Failed records should be 20").isEqualTo(20L);
    verify(mockBatchStepResult, times(1)).getFailedRecords();
  }
}
