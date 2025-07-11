package com.avioconsulting.mule.opentelemetry.api.traces;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;

import java.time.Instant;
import java.util.Map;

public class TraceComponent implements ComponentEventContext {
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
  private ComponentLocation componentLocation;

  /**
   * Number of processors in the same container
   */
  private long siblings = -1;

  private TraceComponent(String name) {
    this.name = name;
  }

  public static TraceComponent of(String name) {
    return new TraceComponent(name);
  }

  public static TraceComponent of(String name, ComponentLocation location) {
    return of(name)
        .withLocation(location.getLocation())
        .withComponentLocation(location);
  }

  public static TraceComponent of(Component component) {
    return of(component.getLocation());
  }

  public static TraceComponent of(ComponentLocation location) {
    return of(location.getLocation(), location);
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

  public ComponentLocation getComponentLocation() {
    return componentLocation;
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

  public TraceComponent withComponentLocation(ComponentLocation componentLocation) {
    this.componentLocation = componentLocation;
    return this;
  }

  public StatusCode getStatusCode() {
    return statusCode;
  }

  public TraceComponent withSiblings(long siblings) {
    this.siblings = siblings;
    return this;
  }

  public long getSiblings() {
    return siblings;
  }

  @Override
  public String toString() {
    return "TraceComponent{" +
        "tags=" + tags +
        ", name='" + name + '\'' +
        ", transactionId='" + transactionId + '\'' +
        ", spanName='" + spanName + '\'' +
        ", location='" + location + '\'' +
        ", context=" + context +
        ", spanKind=" + spanKind +
        ", errorMessage='" + errorMessage + '\'' +
        ", statusCode=" + statusCode +
        ", startTime=" + startTime +
        ", endTime=" + endTime +
        ", eventContextId='" + eventContextId + '\'' +
        ", componentLocation=" + componentLocation +
        '}';
  }
}
