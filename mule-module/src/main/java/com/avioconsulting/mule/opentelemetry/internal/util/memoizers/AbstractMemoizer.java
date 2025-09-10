package com.avioconsulting.mule.opentelemetry.internal.util.memoizers;

import java.util.concurrent.ConcurrentHashMap;

public class AbstractMemoizer<T, R> {

  protected final boolean supportNullValues;
  protected final Object NULL_VALUE = new Object();
  protected final ConcurrentHashMap<T, R> cache = new ConcurrentHashMap<>();

  public AbstractMemoizer(boolean supportNullValues) {
    this.supportNullValues = supportNullValues;
  }

  public AbstractMemoizer() {
    this.supportNullValues = false;
  }

  public void clear() {
    cache.clear();
  }

  public int size() {
    return cache.size();
  }

  public R remove(T key) {
    R removed = cache.remove(key);
    return removed == NULL_VALUE ? null : removed;
  }

}
