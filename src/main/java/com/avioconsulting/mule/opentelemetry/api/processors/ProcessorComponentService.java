package com.avioconsulting.mule.opentelemetry.api.processors;

import org.mule.runtime.api.component.ComponentIdentifier;

import java.util.*;

public class ProcessorComponentService {
    private static ProcessorComponentService service;
    private final List<ProcessorComponent> processorComponents ;

    private ProcessorComponentService() {
        ServiceLoader<ProcessorComponent> loader = ServiceLoader.load(ProcessorComponent.class);
        List<ProcessorComponent> lst = new ArrayList<>();
        loader.iterator().forEachRemaining(lst::add);
        processorComponents = Collections.unmodifiableList(lst);
    }
    public static synchronized ProcessorComponentService getInstance() {
        if (service == null) {
            service = new ProcessorComponentService();
        }
        return service;
    }
    public Optional<ProcessorComponent> getProcessorComponentFor(ComponentIdentifier identifier){
        ProcessorComponent processorComponent = null;
        return processorComponents.stream().filter(p -> p.canHandle(identifier)).findFirst();
    }
}
