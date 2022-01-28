package com.avioconsulting.mule.opentelemetry.test.util;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Span {
  private String spanName;
  private String traceId;
  private String spanId;
  private String spanKind;
  private String rawString;
  private Attributes attributes;

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

  public Attributes getAttributes() {
    return attributes;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("spanName", spanName)
        .append("traceId", traceId)
        .append("spanId", spanId)
        .append("spanKind", spanKind)
        .append("rawString", rawString)
        .append("attributes", attributes)
        .toString();
  }

  /**
   * Parse the Span String into @{@link Span} object. Parsing is based on logic
   * in @{@link io.opentelemetry.exporter.logging.LoggingSpanExporter#export(Collection)}
   * class.
   * 
   * @param spanString
   * @return
   */
  public static Span fromString(String spanString) {

    Span span = new Span();
    span.rawString = spanString;
    String spanEndSign = "' : ";
    span.spanName = spanString.substring(0, spanString.indexOf(spanEndSign) + 1).trim();

    String idString = spanString
        .substring(spanString.indexOf(spanEndSign) + spanEndSign.length(), spanString.indexOf("[tracer:"))
        .trim();
    String[] idParts = idString.split(" ");
    span.traceId = idParts[0];
    span.spanId = idParts[1];
    span.spanKind = idParts[2];

    String attrMapKey = "AttributesMap{";
    String attrMap = spanString.substring(spanString.indexOf(attrMapKey) + attrMapKey.length(),
        spanString.indexOf("}", spanString.indexOf("totalAddedValues")));
    String dataMapKey = "data={";
    String dataMapString = attrMap.substring(attrMap.indexOf(dataMapKey) + 6, attrMap.indexOf("}", 6));
    Map<String, String> dataAttributes = Stream.of(dataMapString.split(","))
        .collect(Collectors.toMap(key -> key.split("=")[0].trim(), value -> value.split("=")[1].trim()));

    Attributes attributes = new Attributes();
    attributes.dataMap = Collections.unmodifiableMap(dataAttributes);
    span.attributes = attributes;
    return span;
  }

  public static List<Span> fromStrings(Collection<String> spanStrings) {
    return spanStrings.stream().map(Span::fromString).collect(Collectors.toList());
  }

  public static class Attributes {
    private Map<String, String> dataMap;

    public Map<String, String> getDataMap() {
      return dataMap;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("dataMap", dataMap)
          .toString();
    }
  }
}
