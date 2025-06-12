package com.avioconsulting.mule.opentelemetry.api.traces;

import java.util.Optional;

/**
 * Event context associated with the component.
 *
 * Event Context Id is a time-based unique identifier with a UUID and identity
 * hash appended for nested scopes.
 * <br/>
 * <br/>
 *
 * Example of event context id -
 * `964c53f0-e73d-11ee-a9a1-ca89f39a1b64_2028655840_1690157502_1540340657`
 * where `964c53f0-e73d-11ee-a9a1-ca89f39a1b64` represents a primary id and each
 * value separated by `_` is
 * an identity hash representing a nested child event.
 *
 * @since 2.0
 */
public interface ComponentEventContext {

  String getEventContextId();

  String getLocation();

  /**
   * Gets the primary event context id which is the part before first `_`
   * character.
   * 
   * @return String
   */
  default String getEventContextPrimaryId() {
    return getEventContextId().substring(0, getEventContextId().indexOf("_"));
  }

  /**
   * Number of levels in event context id.
   *
   * For example
   * `58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100_894835844_515059234`
   * will have 5 levels (split by _ ).
   * 
   * @return int
   */
  default int contextNestingLevel() {
    return getEventContextId().split("_").length;
  }

  /**
   * Prefix the given path with event context id
   * 
   * @param path
   *            to prefix
   * @return String
   * @see #getEventContextId()
   */
  default String contextScopedPath(String path) {
    return getEventContextId() + "/" + path;
  }

  /**
   * Prefixes given path with the event context id trimmed by provided levels.
   * <br/>
   * <br/>
   *
   * For example, given a context event id
   * `58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100_894835844_515059234`
   * and path `test-location-path`, if prevLevel = 2, the last two identity has
   * scopes `_894835844_515059234` will be dropped
   * and value
   * `58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100/test-location-path`
   * will be returned.
   *
   * @param path
   *            String to prefix event context id
   * @param prevLevel
   *            int levels to remove
   * @return String
   */
  default String contextCopedPath(String path, int prevLevel) {
    String eventContextId = getEventContextId();
    for (int i = 0; i < prevLevel; i++) {
      eventContextId = eventContextId.substring(0, eventContextId.lastIndexOf("_"));
    }
    return eventContextId + "/" + path;
  }

  default Optional<String> prevContextScopedPath(String path) {
    return Optional.ofNullable(getEventContextId().contains("_")
        ? getEventContextId().substring(0, getEventContextId().lastIndexOf("_")) + "/" + path
        : null);
  }

  /**
   * Get component location prefixed with the event context id. This helps to get
   * a request based unique path to a component location.
   * 
   * @return String
   */
  default String contextScopedLocation() {
    return contextScopedLocationFor(getEventContextId(), getLocation());
  }

  static String contextScopedLocationFor(String eventContextId, String location) {
    return eventContextId + "/" + location;
  }

}
