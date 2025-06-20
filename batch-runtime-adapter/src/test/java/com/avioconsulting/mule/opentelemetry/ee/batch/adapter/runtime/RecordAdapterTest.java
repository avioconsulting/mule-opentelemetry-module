package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.runtime;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.Record;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mule.runtime.api.metadata.TypedValue;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RecordAdapterTest {

  @Mock
  private com.mulesoft.mule.runtime.module.batch.api.record.Record mockRecord;

  private TypedValue<Object> mockPayload = TypedValue.of(Mockito.mock(Object.class));

  private TypedValue<Object> mockAttributes = TypedValue.of(Mockito.mock(Object.class));

  private TypedValue<Object> mockVariable = TypedValue.of(Mockito.mock(Object.class));

  @Mock
  private Exception mockException;

  private Map<String, TypedValue<?>> variables;
  private Map<String, Exception> exceptions;

  @Before
  public void setUp() {
    variables = new HashMap<>();
    variables.put("testVar", mockVariable);

    exceptions = new HashMap<>();
    exceptions.put("testStep", mockException);

    lenient().when(mockRecord.getPayload()).thenReturn(mockPayload);
    lenient().when(mockRecord.getAttributes()).thenReturn(mockAttributes);
    lenient().when(mockRecord.getVariable("testVar")).thenReturn(mockVariable);
    lenient().when(mockRecord.getAllVariables()).thenReturn(variables);
    lenient().when(mockRecord.getFailedStepsCount()).thenReturn(1);
    lenient().when(mockRecord.getExceptionForStep("testStep")).thenReturn(mockException);
    lenient().when(mockRecord.getStepExceptions()).thenReturn(exceptions);
    lenient().when(mockRecord.hasErrors()).thenReturn(true);
    lenient().when(mockRecord.getCurrentStepId()).thenReturn("currentStep");
  }

  @Test
  public void testFromWithNullDelegate() {
    Record result = RecordAdapter.from(null);
    assertThat(result).as("Should return null when delegate is null").isNull();
  }

  @Test
  public void testFromWithValidDelegate() {
    Record result = RecordAdapter.from(mockRecord);
    assertThat(result).as("Should not return null when delegate is valid").isNotNull();
    assertThat(result).as("Should return an instance of RecordAdapter").isInstanceOf(RecordAdapter.class);
  }

  @Test
  public void testGetPayload() {
    Record adapter = RecordAdapter.from(mockRecord);
    TypedValue<Object> payload = adapter.getPayload();

    assertThat(payload).as("Payload should be the same").isSameAs(mockPayload);
    verify(mockRecord, times(1)).getPayload();
  }

  @Test
  public void testGetAttributes() {
    Record adapter = RecordAdapter.from(mockRecord);
    TypedValue<Object> attributes = adapter.getAttributes();

    assertThat(attributes).as("Attributes should be the same").isSameAs(mockAttributes);
    verify(mockRecord, times(1)).getAttributes();
  }

  @Test
  public void testGetVariable() {
    Record adapter = RecordAdapter.from(mockRecord);
    TypedValue<?> variable = adapter.getVariable("testVar");

    assertThat(variable).as("Variable should be the same").isSameAs(mockVariable);
    verify(mockRecord, times(1)).getVariable("testVar");
  }

  @Test
  public void testGetAllVariables() {
    Record adapter = RecordAdapter.from(mockRecord);
    Map<String, TypedValue<?>> allVariables = adapter.getAllVariables();

    assertThat(allVariables).as("All variables should be the same").isSameAs(variables);
    verify(mockRecord, times(1)).getAllVariables();
  }

  @Test
  public void testGetFailedStepsCount() {
    Record adapter = RecordAdapter.from(mockRecord);
    int failedStepsCount = adapter.getFailedStepsCount();

    assertThat(failedStepsCount).as("Failed steps count should be 1").isEqualTo(1);
    verify(mockRecord, times(1)).getFailedStepsCount();
  }

  @Test
  public void testGetExceptionForStep() {
    Record adapter = RecordAdapter.from(mockRecord);
    Exception exception = adapter.getExceptionForStep("testStep");

    assertThat(exception).as("Exception should be the same").isSameAs(mockException);
    verify(mockRecord, times(1)).getExceptionForStep("testStep");
  }

  @Test
  public void testGetStepExceptions() {
    Record adapter = RecordAdapter.from(mockRecord);
    Map<String, Exception> stepExceptions = adapter.getStepExceptions();

    assertThat(stepExceptions).as("Step exceptions should be the same").isSameAs(exceptions);
    verify(mockRecord, times(1)).getStepExceptions();
  }

  @Test
  public void testHasErrors() {
    Record adapter = RecordAdapter.from(mockRecord);
    boolean hasErrors = adapter.hasErrors();

    assertThat(hasErrors).as("Should have errors").isTrue();
    verify(mockRecord, times(1)).hasErrors();
  }

  @Test
  public void testGetCurrentStepId() {
    Record adapter = RecordAdapter.from(mockRecord);
    String currentStepId = adapter.getCurrentStepId();

    assertThat(currentStepId).as("Current step ID should be 'currentStep'").isEqualTo("currentStep");
    verify(mockRecord, times(1)).getCurrentStepId();
  }
}
