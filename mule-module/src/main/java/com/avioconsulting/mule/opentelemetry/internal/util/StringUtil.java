package com.avioconsulting.mule.opentelemetry.internal.util;

public class StringUtil {
  public static final String EMPTY_STRING = "";
  public static final Character UNDERSCORE_CHAR = '_';
  public static final String UNDERSCORE = UNDERSCORE_CHAR.toString();

  /**
   * Count the number of parts in a given input string when split by the provided
   * separator
   * 
   * @param separator
   *            character to split string input
   * @return Number of parts in a given input
   */
  public static int countParts(String input, Character separator) {
    if (input.isEmpty())
      return 0;

    int count = 1; // Start with 1 for first part
    for (int i = 0; i < input.length(); i++) {
      if (input.charAt(i) == separator)
        count++;
    }
    return count;
  }
}
