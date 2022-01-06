package com.avioconsulting.mule.opentelemetry.api.processors;

import java.util.Collections;
import java.util.Map;

public class TraceComponent {
    private final Map<String, String> tags;
    private final String name;
    private final String transactionId;
    private final String spanId;

    public Map<String, String> getTags() {
        return tags;
    }

    public String getName() {
        return name;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getSpanId() {
        return spanId;
    }

    public TraceComponent(Map<String, String> tags, String name, String transactionId) {
        this(tags, name, transactionId, null);
    }

    public TraceComponent(Map<String, String> tags, String name, String transactionId, String spanId) {
        this.tags = Collections.unmodifiableMap(tags);
        this.name = name;
        this.transactionId = transactionId;
        this.spanId = spanId;
    }
}
