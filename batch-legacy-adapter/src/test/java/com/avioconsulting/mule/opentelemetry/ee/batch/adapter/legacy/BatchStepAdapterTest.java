package com.avioconsulting.mule.opentelemetry.ee.batch.adapter.legacy;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchStep;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mule.runtime.api.component.Component;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BatchStepAdapterTest {

  @Mock
  private com.mulesoft.mule.runtime.module.batch.api.BatchStep mockBatchStep;

  @Before
  public void setUp() {
    lenient().when(mockBatchStep.getName()).thenReturn("testStep");
  }

  @Test
  public void testFromWithNullDelegate() {
    BatchStep result = BatchStepAdapter.from(null);
    assertThat(result).as("Should return null when delegate is null").isNull();
  }

  @Test
  public void testFromWithValidDelegate() {
    BatchStep result = BatchStepAdapter.from(mockBatchStep);
    assertThat(result).as("Should not return null when delegate is valid").isNotNull();
    assertThat(result).as("Should return an instance of BatchStepAdapter").isInstanceOf(BatchStepAdapter.class);
  }

  @Test
  public void testGetName() {
    BatchStep adapter = BatchStepAdapter.from(mockBatchStep);
    String name = adapter.getName();
    assertThat(name).as("Name should be 'testStep'").isEqualTo("testStep");
    verify(mockBatchStep, times(1)).getName();
  }

  @Test
  public void testGetComponent() {
    // Create a mock that is both a BatchStep and a Component
    com.mulesoft.mule.runtime.module.batch.api.BatchStep mockBatchStepComponent = mock(
        com.mulesoft.mule.runtime.module.batch.api.BatchStep.class,
        withSettings().extraInterfaces(Component.class));

    // Create the adapter with our special mock
    BatchStep adapter = BatchStepAdapter.from(mockBatchStepComponent);

    // Get the component and verify it's not null
    Component component = adapter.getComponent();
    assertThat(component).as("Component should not be null").isNotNull();
    assertThat(component).as("Component should be the same as mockBatchStepComponent")
        .isSameAs(mockBatchStepComponent);
  }
}
