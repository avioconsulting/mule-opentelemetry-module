package com.avioconsulting.mule.opentelemetry.internal.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * In-memory cache implemented using {@link ConcurrentHashMap}.
 * 
 * @param <K>
 * @param <V>
 */
public class InMemoryCache<K, V> implements Cache<K, V> {

  private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCache.class);

  private final ConcurrentHashMap<K, V> store = new ConcurrentHashMap<>();

  @Override
  public V get(K key) {
    Objects.requireNonNull(key, "Key cannot be null");
    return store.get(key);
  }

  @Override
  public V cached(K key, Function<K, V> valueFunction) {
    V value = get(key);
    if (value == null) {
      value = valueFunction.apply(key);
      if (value != null) {
        put(key, value);
      }
      if (LOGGER.isTraceEnabled())
        LOGGER.trace("---> Computed value for key '{}' in [Cache: {}].", key, this);
    } else {
      if (LOGGER.isTraceEnabled())
        LOGGER.trace("===> Found value for key '{}' in [Cache: {}].", key, this);
    }
    return value;
  }

  @Override
  public V cached(K key, V newValue) {
    V value = store.putIfAbsent(key, newValue);
    if (value == null)
      value = store.get(key);
    return value;
  }

  @Override
  public void put(K key, V value) {
    store.put(key, value);
  }
}
