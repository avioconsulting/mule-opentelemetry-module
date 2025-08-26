package com.avioconsulting.mule.opentelemetry.internal.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJob;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchStep;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchUtil;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.Record;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentRegistryService;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.metadata.TypedValue;

import java.util.List;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil.getEventTransactionId;

public class BatchHelperUtil {

  /**
   * Holder for
   * {@link com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchUtil}
   * initialized by
   * OTEL Batch API Provider
   */
  private static com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchUtil batchUtilDelegate;
  private static boolean batchSupportDisabled = true;

  public static void _resetForTesting() {
    batchSupportDisabled = true;
    batchUtilDelegate = null;
  }

  public static void init(BatchUtil batchUtil) {
    batchUtilDelegate = batchUtil;
  }

  private static BatchUtil getBatchUtil() {
    return batchUtilDelegate;
  }

  public static BatchStep toBatchStep(Component component) {
    return isBatchSupportDisabled() ? null : getBatchUtil().toBatchStep(component);
  }

  public static BatchJob toBatchJob(Component component) {
    return isBatchSupportDisabled() ? null : getBatchUtil().toBatchJob(component);
  }

  public static boolean isBatchStep(String location, ComponentRegistryService componentRegistryService) {
    Component component = componentRegistryService.findComponentByLocation(location);
    return component != null && batchUtilDelegate.isBatchStep(component);
  }

  public static boolean notBatchChildContainer(String containerName,
      ComponentRegistryService componentRegistryService) {
    return BatchHelperUtil.isBatchStep(containerName,
        componentRegistryService) || isBatchOnComplete(containerName, componentRegistryService);
  }

  public static boolean hasBatchJobInstanceId(TraceComponent traceComponent) {
    return traceComponent.getTags().containsKey(MULE_BATCH_JOB_INSTANCE_ID.getKey());
  }

  public static String getBatchJobInstanceId(TraceComponent traceComponent) {
    return traceComponent.getTags().get(MULE_BATCH_JOB_INSTANCE_ID.getKey());
  }

  public static String getBatchJobInstanceId(Event event) {
    TypedValue<?> batchJobInstanceId = null;
    if ((batchJobInstanceId = event.getVariables().get("batchJobInstanceId")) != null
        && batchJobInstanceId.getValue() != null) {
      return batchJobInstanceId.getValue().toString();
    }
    return null;
  }

  public static boolean isBatchStepFirstProcessor(ComponentLocation location, Event event,
      ComponentRegistryService componentRegistryService) {
    if (isBatchSupportDisabled())
      return false;
    return (getBatchJobInstanceId(event) != null
        && isBatchStep(getLocationParent(location.getLocation()), componentRegistryService)
        && isFirstProcessorInScope(location));
  }

  public static boolean hasBatchStep(TraceComponent traceComponent) {
    return traceComponent.getTags().containsKey(MULE_BATCH_JOB_STEP_NAME.getKey());
  }

  public static void addBatchTags(TraceComponent traceComponent, Event event) {
    String batchJobId = getBatchJobInstanceId(event);
    if (!isBatchSupportDisabled() && batchJobId != null) {
      traceComponent.getTags().put(MULE_BATCH_JOB_INSTANCE_ID.getKey(), batchJobId);
      if (event.getVariables().containsKey("_mule_batch_INTERNAL_record")
          && event.getVariables().get("_mule_batch_INTERNAL_record") != null
          && event.getVariables().get("_mule_batch_INTERNAL_record").getValue() != null) {
        // When processing individual record through step scope, this variable contains
        // current record
        Record record = getBatchUtil()
            .toRecord(event.getVariables().get("_mule_batch_INTERNAL_record").getValue());
        traceComponent.getTags().put(MULE_BATCH_JOB_STEP_NAME.getKey(), record.getCurrentStepId());
      } else if (event.getVariables().containsKey("records")
          && event.getVariables().get("records") != null
          && event.getVariables().get("records").getValue() != null) {
        // When processing aggregator scope, records variables contain an accumulated
        // list of records to process
        List records = (List) event.getVariables().get("records").getValue();
        if (!records.isEmpty()) {
          Record record = getBatchUtil().toRecord(records.get(0));
          traceComponent.getTags().put(MULE_BATCH_JOB_STEP_NAME.getKey(), record.getCurrentStepId());
          traceComponent.getTags().put(MULE_BATCH_JOB_STEP_AGGREGATOR_RECORD_COUNT.getKey(),
              String.valueOf(records.size()));
        }
      }
    }
  }

  public static void copyBatchTags(TraceComponent source, TraceComponent target) {
    source.getTags().entrySet().stream().filter(e -> e.getKey().startsWith("mule.batch.job")).forEach(
        e -> target.getTags().put(e.getKey(), e.getValue()));
  }

  public static void enableBatchSupport() {
    batchSupportDisabled = false;
  }

  public static boolean isBatchSupportDisabled() {
    return batchSupportDisabled;
  }

  /**
   * Event processing should be skipped if batch support is disabled and the
   * resolved transaction id is same as batch job instance id, which is the case
   * of batch processing.
   * 
   * @param event
   *            {@link Event}
   * @return true if event should not be processed
   */
  public static boolean shouldSkipThisBatchProcessing(Event event) {
    return event != null
        && BatchHelperUtil.isBatchSupportDisabled()
        && getEventTransactionId(event)
            .equalsIgnoreCase(getBatchJobInstanceId(event));
  }
}
