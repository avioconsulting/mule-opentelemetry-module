package com.avioconsulting.mule.opentelemetry.api.util;

import static java.lang.Long.parseUnsignedLong;
import static java.lang.Long.toUnsignedString;

public class EncodingUtil {
  public static String longFromBase16Hex(String inputHex) {
    long longValue = parseUnsignedLong(inputHex, 16);
    return toUnsignedString(longValue);
  }

  public static String[] traceIdLong(String inputHex) {
    String hiLong = longFromBase16Hex(inputHex.substring(0, 16));
    String lowLong = longFromBase16Hex(inputHex.substring(16));
    return new String[] { hiLong, lowLong };
  }
}
