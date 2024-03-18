package com.avioconsulting.mule.opentelemetry.api.traces;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

import java.time.Instant;
import java.util.Map;

public class TraceComponent {
  private Map<String, String> tags;
  private final String name;
  private String transactionId;
  private String spanName;
  private String location;
  private Context context;
  private SpanKind spanKind;
  private String errorMessage;
  private StatusCode statusCode;
  private Instant startTime = Instant.now();
  private Instant endTime;
  private String eventContextId;

  private TraceComponent(String name) {
    this.name = name;
  }

  public static TraceComponent named(String name) {
    return new TraceComponent(name);
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

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getStartTime() {
    return startTime;
  }

  public Instant getEndTime() {
    return this.endTime;
  }

  public String getEventContextId() {
    return eventContextId;
  }

  public String contextScopedPath(String path) {
    return getEventContextId() + "/" + path;
  }

  public String contextScopedLocation() {
    return getEventContextId() + "/" + getLocation();
  }

  public TraceComponent withTags(Map<String, String> val) {
    tags = val;
    return this;
  }

  public TraceComponent withTransactionId(String val) {
    transactionId = val;
    return this;
  }

  public TraceComponent withSpanName(String val) {
    spanName = val;
    return this;
  }

  public TraceComponent withLocation(String val) {
    location = val;
    return this;
  }

  public TraceComponent withContext(Context val) {
    context = val;
    return this;
  }

  public TraceComponent withSpanKind(SpanKind val) {
    spanKind = val;
    return this;
  }

  public TraceComponent withErrorMessage(String val) {
    errorMessage = val;
    return this;
  }

  public TraceComponent withStatsCode(StatusCode statusCode) {
    this.statusCode = statusCode;
    return this;
  }

  public TraceComponent withStartTime(Instant startTime) {
    this.startTime = startTime;
    return this;
  }

  public TraceComponent withEndTime(Instant endTime) {
    this.endTime = endTime;
    return this;
  }

  public TraceComponent withEventContextId(String eventContextId) {
    this.eventContextId = eventContextId;
    return this;
  }

  public StatusCode getStatusCode() {
    return statusCode;
  }

}
