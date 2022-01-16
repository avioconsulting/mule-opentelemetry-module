package com.avioconsulting.mule.opentelemetry.test.util;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class Span {
  private String spanName;
  private String traceId;
  private String spanId;
  private String spanKind;
  private String rawString;

  public String getSpanName() {
    return spanName;
  }

  public String getTraceId() {
    return traceId;
  }

  public String getSpanId() {
    return spanId;
  }

  public String getSpanKind() {
    return spanKind;
  }

  public String getRawString() {
    return rawString;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("spanName", spanName)
        .append("traceId", traceId)
        .append("spanId", spanId)
        .append("spanKind", spanKind)
        .append("rawString", rawString)
        .toString();
  }

  public static Span fromString(String spanString) {
    String[] spaceSplit = spanString.split(" ");
    Span span = new Span();
    span.rawString = spanString;
    span.spanName = spaceSplit[0];
    span.traceId = spaceSplit[2];
    span.spanId = spaceSplit[3];
    span.spanKind = spaceSplit[4];
    return span;
  }

  public static List<Span> fromStrings(Collection<String> spanStrings) {
    return spanStrings.stream().map(Span::fromString).collect(Collectors.toList());
  }
}
