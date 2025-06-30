package com.avioconsulting.mule.opentelemetry.internal.util;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A generic utility class that memoizes the results of a function, i.e., it
 * caches
 * the output of a function for a given input. If the function is called again
 * with
 * the same input, the cached result is returned instead of recomputing the
 * value.
 *
 * @param <T>
 *            the type of the input to the function
 * @param <R>
 *            the type of the result of the function
 */
public class FunctionMemoizer<T, R> implements Function<T, R> {
  private final Function<T, R> function;
  private final ConcurrentHashMap<T, R> cache = new ConcurrentHashMap<>();

  public FunctionMemoizer(Function<T, R> function) {
    this.function = Objects.requireNonNull(function);
  }

  @Override
  public R apply(T input) {
    return cache.computeIfAbsent(input, function);
  }

  public void clear() {
    cache.clear();
  }

  public int size() {
    return cache.size();
  }

  /**
   * Creates a memoized version of the provided function.
   * The memoized function caches results for previously used inputs,
   * which can improve performance when the same inputs are used repeatedly.
   *
   * @param function
   *            The function to memoize
   * @param <T>
   *            The type of the input to the function
   * @param <R>
   *            The type of the result of the function
   * @return A memoized version of the function
   */
  public static <T, R> FunctionMemoizer<T, R> memoize(Function<T, R> function) {
    return new FunctionMemoizer<>(function);
  }
}
