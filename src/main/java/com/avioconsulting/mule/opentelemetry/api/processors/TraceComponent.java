package com.avioconsulting.mule.opentelemetry.api.processors;

import io.opentelemetry.context.Context;

import java.util.Map;

public class TraceComponent {
    private Map<String, String> tags;
    private final String name;
    private String transactionId;
    private String spanId;
    private Context context;

    private TraceComponent(Builder builder) {
        tags = builder.tags;
        name = builder.name;
        transactionId = builder.transactionId;
        spanId = builder.spanId;
        context = builder.context;
    }

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
    public Context getContext() {
        return context;
    }

    public static final class Builder {
        private Map<String, String> tags;
        private final String name;
        private String transactionId;
        private String spanId;
        private Context context;

        public Builder(String name) {
            this.name = name;
        }

        public Builder tags(Map<String, String> val) {
            tags = val;
            return this;
        }

        public Builder transactionId(String val) {
            transactionId = val;
            return this;
        }

        public Builder spanId(String val) {
            spanId = val;
            return this;
        }

        public Builder context(Context val) {
            context = val;
            return this;
        }

        public TraceComponent build() {
            return new TraceComponent(this);
        }
    }
}
