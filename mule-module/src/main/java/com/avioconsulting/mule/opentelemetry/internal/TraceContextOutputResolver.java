package com.avioconsulting.mule.opentelemetry.internal;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataType;
import org.mule.runtime.api.metadata.MetadataContext;
import org.mule.runtime.api.metadata.resolving.OutputTypeResolver;

public class TraceContextOutputResolver implements OutputTypeResolver<String> {
  private static final String RESOLVER_NAME = "TraceContextOutputResolver";
  private static final String CATEGORY_NAME = "TRACE_CONTEXT";

  // Field name constants
  private static final String TRACE_TRANSACTION_ID = "TRACE_TRANSACTION_ID";
  private static final String TRACE_ID = "traceId";
  private static final String SPAN_ID = "spanId";
  private static final String TRACEPARENT = "traceparent";
  private static final String TRACESTATE = "tracestate";

  @Override
  public String getResolverName() {
    return RESOLVER_NAME;
  }

  @Override
  public String getCategoryName() {
    return CATEGORY_NAME;
  }

  @Override
  public MetadataType getOutputType(MetadataContext context, String key) {

    BaseTypeBuilder typeBuilder = context.getTypeBuilder();

    ObjectTypeBuilder builder = typeBuilder.objectType()
        .id("trace-context")
        .description("Complete trace context with OpenTelemetry and W3C fields");

    addTransactionIdField(builder, typeBuilder, true);
    addTraceIdHexField(builder, typeBuilder, true);
    addSpanIdHexField(builder, typeBuilder, true);
    addTraceparentField(builder, typeBuilder, true);
    addTracestateField(builder, typeBuilder, false);

    return builder.build();
  }

  private void addTransactionIdField(ObjectTypeBuilder builder, BaseTypeBuilder typeBuilder, boolean required) {
    builder.addField()
        .key(TRACE_TRANSACTION_ID)
        .value(typeBuilder.stringType()
            .id("uuid-string"))
        .description("UUID format transaction identifier")
        .required(required);
  }

  private void addTraceIdHexField(ObjectTypeBuilder builder, BaseTypeBuilder typeBuilder, boolean required) {
    builder.addField()
        .key(TRACE_ID)
        .value(typeBuilder.stringType()
            .id("trace-id-hex"))
        .description("32-character hexadecimal trace ID")
        .required(required);
  }

  private void addSpanIdHexField(ObjectTypeBuilder builder, BaseTypeBuilder typeBuilder, boolean required) {
    builder.addField()
        .key(SPAN_ID)
        .value(typeBuilder.stringType()
            .id("span-id-hex"))
        .description("16-character hexadecimal span ID")
        .required(required);
  }

  private void addTraceparentField(ObjectTypeBuilder builder, BaseTypeBuilder typeBuilder, boolean required) {
    builder.addField()
        .key(TRACEPARENT)
        .value(typeBuilder.stringType()
            .id("w3c-traceparent"))
        .description("W3C traceparent: version-traceId-spanId-flags")
        .required(required);
  }

  private void addTracestateField(ObjectTypeBuilder builder, BaseTypeBuilder typeBuilder, boolean required) {
    builder.addField()
        .key(TRACESTATE)
        .value(typeBuilder.stringType()
            .id("w3c-tracestate"))
        .description("W3C tracestate header value")
        .required(required);
  }
}
