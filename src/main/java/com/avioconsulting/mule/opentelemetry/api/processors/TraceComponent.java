package com.avioconsulting.mule.opentelemetry.api.processors;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;

import java.util.Map;

public class TraceComponent {
    private Map<String, String> tags;
    private final String name;
    private String transactionId;
    private String spanName;
    private String location;
    private Context context;
    private SpanKind spanKind = SpanKind.INTERNAL;

    private TraceComponent(Builder builder) {
        tags = builder.tags;
        name = builder.name;
        transactionId = builder.transactionId;
        spanName = builder.spanName;
        location = builder.location;
        context = builder.context;
        spanKind = builder.spanKind;
    }

    public static Builder newBuilder(String name) {
        return new Builder(name);
    }

    public SpanKind getSpanKind() {
        return spanKind;
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

    public String getSpanName() {
        return spanName;
    }
    public Context getContext() {
        return context;
    }

    public String getLocation() {
        return location;
    }

    public static final class Builder {
        private Map<String, String> tags;
        private final String name;
        private String transactionId;
        private String spanName;
        private String location;
        private Context context;
        private SpanKind spanKind;

        private Builder(String name) {
            this.name = name;
        }

        public Builder withTags(Map<String, String> val) {
            tags = val;
            return this;
        }

        public Builder withTransactionId(String val) {
            transactionId = val;
            return this;
        }

        public Builder withSpanName(String val) {
            spanName = val;
            return this;
        }

        public Builder withLocation(String val) {
            location = val;
            return this;
        }

        public Builder withContext(Context val) {
            context = val;
            return this;
        }

        public Builder withSpanKind(SpanKind val) {
            spanKind = val;
            return this;
        }

        public TraceComponent build() {
            return new TraceComponent(this);
        }
    }
}
