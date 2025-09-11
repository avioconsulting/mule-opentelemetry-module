package com.avioconsulting.mule.opentelemetry.tools;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool to extract all system properties from the codebase and generate
 * documentation
 * for integration into module-config.adoc
 */
public class SystemPropertyExtractor {

  private static final Map<String, PropertyInfo> systemProperties = new LinkedHashMap<>();

  private static class PropertyInfo {
    String name;
    String defaultValue;
    String description;
    String javadocDescription;
    String javadocDefaultValue;
    String location;
    String environmentVariable;

    PropertyInfo(String name, String location) {
      this.name = name;
      this.location = location;
      this.environmentVariable = toEnvName(name);
    }

    private String toEnvName(String propertyName) {
      return propertyName
          .toUpperCase(Locale.ROOT)
          .replace('.', '_')
          .replace('-', '_');
    }

    public String getEffectiveDescription() {
      return javadocDescription != null && !javadocDescription.isEmpty() ? javadocDescription : description;
    }

    public String getEffectiveDefaultValue() {
      return javadocDefaultValue != null && !javadocDefaultValue.isEmpty() ? javadocDefaultValue : defaultValue;
    }
  }

  public static void main(String[] args) {
    try {
      String baseDir = args[0];
      System.out.println("Extracting system properties from project at: " + baseDir + " ...");
      Path projectRoot = Paths.get(baseDir, "src/main/java");
      extractSystemProperties(projectRoot);
      generateDocumentation(baseDir);
    } catch (IOException e) {
      System.err.println("Error extracting system properties: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private static void extractSystemProperties(Path projectRoot) throws IOException {
    // Clear any existing properties to start fresh
    systemProperties.clear();

    // Walk through Java files to find additional properties
    Files.walk(projectRoot)
        .filter(path -> path.toString().endsWith(".java"))
        .forEach(SystemPropertyExtractor::extractFromFile);
  }

  private static void addProperty(String name, String defaultValue, String description, String location) {
    PropertyInfo prop = new PropertyInfo(name, location);
    prop.defaultValue = defaultValue;
    prop.description = description;
    systemProperties.put(name, prop);
  }

  private static void extractFromFile(Path filePath) {
    try {
      if (filePath.endsWith("SystemPropertyExtractor.java"))
        return;

      String content = new String(Files.readAllBytes(filePath));

      // Pattern to find property constants with their Javadoc
      // This pattern looks for: /** javadoc */ followed by private or public static
      // final
      // String
      // CONSTANT_NAME = "property.name";
      // Uses pattern that prevents matching across nested /** patterns
      Pattern propertyWithJavadocPattern = Pattern.compile(
          "/\\*\\*([^*]*(?:\\*(?!/)[^*]*)*)\\*/\\s*(private|public)\\s+static\\s+final\\s+String\\s+\\w+\\s*=\\s*\"([a-z][a-z0-9._-]+)\"",
          Pattern.MULTILINE);

      Matcher javadocMatcher = propertyWithJavadocPattern.matcher(content);

      while (javadocMatcher.find()) {
        String javadoc = javadocMatcher.group(1);
        String potentialProperty = javadocMatcher.group(3);

        if (potentialProperty.startsWith("mule.otel.")) {
          // Extract @default value before cleaning
          String defaultValue = extractDefaultValue(javadoc);
          // Clean up the Javadoc text
          String cleanJavadoc = cleanJavadoc(javadoc);

          if (!systemProperties.containsKey(potentialProperty)) {
            PropertyInfo prop = new PropertyInfo(potentialProperty, filePath.toString());
            prop.description = "Property found in codebase";
            prop.javadocDescription = cleanJavadoc;
            prop.javadocDefaultValue = defaultValue;
            systemProperties.put(potentialProperty, prop);
          } else {
            // Update existing property with Javadoc if we have it
            PropertyInfo existingProp = systemProperties.get(potentialProperty);
            if (existingProp.javadocDescription == null || existingProp.javadocDescription.isEmpty()) {
              existingProp.javadocDescription = cleanJavadoc;
            }
            if (existingProp.javadocDefaultValue == null || existingProp.javadocDefaultValue.isEmpty()) {
              existingProp.javadocDefaultValue = defaultValue;
            }
          }
        }
      }

      // Also look for string constants that look like property names (fallback)
      Pattern propertyPattern = Pattern.compile("\"([a-z][a-z0-9._-]+)\"");
      Matcher matcher = propertyPattern.matcher(content);

      while (matcher.find()) {
        String potentialProperty = matcher.group(1);
        if (potentialProperty.startsWith("mule.otel.")) {
          if (!systemProperties.containsKey(potentialProperty)) {
            addProperty(potentialProperty, "", "Property found in codebase", filePath.toString());
          }
        }
      }
    } catch (IOException e) {
      System.err.println("Error reading file " + filePath + ": " + e.getMessage());
    }
  }

  private static String extractDefaultValue(String javadoc) {
    if (javadoc == null)
      return "";

    // Pattern to find @default or @Default followed by a value
    Pattern defaultPattern = Pattern.compile("@[Dd]efault\\s+([^\\s\\*]+)", Pattern.CASE_INSENSITIVE);
    Matcher matcher = defaultPattern.matcher(javadoc);

    if (matcher.find()) {
      return matcher.group(1).trim();
    }

    return "";
  }

  private static String cleanJavadoc(String javadoc) {
    if (javadoc == null)
      return "";

    return javadoc
        .replaceAll("@[Dd]efault\\s+[^\\s\\*]+", "") // Remove @default annotations
        .replaceAll("\\*", "") // Remove asterisks
        .replaceAll("\\n\\s*", " ") // Replace newlines with spaces
        .replaceAll("\\s+", " ") // Normalize multiple spaces
        .trim();
  }

  private static void generateDocumentation(String baseDir) {
    try {
      Path outputPath = Paths.get(baseDir, "src/docs/asciidoc/system-properties-reference.adoc");
      Files.createDirectories(outputPath.getParent());

      try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath))) {
        writer.println("=== System Properties Reference");
        writer.println();
        writer.println(
            "The following table lists all system properties supported by the Mule OpenTelemetry Module:");
        writer.println();
        writer.println(
            "NOTE: *Environment variables take precedence over system properties*. System properties take precedence over configuration values.");
        writer.println();
        writer.println(".System Properties");
        writer.println("|===");
        writer.println("|System Property |Environment Variable |Description |Default Value");
        writer.println();

        for (PropertyInfo prop : systemProperties.values()) {
          // Only output properties that start with "mule.otel"
          if (prop.name.startsWith("mule.otel.")) {
            writer.printf("|%s%n", prop.name);
            writer.printf("|%s%n", prop.environmentVariable);
            writer.printf("|%s%n", prop.getEffectiveDescription());
            String effectiveDefault = prop.getEffectiveDefaultValue();
            writer.printf("|%s%n", (effectiveDefault == null || effectiveDefault.isEmpty()) ? "Not set"
                : "`" + effectiveDefault + "`");
            writer.println();
          }
        }

        writer.println("|===");
        writer.println();

        System.out.println("Generated system properties documentation at: " + outputPath.toAbsolutePath());
        System.out.println("Found " + systemProperties.size() + " system properties");
      }
    } catch (IOException e) {
      System.err.println("Error writing documentation: " + e.getMessage());
      e.printStackTrace();
    }
  }
}