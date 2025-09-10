package com.avioconsulting.mule.opentelemetry.api.traces;

import com.avioconsulting.mule.opentelemetry.internal.util.StringUtil;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.location.ComponentLocation;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.avioconsulting.mule.opentelemetry.internal.util.StringUtil.UNDERSCORE;
import static com.avioconsulting.mule.opentelemetry.internal.util.StringUtil.UNDERSCORE_CHAR;

/**
 * Represents a traceable component in an application, capturing metadata such
 * as tags, context,
 * and span information. This class implements various interfaces for
 * extensibility and manages
 * operations related to trace tags, component event context, and lifecycle
 * control.
 */
public class TraceComponent implements ComponentEventContext, AutoCloseable, Clearable, Taggable<String, String> {
  private final Map<String, String> tags;
  private String name;
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
  private String contextScopedLocation = null;
  private int contextNestingLevel = 0;
  /**
   * Number of processors in the same container
   */
  private long siblings = -1;
  private String eventContextPrimaryId;

  protected TraceComponent(String name, Map<String, String> tags) {
    this.name = name;
    this.tags = tags;
  }

  public static TraceComponent of(String name, Map<String, String> tags) {
    return new TraceComponent(name, tags);
  }

  /**
   * Use {@link TraceComponent#of(String, Map)} to explicitly assign the tag
   * {@link Map}
   * 
   * @param name
   *            Trace component name
   * @return
   */
  @Deprecated
  public static TraceComponent of(String name) {
    return new TraceComponent(name, new HashMap<>());
  }

  /**
   * Use {@link TraceComponent#of(String, Map)} to explicitly assign the tag
   * {@link Map}
   *
   * @param name
   *            Trace component name
   * @return
   */
  @Deprecated
  public static TraceComponent of(String name, ComponentLocation location) {
    return of(name, new HashMap<>())
        .withLocation(location.getLocation())
        .withComponentLocation(location);
  }

  /**
   * Use {@link TraceComponent#of(String, Map)} to explicitly assign the tag
   * {@link Map}
   *
   * @param component
   *            Trace component name
   * @return
   */
  @Deprecated
  public static TraceComponent of(Component component) {
    return of(component.getLocation());
  }

  /**
   * Use {@link TraceComponent#of(String, Map)} to explicitly assign the tag
   * {@link Map}
   *
   * @param location
   *            Trace component name
   * @return
   */
  @Deprecated
  public static TraceComponent of(ComponentLocation location) {
    return of(location.getLocation(), location);
  }

  public SpanKind getSpanKind() {
    return spanKind;
  }

  /**
   * Retrieves a map of tags associated with the current trace component.
   * This method is deprecated and should not be used for accessing or
   * manipulating tags.
   * Use {@link TraceComponent#getReadOnlyTags()} for read-only access to tags.
   * For tag manipulation, use the individual methods on {@link TraceComponent}.
   * This has been deprecated to prevent memory leaks of internal {@link Map}
   * instance.
   * 
   * @return a map of tags as key-value pairs, if this method were implemented.
   *         However, this method will throw an
   *         {@link UnsupportedOperationException}.
   */
  @Deprecated
  public Map<String, String> getTags() {
    throw new UnsupportedOperationException(
        "Use TraceComponent.getReadOnlyTags() for read-only access to tags instead. For manipulating tags, use individual methods on TraceComponent");
  }

  /**
   * Retrieves a read-only view of the tags associated with this trace component.
   * The returned map is unmodifiable to ensure that the tags cannot be altered.
   * Should be used during testing only.
   * 
   * @return an unmodifiable map containing the tags as key-value pairs
   */
  public Map<String, String> getReadOnlyTags() {
    return Collections.unmodifiableMap(tags);
  }

  public TraceComponent setName(String name) {
    this.name = name;
    return this;
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
    throw new UnsupportedOperationException("Use TraceComponent.of(String, Map<String,String>) instead");
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
    if (val == null) {
      contextScopedLocation = null;
      return this;
    }
    if (getEventContextId() != null && contextScopedLocation == null) {
      contextScopedLocation = getEventContextId() + "/" + val;
    }
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
    if (eventContextId == null) {
      contextScopedLocation = null;
      eventContextPrimaryId = null;
      contextNestingLevel = 0;
      return this;
    }
    if (getLocation() != null && contextScopedLocation == null) {
      contextScopedLocation = eventContextId + "/" + getLocation();
    }
    eventContextPrimaryId = eventContextId.contains(UNDERSCORE)
        ? eventContextId.substring(0, eventContextId.indexOf(UNDERSCORE))
        : eventContextId;
    contextNestingLevel = StringUtil.countParts(eventContextId, UNDERSCORE_CHAR);
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

  public String contextScopedLocation() {
    return contextScopedLocation;
  }

  public String getEventContextPrimaryId() {
    return eventContextPrimaryId;
  }

  public int contextNestingLevel() {
    return contextNestingLevel;
  }

  public void addTag(String key, String value) {
    if (tags != null) {
      tags.put(key, value);
    }
  }

  public String getTag(String key) {
    if (tags != null) {
      return tags.get(key);
    }
    return null;
  }

  public void addAllTags(Map<String, String> source) {
    if (tags != null) {
      this.tags.putAll(source);
    }
  }

  public String removeTag(String key) {
    if (tags != null) {
      return tags.remove(key);
    }
    return null;
  }

  public void copyTagsTo(Map<String, String> target) {
    if (tags != null && target != null) {
      target.putAll(tags);
    }
  }

  @Override
  public void copyTagsTo(Taggable<String, String> target) {
    if (!(target instanceof TraceComponent))
      return;
    if (tags != null && ((TraceComponent) target).tags != null) {
      ((TraceComponent) target).tags.putAll(tags);
    }
  }

  @Override
  public void copyTagsTo(Taggable<String, String> target, Predicate<String> keyFilter) {
    if (!(target instanceof TraceComponent))
      return;
    TraceComponent other = ((TraceComponent) target);
    if (tags != null && other.tags != null) {
      tags.forEach((key, value) -> {
        if (keyFilter.test(key)) {
          other.tags.put(key, value);
        }
      });
    }
  }

  @Override
  public boolean hasTagFor(String key) {
    return tags != null && tags.containsKey(key);
  }

  @Override
  public boolean hasTags() {
    return tags != null && !tags.isEmpty();
  }

  @Override
  public boolean containsTag(String key, String value) {
    return tags != null && tags.containsKey(key) && tags.get(key).equals(value);
  }

  @Override
  public void forEachTagEntry(Consumer<Map.Entry<String, String>> consumer) {
    if (tags.isEmpty())
      return;
    tags.entrySet().forEach(consumer);
  }

  /**
   * Clears all fields for returning to pool.
   */
  public void clear() {
    // Clear all fields to their default values
    this.setName(null)
        .withTransactionId(null)
        .withSpanName(null)
        .withLocation(null)
        .withContext(null)
        .withSpanKind(null)
        .withErrorMessage(null)
        .withStatsCode(null)
        .withStartTime(null)
        .withEndTime(null)
        .withEventContextId(null)
        .withComponentLocation(null)
        .withSiblings(-1);

    // Clear the embedded tags map
    if (tags != null) {
      tags.clear();
    }
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

  @Override
  public void close() {
    // the default application does nothing
  }
}
