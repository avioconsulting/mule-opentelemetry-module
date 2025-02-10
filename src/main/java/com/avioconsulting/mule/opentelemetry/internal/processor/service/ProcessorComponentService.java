package com.avioconsulting.mule.opentelemetry.internal.processor.service;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.util.LazyValue;
import org.mule.runtime.core.api.el.ExpressionManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ProcessorComponentService {
  private static ProcessorComponentService service;
  private final List<ProcessorComponent> processorComponents;
  private static final LazyValue<ProcessorComponentService> VALUE = new LazyValue<>(new ProcessorComponentService());
  private final List<ProcessorComponent> cached = new ArrayList<>();
  private final Map<ComponentIdentifier, ProcessorComponent> cachedMap = new ConcurrentHashMap<>();

  private ProcessorComponentService() {
    ServiceLoader<ProcessorComponent> loader = ServiceLoader.load(ProcessorComponent.class,
        ProcessorComponent.class.getClassLoader());
    List<ProcessorComponent> lst = new ArrayList<>();
    loader.iterator().forEachRemaining(lst::add);
    processorComponents = Collections.unmodifiableList(lst);
  }

  public static synchronized ProcessorComponentService getInstance() {
    return VALUE.get();
  }

  public ProcessorComponent getProcessorComponentFor(ComponentIdentifier identifier,
      ConfigurationComponentLocator configurationComponentLocator, ExpressionManager expressionManager) {
    for (ProcessorComponent pc : processorComponents) {
      if (pc.canHandle(identifier)) {
        pc.withConfigurationComponentLocator(configurationComponentLocator)
            .withExpressionManager(expressionManager);
        return pc;
      }
    }
    return null;
  }
}
