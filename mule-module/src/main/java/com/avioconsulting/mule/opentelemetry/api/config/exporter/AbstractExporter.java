package com.avioconsulting.mule.opentelemetry.api.config.exporter;

import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.Summary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractExporter implements OpenTelemetryExporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractExporter.class);

  @Parameter
  @NullSafe
  @Optional
  @Summary("Additional Configuration properties for Exporter. System or Environment Variables will override this configuration.")
  private Map<String, String> configProperties = new HashMap<>();

  public Map<String, String> getConfigProperties() {
    return configProperties;
  }

  public void setConfigProperties(Map<String, String> configProperties) {
    this.configProperties = configProperties;
  }

  @Override
  public Map<String, String> getExporterProperties() {
    Map<String, String> config = new HashMap<>();
    config.put(OTEL_TRACES_EXPORTER_KEY, "none");
    config.put(OTEL_METRICS_EXPORTER_KEY, "none");
    config.put(OTEL_LOGS_EXPORTER_KEY, "none");
    config.putAll(getConfigProperties());
    return config;
  }

  /**
   * When paths are provided relative to the mule application code, the OTEL SDK
   * resolution fails to find those files.
   * This method leverages classpath to find the absolute path.
   *
   * @param propertyName
   *            {@link String} property to transform form
   * @param path
   *            {@link String} path to transform
   * @return transformed path or same path if exists
   */
  protected String transformToAbsolutePath(String propertyName, String path) {
    if (path != null && !path.isEmpty()) {
      if (Files.exists(Paths.get(path))) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("{} path exists - {}", propertyName, path);
        }
        return path;
      } else {
        String absolutePath = Objects.requireNonNull(this.getClass().getClassLoader().getResource(path),
            path + " not found on the classpath").getPath();
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info("Transforming {} from {} to absolute path {}", propertyName, path, absolutePath);
        }
        return absolutePath;
      }
    }
    return path;
  }
}
