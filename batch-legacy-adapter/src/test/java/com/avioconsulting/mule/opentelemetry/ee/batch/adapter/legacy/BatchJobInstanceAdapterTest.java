package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.legacy;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJobInstance;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJobInstanceStatus;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJobResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BatchJobInstanceAdapterTest {

  @Mock
  private com.mulesoft.mule.runtime.module.batch.api.BatchJobInstance mockBatchJobInstance;

  @Mock
  private com.mulesoft.mule.runtime.module.batch.api.BatchJobResult mockBatchJobResult;

  private Date creationTime;

  @Before
  public void setUp() {
    creationTime = new Date();
    lenient().when(mockBatchJobInstance.getId()).thenReturn("test-instance-id");
    lenient().when(mockBatchJobInstance.getResult()).thenReturn(mockBatchJobResult);
    lenient().when(mockBatchJobInstance.getStatus())
        .thenReturn(com.mulesoft.mule.runtime.module.batch.api.BatchJobInstanceStatus.SUCCESSFUL);
    lenient().when(mockBatchJobInstance.getRecordCount()).thenReturn(100L);
    lenient().when(mockBatchJobInstance.getOwnerJobName()).thenReturn("test-owner-job");
    lenient().when(mockBatchJobInstance.getCreationTime()).thenReturn(creationTime);
  }

  @Test
  public void testFromWithNullDelegate() {
    BatchJobInstance result = BatchJobInstanceAdapter.from(null);
    assertThat(result).as("Should return null when delegate is null").isNull();
  }

  @Test
  public void testFromWithValidDelegate() {
    BatchJobInstance result = BatchJobInstanceAdapter.from(mockBatchJobInstance);
    assertThat(result).as("Should not return null when delegate is valid").isNotNull();
    assertThat(result).as("Should return an instance of BatchJobInstanceAdapter")
        .isInstanceOf(BatchJobInstanceAdapter.class);
  }

  @Test
  public void testGetId() {
    BatchJobInstance adapter = BatchJobInstanceAdapter.from(mockBatchJobInstance);
    String id = adapter.getId();

    assertThat(id).as("ID should be 'test-instance-id'").isEqualTo("test-instance-id");
    verify(mockBatchJobInstance, times(1)).getId();
  }

  @Test
  public void testGetResult() {
    BatchJobInstance adapter = BatchJobInstanceAdapter.from(mockBatchJobInstance);
    BatchJobResult result = adapter.getResult();

    assertThat(result).as("Result should not be null").isNotNull();
    verify(mockBatchJobInstance, times(1)).getResult();
  }

  @Test
  public void testGetStatus() {
    BatchJobInstance adapter = BatchJobInstanceAdapter.from(mockBatchJobInstance);
    BatchJobInstanceStatus status = adapter.getStatus();

    assertThat(status).as("Status should be SUCCESSFUL").isEqualTo(BatchJobInstanceStatus.SUCCESSFUL);
    verify(mockBatchJobInstance, times(1)).getStatus();
  }

  @Test
  public void testGetRecordCount() {
    BatchJobInstance adapter = BatchJobInstanceAdapter.from(mockBatchJobInstance);
    long recordCount = adapter.getRecordCount();

    assertThat(recordCount).as("Record count should be 100").isEqualTo(100L);
    verify(mockBatchJobInstance, times(1)).getRecordCount();
  }

  @Test
  public void testGetOwnerJobName() {
    BatchJobInstance adapter = BatchJobInstanceAdapter.from(mockBatchJobInstance);
    String ownerJobName = adapter.getOwnerJobName();

    assertThat(ownerJobName).as("Owner job name should be 'test-owner-job'").isEqualTo("test-owner-job");
    verify(mockBatchJobInstance, times(1)).getOwnerJobName();
  }

  @Test
  public void testGetCreationTime() {
    BatchJobInstance adapter = BatchJobInstanceAdapter.from(mockBatchJobInstance);
    Date result = adapter.getCreationTime();

    assertThat(result).as("Creation time should match").isEqualTo(creationTime);
    verify(mockBatchJobInstance, times(1)).getCreationTime();
  }
}
