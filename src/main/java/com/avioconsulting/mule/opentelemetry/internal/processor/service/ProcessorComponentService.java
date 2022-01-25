package com.avioconsulting.mule.opentelemetry.internal.processor.service;

import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.location.ConfigurationComponentLocator;
import org.mule.runtime.api.util.LazyValue;

import java.util.*;

public class ProcessorComponentService {
  private static ProcessorComponentService service;
  private final List<ProcessorComponent> processorComponents;
  private static final LazyValue<ProcessorComponentService> VALUE = new LazyValue<>(new ProcessorComponentService());
  private final List<ProcessorComponent> cached = new ArrayList<>();

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

  public Optional<ProcessorComponent> getProcessorComponentFor(ComponentIdentifier identifier,
      ConfigurationComponentLocator configurationComponentLocator) {
    Optional<ProcessorComponent> cachedPC = cached.stream().filter(p -> p.canHandle(identifier))
        .findFirst();
    if (!cachedPC.isPresent()) {
      processorComponents.stream().filter(p -> p.canHandle(identifier))
          .findFirst()
          .map(pc -> pc.withConfigurationComponentLocator(configurationComponentLocator))
          .ifPresent(cached::add);
      cachedPC = cached.stream().filter(p -> p.canHandle(identifier))
          .findFirst();
    }
    return cachedPC;
  }
}
