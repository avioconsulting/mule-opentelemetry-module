package com.avioconsulting.mule.opentelemetry.internal.notifications.listeners;

import com.avioconsulting.mule.opentelemetry.api.store.TransactionStore;
import com.avioconsulting.mule.opentelemetry.internal.processor.MuleNotificationProcessor;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.metadata.TypedValue;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.Map;

public abstract class AbstractMuleNotificationListener {

  protected final MuleNotificationProcessor muleNotificationProcessor;

  public AbstractMuleNotificationListener(MuleNotificationProcessor muleNotificationProcessor) {
    this.muleNotificationProcessor = muleNotificationProcessor;
  }

  protected void replaceMDCEntry(Event event) {
    TypedValue<Map<String, String>> contextMap = (TypedValue<Map<String, String>>) event.getVariables()
        .getOrDefault(TransactionStore.TRACE_CONTEXT_MAP_KEY, TypedValue.of(Collections.emptyMap()));
    Map<String, String> context = contextMap.getValue();
    replaceMDCEntry(context, "traceId");
    replaceMDCEntry(context, "traceIdLongLowPart");
    replaceMDCEntry(context, "spanId");
    replaceMDCEntry(context, "spanIdLong");
  }

  private void replaceMDCEntry(Map<String, String> contextMap, String key) {
    if (contextMap.containsKey(key)) {
      MDC.remove(key);
      MDC.put(key, contextMap.get(key));
    }
  }
}
