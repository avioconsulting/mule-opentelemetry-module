package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.api.ee.batch.BatchJob;
import com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.notification.EnrichedServerNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil.toBatchJob;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.BATCH_JOB_TAG;

public class BatchProcessorComponent extends AbstractProcessorComponent {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchProcessorComponent.class);

  @Override
  public boolean canHandle(ComponentIdentifier componentIdentifier) {
    return namespaceSupported(componentIdentifier)
        && operationSupported(componentIdentifier);
  }

  @Override
  protected String getNamespace() {
    return "batch";
  }

  @Override
  protected List<String> getSources() {
    return Collections.emptyList();
  }

  @Override
  protected List<String> getOperations() {
    return Collections.singletonList("job");
  }

  @Override
  public TraceComponent getStartTraceComponent(Component component, Event event) {
    TraceComponent startTraceComponent = super.getStartTraceComponent(component, event);
    String jobName = addJobTags(startTraceComponent, component);
    return TraceComponent.of(BATCH_JOB_TAG, startTraceComponent.getComponentLocation())
        .withSpanName(BATCH_JOB_TAG)
        .withTags(startTraceComponent.getTags())
        .withTransactionId(startTraceComponent.getTransactionId())
        .withEventContextId(startTraceComponent.getEventContextId())
        .withSpanKind(getSpanKind())
        .withContext(startTraceComponent.getContext());
  }

  private String addJobTags(TraceComponent traceComponent, Component component) {
    try {
      BatchJob batchJob = toBatchJob(component);
      if (batchJob != null) {
        String steps = batchJob.getSteps()
            .stream().map(step -> {
              String stepName = step.getName();
              String location = step.getComponent().getLocation().getLocation();
              return stepName + "|" + location;
            }).collect(Collectors.joining(","));
        traceComponent.getTags().put(MULE_BATCH_JOB_STEPS.getKey(), steps);
      }
    } catch (Exception ignore) {
    }
    ComponentWrapper wrapper = new ComponentWrapper(component, configurationComponentLocator);
    String jobName = wrapper.getParameter("jobName");
    traceComponent.getTags().put(MULE_BATCH_JOB_NAME.getKey(), jobName);
    return jobName;
  }

  @Override
  public TraceComponent getEndTraceComponent(EnrichedServerNotification notification) {
    TraceComponent endTraceComponent = super.getEndTraceComponent(notification);
    // On successful execution of batch:job operation, the event variables contains
    // batchJobInstanceId.
    // The default trace component sets that as a transaction id but since the trace
    // was started
    // with the original event context id as a transaction id. Reset the transaction
    // id here
    // to avoid original span entry not found error.
    String transactionId = OpenTelemetryUtil.getEventTransactionId(notification.getEvent().getContext().getId());
    endTraceComponent.withTransactionId(transactionId);
    TypedValue<?> batchJobInstanceId = null;
    if ((batchJobInstanceId = notification.getEvent().getVariables().get("batchJobInstanceId")) != null
        && batchJobInstanceId.getValue() != null) {
      endTraceComponent.getTags().put(MULE_BATCH_JOB_INSTANCE_ID.getKey(),
          batchJobInstanceId.getValue().toString());
    }
    addJobTags(endTraceComponent, notification.getComponent());
    return TraceComponent.of(BATCH_JOB_TAG, endTraceComponent.getComponentLocation())
        .withTransactionId(endTraceComponent.getTransactionId())
        .withTags(endTraceComponent.getTags())
        .withStatsCode(endTraceComponent.getStatusCode())
        .withContext(endTraceComponent.getContext())
        .withSpanKind(getSpanKind())
        .withErrorMessage(endTraceComponent.getErrorMessage());
  }
}
