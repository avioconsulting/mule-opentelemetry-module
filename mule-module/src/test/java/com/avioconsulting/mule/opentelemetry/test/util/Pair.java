package com.avioconsulting.mule.opentelemetry.test.util;

import java.util.Objects;

/**
 * A simple pair of data
 * 
 * @param <L>
 *            Left value type
 * @param <R>
 *            Right value type
 */
public class Pair<L, R> {
  private final L left;
  private final R right;

  private Pair(L left, R right) {
    this.left = left;
    this.right = right;
  }

  public static <L, R> Pair<L, R> of(L left, R right) {
    return new Pair<>(left, right);
  }

  public L getLeft() {
    return left;
  }

  public R getRight() {
    return right;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    Pair<?, ?> pair = (Pair<?, ?>) o;
    return Objects.equals(getLeft(), pair.getLeft()) && Objects.equals(getRight(), pair.getRight());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getLeft(), getRight());
  }

  @Override
  public String toString() {
    return "Pair{" +
        "left=" + left +
        ", right=" + right +
        '}';
  }
}
