package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.legacy;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJob;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchStep;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BatchJobAdapterTest {

  @Mock
  private com.mulesoft.mule.runtime.module.batch.api.BatchJob mockBatchJob;

  @Mock
  private com.mulesoft.mule.runtime.module.batch.api.BatchStep mockStep1;

  @Mock
  private com.mulesoft.mule.runtime.module.batch.api.BatchStep mockStep2;

  @Before
  public void setUp() {
    lenient().when(mockBatchJob.getSteps()).thenReturn(Arrays.asList(mockStep1, mockStep2));
    lenient().when(mockBatchJob.getMaxFailedRecords()).thenReturn(10);
    lenient().when(mockBatchJob.getName()).thenReturn("TestBatchJob");
  }

  @Test
  public void testFromWithNullDelegate() {
    BatchJob result = BatchJobAdapter.from(null);
    assertThat(result).as("Should return null when delegate is null").isNull();
  }

  @Test
  public void testFromWithValidDelegate() {
    BatchJob result = BatchJobAdapter.from(mockBatchJob);
    assertThat(result).as("Should not return null when delegate is valid").isNotNull();
    assertThat(result).as("Should return an instance of BatchJobAdapter").isInstanceOf(BatchJobAdapter.class);
  }

  @Test
  public void testGetSteps() {
    BatchJob adapter = BatchJobAdapter.from(mockBatchJob);
    List<BatchStep> steps = adapter.getSteps();

    assertThat(steps).as("Steps should not be null").isNotNull();
    assertThat(steps).as("Should have 2 steps").hasSize(2);
    verify(mockBatchJob, times(1)).getSteps();
  }

  @Test
  public void testGetMaxFailedRecords() {
    BatchJob adapter = BatchJobAdapter.from(mockBatchJob);
    int maxFailedRecords = adapter.getMaxFailedRecords();

    assertThat(maxFailedRecords).as("Max failed records should be 10").isEqualTo(10);
    verify(mockBatchJob, times(1)).getMaxFailedRecords();
  }

  @Test
  public void testGetName() {
    BatchJob adapter = BatchJobAdapter.from(mockBatchJob);
    String name = adapter.getName();

    assertThat(name).as("Name should be 'TestBatchJob'").isEqualTo("TestBatchJob");
    verify(mockBatchJob, times(1)).getName();
  }
}
