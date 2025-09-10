package com.avioconsulting.mule.opentelemetry.api.traces;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An interface defining a contract for managing key-value tags in an object.
 * The interface is generic, allowing flexibility in defining the types of keys
 * and values.
 *
 * @param <K>
 *            the type for the key
 * @param <V>
 *            the type for the value
 */
public interface Taggable<K, V> {

  /**
   * Adds or updates a tag with the specified key and value.
   * If a tag with the same key already exists, it will be replaced with the new
   * value.
   *
   * @param key
   *            the key for the tag
   * @param value
   *            the value for the tag
   */
  void addTag(K key, V value);

  /**
   * Retrieves the value associated with the specified key.
   *
   * @param key
   *            the key whose associated value is to be returned
   * @return the value associated with the specified key, or null if the key is
   *         not found
   */
  V getTag(K key);

  /**
   * Adds all key-value pairs from the source map to this taggable object.
   * If any keys already exist, their values will be replaced.
   *
   * @param source
   *            the map containing tags to be added
   */
  void addAllTags(Map<K, V> source);

  /**
   * Removes the tag with the specified key.
   *
   * @param key
   *            the key of the tag to be removed
   * @return the previous value associated with the key, or null if there was no
   *         mapping for the key
   */
  V removeTag(K key);

  /**
   * Copies all tags from this taggable object to the specified target map.
   *
   * @param target
   *            the map to which tags will be copied
   */
  void copyTagsTo(Map<K, V> target);

  /**
   * Copies all tags from this taggable object to another taggable object.
   *
   * @param target
   *            the taggable object to which tags will be copied
   */
  void copyTagsTo(Taggable<K, V> target);

  /**
   * Copies tags from this taggable object to another taggable object, but only
   * for keys
   * that satisfy the provided predicate filter.
   *
   * @param target
   *            the taggable object to which tags will be copied
   * @param keyFilter
   *            a predicate that determines which keys should be copied
   */
  void copyTagsTo(Taggable<String, String> target, Predicate<String> keyFilter);

  /**
   * Checks if this taggable object contains a tag with the specified key.
   *
   * @param key
   *            the key to check for
   * @return true if a tag with the specified key exists, false otherwise
   */
  boolean hasTagFor(K key);

  /**
   * Checks if this taggable object contains any tags.
   *
   * @return true if this object contains at least one tag, false otherwise
   */
  boolean hasTags();

  /**
   * Checks if this taggable object contains a tag with the specified key and
   * value.
   *
   * @param key
   *            the key to check for
   * @param value
   *            the value to check for
   * @return true if a tag with the specified key and value exists, false
   *         otherwise
   */
  boolean containsTag(K key, V value);

  /**
   * Performs the given action for each tag entry in this taggable object.
   * The behavior is unspecified if the taggable object is modified during
   * iteration.
   *
   * @param entry
   *            the action to be performed for each tag entry
   */
  void forEachTagEntry(Consumer<Map.Entry<K, V>> entry);
}
