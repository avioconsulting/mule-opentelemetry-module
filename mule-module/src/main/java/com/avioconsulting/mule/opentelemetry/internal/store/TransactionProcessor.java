package com.avioconsulting.mule.opentelemetry.internal.store;

import com.avioconsulting.mule.opentelemetry.api.store.SpanMeta;
import com.avioconsulting.mule.opentelemetry.api.store.TransactionMeta;
import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentRegistryService;
import io.opentelemetry.api.trace.SpanBuilder;
import org.mule.runtime.api.message.Error;

public interface TransactionProcessor {
  void addProcessorSpan(TraceComponent traceComponent, String containerName);

  SpanMeta endProcessorSpan(TraceComponent traceComponent, Error error);

  void startTransaction(TraceComponent traceComponent);

  TransactionMeta endTransaction(TraceComponent traceComponent, Exception exception);

  SpanBuilder spanBuilder(String name);

  ComponentRegistryService getComponentRegistryService();
}
