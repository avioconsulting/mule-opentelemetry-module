package com.avioconsulting.mule.opentelemetry.api.util;

import static java.lang.Long.parseUnsignedLong;
import static java.lang.Long.toUnsignedString;

public class EncodingUtil {

  // avoids all intermediate allocations and is specifically optimized for the
  // fixed-length hex strings that OTEL uses.
  public static long spanIdHexToLong(String hex) {
    long result = 0;
    for (int i = 0; i < 16; i++) {
      result = (result << 4) | digitValue(hex.charAt(i));
    }
    return result;
  }

  // avoids all intermediate allocations and is specifically optimized for the
  // fixed-length hex strings that OTEL uses.
  // Convert 32-char hex traceId to two longs
  public static long traceIdHexToLowLong(String hex) {
    long low = 0;

    for (int i = 16; i < 32; i++) {
      low = (low << 4) | digitValue(hex.charAt(i));
    }
    return low;
  }

  // avoids all intermediate allocations and is specifically optimized for the
  // fixed-length hex strings that OTEL uses.
  // Convert 32-char hex traceId to two longs
  public static void traceIdHexToLongs(String hex, long[] output) {
    long high = 0, low = 0;

    for (int i = 0; i < 16; i++) {
      high = (high << 4) | digitValue(hex.charAt(i));
    }
    for (int i = 16; i < 32; i++) {
      low = (low << 4) | digitValue(hex.charAt(i));
    }

    output[0] = high;
    output[1] = low;
  }

  // Fast inline hex digit conversion
  private static int digitValue(char c) {
    if (c >= '0' && c <= '9')
      return c - '0';
    if (c >= 'a' && c <= 'f')
      return c - 'a' + 10;
    if (c >= 'A' && c <= 'F')
      return c - 'A' + 10;
    return -1; // Or throw exception
  }

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
