package com.avioconsulting.mule.opentelemetry.ee.batch.notifications;

import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchUtil;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.notifications.OtelBatchNotification;
import com.avioconsulting.mule.opentelemetry.ee.batch.adapter.runtime.BatchJobInstanceAdapter;
import com.avioconsulting.mule.opentelemetry.ee.batch.adapter.runtime.BatchStepAdapter;
import com.avioconsulting.mule.opentelemetry.ee.batch.adapter.runtime.RecordAdapter;
import com.mulesoft.mule.runtime.module.batch.BatchEventWithSpan;
import com.mulesoft.mule.runtime.module.batch.api.BatchStep;
import com.mulesoft.mule.runtime.module.batch.api.extension.structure.BatchJobInstance;
import com.mulesoft.mule.runtime.module.batch.api.extension.structure.BatchJobResult;
import com.mulesoft.mule.runtime.module.batch.api.notification.BatchNotification;
import com.mulesoft.mule.runtime.module.batch.api.record.Record;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mule.runtime.api.notification.CustomNotification;
import org.mule.runtime.api.notification.NotificationListenerRegistry;

import java.util.Arrays;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RuntimeBatchNotificationListenerTest {

  @Mock
  private NotificationListenerRegistry mockRegistry;

  @Mock
  private Consumer<OtelBatchNotification> mockCallback;

  private BatchJobInstance mockBatchJobInstance;
  @Mock
  private BatchJobResult mockBatchJobResult;

  @Mock
  private BatchStep mockBatchStep;

  @Mock
  private Record mockRecord;

  @Mock
  private Exception mockException;

  private RuntimeBatchNotificationListener listener;

  @Before
  public void setUp() {
    mockBatchJobInstance = Mockito.mock(
        com.mulesoft.mule.runtime.module.batch.api.extension.structure.BatchJobInstance.class,
        withSettings().extraInterfaces(
            com.mulesoft.mule.runtime.module.batch.engine.BatchJobInstanceAdapter.class));
    BatchEventWithSpan batchEvent = Mockito.mock(BatchEventWithSpan.class);
    lenient().when(batchEvent.getCorrelationId()).thenReturn("testCorrelationId");
    lenient().when(((com.mulesoft.mule.runtime.module.batch.engine.BatchJobInstanceAdapter) mockBatchJobInstance)
        .getBatchEvent()).thenReturn(batchEvent);
    lenient().when(mockBatchJobInstance.getResult()).thenReturn(mockBatchJobResult);
    listener = new RuntimeBatchNotificationListener();
  }

  @Test
  public void onNotification_NoBatchNotification() {
    listener = new RuntimeBatchNotificationListener();
    listener.register(mockCallback, mockRegistry);
    verify(mockRegistry).registerListener(listener);
    CustomNotification notification = Mockito.mock(CustomNotification.class);
    listener.onNotification(notification);
    verifyNoInteractions(mockCallback);
  }

  @Test
  public void onNotification_BatchNotification() {
    listener = new RuntimeBatchNotificationListener();
    listener.register(mockCallback, mockRegistry);
    verify(mockRegistry).registerListener(listener);
    BatchNotification notification = new BatchNotification(mockBatchJobInstance,
        BatchNotification.LOAD_PHASE_BEGIN);
    listener.onNotification(notification);
    ArgumentCaptor<OtelBatchNotification> captor = ArgumentCaptor.forClass(OtelBatchNotification.class);
    verify(mockCallback).accept(captor.capture());
    OtelBatchNotification otelBatchNotification = captor.getValue();
    assertThat(otelBatchNotification)
        .isNotNull()
        .isInstanceOf(OtelBatchNotification.class);
    assertThat(otelBatchNotification.getCorrelationId()).isEqualTo("testCorrelationId");
    assertThat(otelBatchNotification.getJobInstance()).isNotNull()
        .isInstanceOf(BatchJobInstanceAdapter.class);
    assertThat(otelBatchNotification.getStep()).isNull();
    assertThat(otelBatchNotification.getException()).isNull();
    assertThat(otelBatchNotification.getRecord()).isNull();
  }

  @Test
  public void onNotification_BatchNotification_withException() {
    Exception mockException = mock(Exception.class);
    listener = new RuntimeBatchNotificationListener();
    listener.register(mockCallback, mockRegistry);
    verify(mockRegistry).registerListener(listener);
    BatchNotification notification = new BatchNotification(mockBatchJobInstance, mockException,
        BatchNotification.LOAD_PHASE_BEGIN);
    listener.onNotification(notification);
    ArgumentCaptor<OtelBatchNotification> captor = ArgumentCaptor.forClass(OtelBatchNotification.class);
    verify(mockCallback).accept(captor.capture());
    OtelBatchNotification otelBatchNotification = captor.getValue();
    assertThat(otelBatchNotification)
        .isNotNull()
        .isInstanceOf(OtelBatchNotification.class);
    assertThat(otelBatchNotification.getCorrelationId()).isEqualTo("testCorrelationId");
    assertThat(otelBatchNotification.getJobInstance()).isNotNull()
        .isInstanceOf(BatchJobInstanceAdapter.class);
    assertThat(otelBatchNotification.getStep()).isNull();
    assertThat(otelBatchNotification.getException()).isEqualTo(mockException);
    assertThat(otelBatchNotification.getRecord()).isNull();
  }

  @Test
  public void onNotification_BatchNotification_withStep() {
    listener = new RuntimeBatchNotificationListener();
    listener.register(mockCallback, mockRegistry);
    verify(mockRegistry).registerListener(listener);
    BatchNotification notification = new BatchNotification(mockBatchJobInstance, mockBatchStep,
        BatchNotification.LOAD_PHASE_BEGIN);
    listener.onNotification(notification);
    ArgumentCaptor<OtelBatchNotification> captor = ArgumentCaptor.forClass(OtelBatchNotification.class);
    verify(mockCallback).accept(captor.capture());
    OtelBatchNotification otelBatchNotification = captor.getValue();
    assertThat(otelBatchNotification)
        .isNotNull()
        .isInstanceOf(OtelBatchNotification.class);
    assertThat(otelBatchNotification.getCorrelationId()).isEqualTo("testCorrelationId");
    assertThat(otelBatchNotification.getJobInstance()).isNotNull()
        .isInstanceOf(BatchJobInstanceAdapter.class);
    assertThat(otelBatchNotification.getStep()).isNotNull().isInstanceOf(BatchStepAdapter.class);
    assertThat(otelBatchNotification.getException()).isNull();
    assertThat(otelBatchNotification.getRecord()).isNull();
  }

  @Test
  public void onNotification_BatchNotification_withStepRecord() {
    listener = new RuntimeBatchNotificationListener();
    listener.register(mockCallback, mockRegistry);
    verify(mockRegistry).registerListener(listener);
    BatchNotification notification = new BatchNotification(mockBatchJobInstance, mockBatchStep, mockRecord,
        BatchNotification.LOAD_PHASE_BEGIN);
    listener.onNotification(notification);
    ArgumentCaptor<OtelBatchNotification> captor = ArgumentCaptor.forClass(OtelBatchNotification.class);
    verify(mockCallback).accept(captor.capture());
    OtelBatchNotification otelBatchNotification = captor.getValue();
    assertThat(otelBatchNotification)
        .isNotNull()
        .isInstanceOf(OtelBatchNotification.class);
    assertThat(otelBatchNotification.getCorrelationId()).isEqualTo("testCorrelationId");
    assertThat(otelBatchNotification.getJobInstance()).isNotNull()
        .isInstanceOf(BatchJobInstanceAdapter.class);
    assertThat(otelBatchNotification.getStep()).isNotNull().isInstanceOf(BatchStepAdapter.class);
    assertThat(otelBatchNotification.getException()).isNull();
    assertThat(otelBatchNotification.getRecord()).isNotNull().isInstanceOf(RecordAdapter.class);
  }

  @Test
  public void getBatchUtil() {
    listener = new RuntimeBatchNotificationListener();
    assertThat(listener.getBatchUtil()).isNotNull()
        .isInstanceOf(BatchUtil.class);
  }

  @Test
  public void register() {
    listener = new RuntimeBatchNotificationListener();
    listener.register(mockCallback, mockRegistry);
    verify(mockRegistry).registerListener(listener);
  }

  @Test
  public void registerNullCallback() {
    listener = new RuntimeBatchNotificationListener();
    NullPointerException exception = catchThrowableOfType(() -> listener.register(null, mockRegistry),
        NullPointerException.class);
    assertThat(exception).isNotNull().isInstanceOf(NullPointerException.class);
    verifyNoInteractions(mockRegistry);
  }

  @Test
  public void testActionCodesAreSynced() {
    listener = new RuntimeBatchNotificationListener();
    Arrays.stream(BatchNotification.class.getDeclaredFields())
        .filter(field -> field.isAccessible() && field.getType().getName().equalsIgnoreCase("int"))
        .forEach(f -> {
          try {
            int batch = f.getInt(BatchNotification.class);
            int otel = OtelBatchNotification.class.getDeclaredField(f.getName())
                .getInt(OtelBatchNotification.class);
            assertThat(otel).as("Field " + f.getName()).isEqualTo(batch);
          } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
          }
        });
  }
}
