package com.avioconsulting.mule.opentelemetry.internal.util.memoizers;

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
 * @param <T>
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
public class BiFunctionMemoizer<K, T, R> extends AbstractMemoizer<K, R> implements BiFunction<K, T, R> {
  private final BiFunction<K, T, R> function;

  /**
   * Constructs a {@code BiFunctionMemoizer} that wraps the given
   * {@code BiFunction}.
   * The provided function cannot be null and is used to perform the computation
   * for inputs that are not already cached.
   * 
   * @param function
   *            the {@code BiFunction} to be memoized, must not be null
   */
  public BiFunctionMemoizer(BiFunction<K, T, R> function) {
    this.function = Objects.requireNonNull(function);
  }

  @Override
  public R apply(K k, T t) {
    R cached = cache.computeIfAbsent(k, key -> {
      R computed = function.apply(key, t);
      if (computed == null && supportNullValues) {
        return (R) NULL_VALUE;
      }
      return computed;
    });
    return cached == NULL_VALUE ? null : cached;
  }

  public static <K, T, R> BiFunctionMemoizer<K, T, R> memoize(BiFunction<K, T, R> function) {
    return new BiFunctionMemoizer<>(function);
  }
}