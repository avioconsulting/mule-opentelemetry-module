package com.avioconsulting.mule.opentelemetry.internal.processor.service;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.util.LazyValue;
import org.mule.runtime.core.api.el.ExpressionManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ProcessorComponentService {
  private static ProcessorComponentService service;
  private final List<ProcessorComponent> processorComponents;
  private static LazyValue<ProcessorComponentService> value = new LazyValue<>(new ProcessorComponentService());
  private final Map<ComponentIdentifier, ProcessorComponent> cachedMap = new ConcurrentHashMap<>();

  private ProcessorComponentService() {
    ServiceLoader<ProcessorComponent> loader = ServiceLoader.load(ProcessorComponent.class,
        ProcessorComponent.class.getClassLoader());
    List<ProcessorComponent> lst = new ArrayList<>();
    loader.iterator().forEachRemaining(lst::add);
    processorComponents = Collections.unmodifiableList(lst);
  }

  public static synchronized ProcessorComponentService getInstance() {
    return value.get();
  }

  /**
   * Replace cached providers and their application dependencies between tests.
   * Only call before creating a test Mule context, never during application
   * execution.
   */
  public static synchronized void _resetForTesting() {
    value = new LazyValue<>(new ProcessorComponentService());
  }

  public ProcessorComponent getProcessorComponentFor(ComponentIdentifier identifier,
      ExpressionManager expressionManager,
      ComponentRegistryService componentRegistryService) {
    return cachedMap.computeIfAbsent(identifier, id -> {
      for (ProcessorComponent pc : processorComponents) {
        if (pc.canHandle(identifier)) {
          pc
              .withExpressionManager(expressionManager)
              .withComponentRegistryService(componentRegistryService);
          return pc;
        }
      }
      return null;
    });
  }
}
