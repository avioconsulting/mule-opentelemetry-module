package com.avioconsulting.mule.opentelemetry.internal.util;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * A memoizer for a {@code BiFunction} to optimize performance by caching the
 * results of function invocations
 * for given arguments. It avoids repeated calculations for the same input by
 * storing previously computed results
 * in a thread-safe {@code ConcurrentHashMap}.
 *
 * @param <K>
 *            the type of the key for caching
 * @param <U>
 *            the type of the input object
 * @param <R>
 *            the type of the result produced by the {@code BiFunction}
 * 
 *            <p>
 *            Example usage:
 *            </p>
 * 
 *            <pre>
 *            {@code
 * BiFunction<String, Integer, String> expensiveOperation = 
 *     (str, num) -> str.repeat(num); // Some expensive operation
 *     
 * BiFunctionMemoizer<String, Integer, String> memoized = 
 *     new BiFunctionMemoizer<>(expensiveOperation);
 *     
 * // First call will compute and cache
 * String result1 = memoized.apply("hello", 3); // Returns "hellohellohello"
 * 
 * // Second call with same parameters will return cached result
 * String result2 = memoized.apply("hello", 3); // Returns cached "hellohellohello"
 * }
 *            </pre>
 */
public class BiFunctionMemoizer<K, U, R> implements BiFunction<K, U, R> {
  private final BiFunction<K, U, R> function;
  private final ConcurrentHashMap<K, R> cache = new ConcurrentHashMap<>();

  /**
   * Constructs a {@code BiFunctionMemoizer} that wraps the given
   * {@code BiFunction}.
   * The provided function cannot be null and is used to perform the computation
   * for inputs that are not already cached.
   * 
   * @param function
   *            the {@code BiFunction} to be memoized, must not be null
   */
  public BiFunctionMemoizer(BiFunction<K, U, R> function) {
    this.function = Objects.requireNonNull(function);
  }

  @Override
  public R apply(K k, U u) {
    return cache.computeIfAbsent(k, key -> function.apply(key, u));
  }

  public static <K, U, R> BiFunctionMemoizer<K, U, R> memoize(BiFunction<K, U, R> function) {
    return new BiFunctionMemoizer<>(function);
  }
}