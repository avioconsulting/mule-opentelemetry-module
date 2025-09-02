package com.avioconsulting.mule.opentelemetry.internal.util;

import com.avioconsulting.mule.opentelemetry.api.store.TransactionStore;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.metadata.TypedValue;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.Map;

import static com.avioconsulting.mule.opentelemetry.api.store.TransactionStore.*;

public class MDCUtil {

  private static final TypedValue<Map<Object, Object>> EMPTY_MAP_VALUE = TypedValue.of(Collections.emptyMap());
  public static final String TRACE_ID = "trace_id";
  public static final String SPAN_ID = "span_id";

  public static void replaceMDCOtelEntries(Event event) {
    if (event == null)
      return;
    replaceMDCOtelEntriesFromVars(event.getVariables());
  }

  public static void replaceMDCOtelEntriesFromVars(Map<String, TypedValue<?>> variables) {
    TypedValue<Map<String, Object>> contextMap = (TypedValue<Map<String, Object>>) variables
        .getOrDefault(TransactionStore.TRACE_CONTEXT_MAP_KEY, EMPTY_MAP_VALUE);
    Map<String, Object> context = contextMap.getValue();
    replaceMDCOtelEntries(context);
  }

  public static void replaceMDCOtelEntries(Map<String, Object> context) {
    if (context == null || context.isEmpty())
      return;
    replaceMDCOtelEntry(context, traceId, TRACE_ID);
    replaceMDCOtelEntry(context, spanId, SPAN_ID);
  }

  private static void replaceMDCOtelEntry(Map<String, Object> contextMap, String sourceKey, String targetKey) {
    if (contextMap.containsKey(sourceKey)) {
      String mdcValue = MDC.get(sourceKey);
      if (mdcValue != null &&
          mdcValue.equalsIgnoreCase(contextMap.get(sourceKey).toString())) {
        return;
      }
      MDC.remove(targetKey);
      MDC.put(targetKey, contextMap.get(sourceKey).toString());
    }
  }
}
