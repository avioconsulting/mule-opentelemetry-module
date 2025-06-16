package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes;
import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.BATCH_AGGREGATOR;
import static com.avioconsulting.mule.opentelemetry.internal.util.ComponentsUtil.*;
import static com.avioconsulting.mule.opentelemetry.internal.util.BatchHelperUtil.copyBatchTags;

public class BatchTransaction extends AbstractTransaction {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchTransaction.class);

  // Lock-free implementation with AtomicReferences
  private final ConcurrentHashMap<String, AtomicReference<ContainerSpan>> stepSpans = new ConcurrentHashMap<>();
  private final Map<String, ProcessorSpan> stepProcessorSpans = new ConcurrentHashMap<>();
  private final Span rootSpan;
  private final Function<String, SpanBuilder> spanBuilderFunction;

  /**
   * Batch transaction requires lookup of batch components.
   * {@link ConfigurationComponentLocator} helps to find these component
   * locations.
   */
  private final ConfigurationComponentLocator componentLocator;
  private boolean rootSpanEnded = false;
  private final TraceComponent batchTraceComponent;
  private final Map<String, String> stepLocationNames = new HashMap<>();
  private Context rootContext;

  public BatchTransaction(String jobInstanceId, String traceId, String batchJobName,
      Span rootSpan, TraceComponent batchTraceComponent, Function<String, SpanBuilder> spanBuilderFunction,
      ConfigurationComponentLocator componentLocator) {
    super(jobInstanceId, traceId, batchJobName, batchTraceComponent.getStartTime());
    this.batchTraceComponent = batchTraceComponent;
    this.rootSpan = rootSpan;
    this.rootContext = rootSpan.storeInContext(Context.current());
    this.spanBuilderFunction = spanBuilderFunction;
    this.componentLocator = componentLocator;
    extractStepLocations(batchTraceComponent);
  }

  /**
   * List of job steps and locations are read while processing the batch:job
   * element.
   * This helps to know where steps are located.
   * 
   * @param batchTraceComponent
   * @{@link TraceComponent} with tags containing MULE_BATCH_JOB_STEPS
   */
  private void extractStepLocations(TraceComponent batchTraceComponent) {
    String jobSteps = batchTraceComponent.getTags().get(MULE_BATCH_JOB_STEPS.getKey());
    if (jobSteps != null) {
      StringTokenizer tokenizer = new StringTokenizer(jobSteps, ",");
      while (tokenizer.hasMoreTokens()) {
        String step = tokenizer.nextToken().trim();
        if (step.isEmpty()) {
          continue;
        }
        String stepName = step.substring(0, step.indexOf("|"));
        String stepLocation = step.substring(step.indexOf("|") + 1);
        stepLocationNames.put(stepLocation, stepName);
      }
    }
  }

  /**
   * Computes and Adds a container such as Step OR On-Complete block span if one
   * does not exist
   * 
   * @param location
   *            {@link String}
   * @param stepName
   *            {@link String}
   * @param processorTrace
   *            {@link TraceComponent}
   * @return A newly created or an existing {@link ContainerSpan} for this step
   *         location
   */
  private ContainerSpan addOrGetContainerSpan(final String location, String stepName, TraceComponent processorTrace) {
    AtomicReference<ContainerSpan> containerSpanRef = stepSpans.computeIfAbsent(location,
        s -> new AtomicReference<>());
    String name = BATCH_STEP_TAG;
    String spanName = stepName;
    if (ComponentsUtil.isBatchOnComplete(location, componentLocator)) {
      name = BATCH_ON_COMPLETE_TAG;
      spanName = BATCH_ON_COMPLETE_TAG;
    } else {
      name = BATCH_STEP_TAG + ":" + stepName;
    }
    ContainerSpan ContainerSpan = containerSpanRef.get();
    if (ContainerSpan == null) {
      TraceComponent stepTraceComponent = TraceComponent.of(name)
          .withSpanName(spanName)
          .withTags(new HashMap<>())
          .withTransactionId(processorTrace.getTransactionId())
          .withSpanKind(SpanKind.INTERNAL)
          .withLocation(location)
          .withEventContextId(processorTrace.getEventContextId())
          .withStartTime(processorTrace.getStartTime());
      processorTrace.getTags().forEach((k, v) -> {
        if (!k.startsWith("mule.app.processor")) {
          stepTraceComponent.getTags().put(k, v);
        }
      });
      if (stepName != null) {
        stepTraceComponent.getTags().put(MULE_BATCH_JOB_STEP_NAME.getKey(), stepName);
      }
      SpanBuilder spanBuilder = spanBuilderFunction.apply(name)
          .setParent(rootContext);
      ContainerSpan newContainerSpan = new ContainerSpan(location, spanBuilder.startSpan(),
          stepTraceComponent);
      if (containerSpanRef.compareAndSet(null, newContainerSpan)) {
        // This only executes if the update succeeds. If another thread updates the
        // reference, this will be skipped.
        stepProcessorSpans.putIfAbsent(stepTraceComponent.getSpanName(),
            newContainerSpan.getRootProcessorSpan());
        ContainerSpan = newContainerSpan;
      }
    }
    return ContainerSpan;
  }

  public SpanMeta addProcessorSpan(String containerPath, TraceComponent traceComponent, SpanBuilder spanBuilder) {
    SpanMeta spanMeta = null;
    String stepName = traceComponent.getTags().get(SemanticAttributes.MULE_BATCH_JOB_STEP_NAME.getKey());
    if (isBatchOnComplete(containerPath, componentLocator)) {
      stepName = BATCH_ON_COMPLETE_TAG;
    }
    ProcessorSpan containerProcessorSpan = getStepProcessorSpan(traceComponent, stepName);

    ContainerSpan ContainerSpan = null;
    if (containerProcessorSpan == null) {
      ContainerSpan = addOrGetContainerSpan(containerPath, stepLocationNames.get(containerPath), traceComponent);
      containerProcessorSpan = getStepProcessorSpan(traceComponent, stepName);
    } else {
      ContainerSpan = stepSpans.get(containerProcessorSpan.getLocation()).get();
    }

    if (containerProcessorSpan.getLocation().equalsIgnoreCase(containerPath)) {
      spanMeta = processContainerChild(containerPath, traceComponent, spanBuilder, ContainerSpan,
          containerProcessorSpan);
    } else {
      SpanMeta aggrSpan = addAggregatorSpanIfNeeded(containerPath, traceComponent, ContainerSpan,
          containerProcessorSpan);
      if (aggrSpan != null) {
        traceComponent.withContext(aggrSpan.getContext());
      }
      spanMeta = ContainerSpan.addProcessorSpan(containerPath, traceComponent, spanBuilder);
    }
    return spanMeta;
  }

  private SpanMeta addAggregatorSpanIfNeeded(String containerPath, TraceComponent traceComponent,
      ContainerSpan ContainerSpan,
      ProcessorSpan containerProcessorSpan) {
    SpanMeta aggrSpan = null;
    TraceComponent aggrTraceComponent = null;
    if (containerPath.endsWith("/aggregator")) {
      if (null == ContainerSpan.findSpan(traceComponent.contextScopedPath(containerPath))) {
        SpanBuilder aggrSpanBuilder = spanBuilderFunction.apply(BATCH_AGGREGATOR)
            .setParent(containerProcessorSpan.getContext())
            .setSpanKind(SpanKind.INTERNAL)
            .setStartTimestamp(traceComponent.getStartTime());
        aggrTraceComponent = TraceComponent.of(BATCH_AGGREGATOR)
            .withLocation(containerPath)
            .withSpanName(BATCH_AGGREGATOR)
            .withTags(new HashMap<>())
            .withContext(containerProcessorSpan.getContext())
            .withTransactionId(traceComponent.getTransactionId())
            .withSpanKind(SpanKind.INTERNAL)
            .withStartTime(traceComponent.getStartTime())
            .withEventContextId(traceComponent.getEventContextId())
            .withSiblings(traceComponent.getSiblings());
        copyBatchTags(traceComponent, aggrTraceComponent);
        aggrTraceComponent.getTags().put(MULE_APP_PROCESSOR_NAMESPACE.getKey(),
            "batch");
        aggrTraceComponent.getTags().put(MULE_APP_PROCESSOR_NAME.getKey(),
            "aggregator");
        aggrSpan = ContainerSpan.addProcessorSpan(
            containerPath.substring(0, containerPath.lastIndexOf("/")),
            aggrTraceComponent,
            aggrSpanBuilder);
      }
    }
    return aggrSpan;
  }

  /**
   * Creates a new span when target is processor 0 in the step, otherwise
   * adds a new span under existing record parent.
   *
   * When processing on-complete block, adds spans to the parent block.
   * 
   * @param containerPath
   *            {@link String} must be a step location
   * @param traceComponent
   *            {@link TraceComponent}
   * @param spanBuilder
   *            {@link SpanBuilder}
   * @param stepSpan
   *            {@link ContainerSpan}
   * @param processorSpan
   *            {@link ProcessorSpan}
   * @return Newly created span for a target component
   */
  private SpanMeta processContainerChild(String containerPath, TraceComponent traceComponent,
      SpanBuilder spanBuilder,
      ContainerSpan stepSpan, ProcessorSpan processorSpan) {
    SpanMeta spanMeta;
    if (isBatchOnComplete(containerPath, componentLocator)) {
      // String onCompletePath = containerPath + "/on-complete";
      spanMeta = stepSpan.addProcessorSpan(containerPath, traceComponent, spanBuilder);
    } else {
      String recordPath = containerPath + "/record";
      if (traceComponent.getLocation().equalsIgnoreCase(containerPath + "/processors/0")) {
        // Create a record span
        TraceComponent recordTrace = TraceComponent.of(BATCH_STEP_RECORD_TAG)
            .withLocation(recordPath)
            .withTransactionId(traceComponent.getTransactionId())
            .withStartTime(traceComponent.getStartTime())
            .withSpanName(BATCH_STEP_RECORD_TAG)
            .withTags(traceComponent.getTags())
            .withEventContextId(traceComponent.getEventContextId());
        SpanBuilder record = spanBuilderFunction.apply(recordTrace.getName());

        SpanMeta recordSpanMeta = stepSpan.addChildContainer(recordTrace,
            record.setParent(processorSpan.getContext()));
        spanMeta = stepSpan.addProcessorSpan(recordTrace.getLocation(), traceComponent,
            spanBuilder.setParent(recordSpanMeta.getContext()));
      } else {
        spanMeta = stepSpan.addProcessorSpan(recordPath, traceComponent, spanBuilder);
      }
    }
    return spanMeta;
  }

  private ProcessorSpan getStepProcessorSpan(TraceComponent traceComponent, String containerName) {
    if (containerName == null) {
      return null;
    }
    ProcessorSpan processorSpan = stepProcessorSpans.get(containerName);
    return processorSpan == null ? null : processorSpan.setTags(traceComponent.getTags());
  }

  private ProcessorSpan getStepProcessorSpan(TraceComponent traceComponent) {
    String containerName = traceComponent.getTags().get(SemanticAttributes.MULE_BATCH_JOB_STEP_NAME.getKey());
    return getStepProcessorSpan(traceComponent, containerName);
  }

  @Override
  public SpanMeta endProcessorSpan(TraceComponent traceComponent, Consumer<Span> spanUpdater, Instant endTime) {
    if (BATCH_STEP_TAG.equalsIgnoreCase(traceComponent.getName())
        || BATCH_ON_COMPLETE_TAG.equalsIgnoreCase(traceComponent.getName())) {
      return endContainerSpan(traceComponent, stepProcessorSpans.get(traceComponent.getSpanName()));
    } else {
      String spanName = traceComponent.getTags().get(SemanticAttributes.MULE_BATCH_JOB_STEP_NAME.getKey());
      if (spanName == null) {
        String locationParent = getLocationParent(traceComponent.getLocation());
        if (isBatchOnComplete(locationParent, componentLocator)) {
          spanName = BATCH_ON_COMPLETE_TAG;
        }
      }
      if (spanName != null) {
        String stepLocation = stepProcessorSpans.get(spanName).getLocation();
        ContainerSpan ContainerSpan = stepSpans.get(stepLocation).get();
        SpanMeta spanMeta = ContainerSpan.endProcessorSpan(traceComponent, spanUpdater, endTime);
        String aggregatorLocation = stepLocation + "/aggregator";
        if (traceComponent.getLocation() != null
            && traceComponent.getLocation().startsWith(aggregatorLocation)) {
          ProcessorSpan aggrSpan = ContainerSpan
              .findSpan(traceComponent.contextScopedPath(aggregatorLocation));
          if (aggrSpan.getSiblings() == 0) {
            TraceComponent aggrTraceComponent = TraceComponent.of(BATCH_AGGREGATOR)
                .withLocation(aggregatorLocation)
                .withSpanName(BATCH_AGGREGATOR)
                .withTags(new HashMap<>())
                .withTransactionId(traceComponent.getTransactionId())
                .withSpanKind(SpanKind.INTERNAL)
                .withEventContextId(traceComponent.getEventContextId())
                .withEndTime(traceComponent.getEndTime());
            aggrTraceComponent.getTags().putAll(traceComponent.getTags());
            ContainerSpan.endProcessorSpan(aggrTraceComponent, spanUpdater, endTime);
          }
        }
        return spanMeta;
      }
    }
    return null;
  }

  private ProcessorSpan endContainerSpan(TraceComponent traceComponent, ProcessorSpan processorSpan) {
    if (processorSpan == null)
      return null;
    ContainerSpan stepSpan = stepSpans.get(processorSpan.getLocation()).get();
    processorSpan.setEndTime(traceComponent.getEndTime());
    processorSpan.getTags().putAll(traceComponent.getTags());
    stepSpan.getSpan().end(traceComponent.getEndTime());
    return processorSpan;
  }

  @Override
  public Span getTransactionSpan() {
    return rootSpan;
  }

  @Override
  public void endRootSpan(TraceComponent traceComponent, Consumer<Span> endSpan) {
    this.stepProcessorSpans.forEach((location, processorSpan) -> {
      endContainerSpan(traceComponent, processorSpan);
    });
    super.endRootSpan(traceComponent, endSpan);
    rootSpanEnded = true;
  }

  @Override
  public boolean hasEnded() {
    return rootSpanEnded;
  }

  @Override
  public void addChildTransaction(TraceComponent traceComponent, SpanBuilder spanBuilder) {
    ProcessorSpan processorSpan = getStepProcessorSpan(traceComponent);
    ContainerSpan stepSpan = stepSpans.get(processorSpan.getLocation()).get();
    stepSpan.addChildContainer(traceComponent, spanBuilder);
  }

  @Override
  public TransactionMeta endChildTransaction(TraceComponent traceComponent, Consumer<Span> endSpan) {
    ProcessorSpan processorSpan = getStepProcessorSpan(traceComponent);
    ContainerSpan stepSpan = stepSpans.get(processorSpan.getLocation()).get();
    return stepSpan.endChildContainer(traceComponent, endSpan);
  }

  @Override
  public ProcessorSpan findSpan(String location) {
    ProcessorSpan processorSpan = null;
    for (Map.Entry<String, AtomicReference<ContainerSpan>> entry : stepSpans.entrySet()) {
      if ((processorSpan = entry.getValue().get().findSpan(location)) != null) {
        return processorSpan;
      }
    }
    return null;
  }

  @Override
  public Span getSpan() {
    return rootSpan;
  }

  @Override
  public Map<String, String> getTags() {
    return batchTraceComponent.getTags();
  }
}
