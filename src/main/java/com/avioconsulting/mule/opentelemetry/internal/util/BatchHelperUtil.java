package com.avioconsulting.mule.opentelemetry.internal.util;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.ee.batch.api.BatchJob;
import com.avioconsulting.mule.opentelemetry.ee.batch.api.BatchStep;
import com.avioconsulting.mule.opentelemetry.ee.batch.api.BatchUtil;
import com.avioconsulting.mule.opentelemetry.ee.batch.api.Record;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.metadata.TypedValue;

import java.util.List;
import java.util.Optional;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.*;

public class BatchHelperUtil {

  /**
   * Holder for
   * {@link com.avioconsulting.mule.opentelemetry.ee.batch.api.BatchUtil}
   * initialized by
   * OTEL Batch API Provider
   */
  private static com.avioconsulting.mule.opentelemetry.ee.batch.api.BatchUtil batchUtilDelegate;

  public static void init(BatchUtil batchUtil) {
    batchUtilDelegate = batchUtil;
  }

  private static BatchUtil getBatchUtil() {
    if (batchUtilDelegate == null) {
      throw new IllegalArgumentException("BatchUtil is not initialized.");
    }
    return batchUtilDelegate;
  }

  public static BatchStep toBatchStep(Component component) {
    return getBatchUtil().toBatchStep(component);
  }

  public static BatchJob toBatchJob(Component component) {
    return getBatchUtil().toBatchJob(component);
  }

  public static boolean isBatchStep(String location, ConfigurationComponentLocator componentLocator) {
    Optional<Component> component = componentLocator
        .find(Location.builderFromStringRepresentation(location).build());
    return component.filter(batchUtilDelegate::isBatchStep).isPresent();
  }

  public static boolean notBatchChildContainer(String containerName, ConfigurationComponentLocator componentLocator) {
    return BatchHelperUtil.isBatchStep(containerName,
        componentLocator) || isBatchOnComplete(containerName, componentLocator);
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
      ConfigurationComponentLocator componentLocator) {
    return (getBatchJobInstanceId(event) != null
        && isBatchStep(getLocationParent(location.getLocation()), componentLocator)
        && isFirstProcessorInScope(location));
  }

  public static boolean hasBatchStep(TraceComponent traceComponent) {
    return traceComponent.getTags().containsKey(MULE_BATCH_JOB_STEP_NAME.getKey());
  }

  public static void addBatchTags(TraceComponent traceComponent, Event event) {
    String batchJobId = getBatchJobInstanceId(event);
    if (batchJobId != null) {
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

}
