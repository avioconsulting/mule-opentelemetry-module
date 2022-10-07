package com.avioconsulting.mule.opentelemetry.internal.cache;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InMemoryCacheTest {

  @Test
  public void getNonExisting() {
    InMemoryCache<String, String> cache = new InMemoryCache<>();
    assertThat(cache.get("test")).isNull();
  }

  @Test
  public void getExisting() {
    InMemoryCache<String, String> cache = new InMemoryCache<>();
    cache.put("test", "value");
    assertThat(cache.get("test")).isEqualTo("value");
  }

  @Test
  public void cacheTheFunctionValue() {
    InMemoryCache<String, String> cache = new InMemoryCache<>();
    String value = cache.cached("test", (key) -> "computed");
    assertThat(cache.get("test")).isEqualTo(value);
  }

  @Test
  public void cacheTheStringValue() {
    InMemoryCache<String, String> cache = new InMemoryCache<>();
    String value = cache.cached("test", "computed");
    assertThat(cache.get("test")).isEqualTo(value);
  }

  @Test
  public void getCachedValue() {
    InMemoryCache<String, String> cache = new InMemoryCache<>();
    String value = cache.cached("test", (key) -> "computed-1");
    assertThat(cache.get("test")).isEqualTo(value);
    String value2 = cache.cached("test", (key) -> "computed-2");
    assertThat(value2).as("Value fetched from cache").isEqualTo(value);
  }

  @Test
  public void put() {
    InMemoryCache<String, String> cache = new InMemoryCache<>();
    cache.put("test", "value");
    assertThat(cache.get("test")).isEqualTo("value");
  }
}