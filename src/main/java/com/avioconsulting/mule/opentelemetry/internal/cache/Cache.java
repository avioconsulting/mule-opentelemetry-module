package com.avioconsulting.mule.opentelemetry.internal.cache;

import java.util.function.Function;

public interface Cache<K, V> {

  /**
   * Get an entry from cache.
   *
   * @param key
   *            {@link String} and non-null.
   * @return V
   */
  V get(K key);

  /**
   * Gets value for provided key. If value doesn't exist or is null, a new value
   * should be computed using provided function.
   *
   * @param key
   *            K
   * @param valueFunction
   *            {@link Function} to calculated value
   * @return V
   */
  V cached(K key, Function<K, V> valueFunction);

  V cached(K key, V newValue);

  /**
   * Add a key-value pair to the cache.
   *
   * @param key
   *            K
   * @param value
   *            V
   */
  void put(K key, V value);
}
