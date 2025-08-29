package com.avioconsulting.mule.opentelemetry.internal.util;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import static com.avioconsulting.mule.opentelemetry.internal.util.StringUtil.UNDERSCORE;

public class StringUtilTest {

  @Test
  public void countParts() {
    String input = "58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100_894835844_515059234";
    int splitLength = input.split(UNDERSCORE).length;
    int countParts = StringUtil.countParts(input, '_');
    Assertions.assertThat(countParts).isEqualTo(splitLength);
  }
}