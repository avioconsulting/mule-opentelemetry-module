package com.avioconsulting.mule.opentelemetry.internal.util;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.avioconsulting.mule.opentelemetry.internal.util.StringUtil.UNDERSCORE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

public class StringUtilTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(StringUtilTest.class);

  @Test
  public void countParts() {
    String input = "58660cf1-e735-11ee-bd25-ca89f39a1b64_493033029_784814100_894835844_515059234";
    int splitLength = input.split(UNDERSCORE).length;
    int countParts = StringUtil.countParts(input, '_');
    Assertions.assertThat(countParts).isEqualTo(splitLength);
  }

  @Test
  public void loggerTest() {
    LOGGER.info("Test info message");
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Test debug message");
    }
    Map temp = mock(Map.class);
    LOGGER.debug("Test debug with parameters message - {}", getValue(temp));
    Mockito.verify(temp, Mockito.times(1)).get("test");
    reset(temp);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Test debug with parameters message - {}", getValue(temp));
    }
    Mockito.verify(temp, Mockito.times(0)).get("test");
  }

  public String getValue(Map<String, String> temp) {
    LOGGER.info("Message from getValue method");
    return temp.get("test");
  }
}