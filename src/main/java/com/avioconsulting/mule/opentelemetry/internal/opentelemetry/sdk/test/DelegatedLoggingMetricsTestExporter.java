package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test;

import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

public class DelegatedLoggingMetricsTestExporter implements MetricExporter {

  private static final LoggingMetricExporter loggingMetricExporter = LoggingMetricExporter.create();
  public static final Queue<MetricTestData> metricQueue = new ConcurrentLinkedQueue<>();
  private final ConfigProperties config;
  private static final Logger LOGGER = Logger.getLogger(DelegatedLoggingMetricsTestExporter.class.getName());

  public DelegatedLoggingMetricsTestExporter(ConfigProperties config) {
    this.config = config;
  }

  @Override
  public CompletableResultCode export(Collection<MetricData> metrics) {
    metrics.stream().map(metricData -> {
      MetricTestData metricTestData = new MetricTestData().setName(metricData.getName())
          .setDescription(metricData.getDescription())
          .setUnit(metricData.getUnit())
          .setType(metricData.getType().name());
      switch (metricData.getType()) {
        case LONG_SUM:
          metricData.getLongSumData().getPoints().forEach((value) -> {
            MetricPointData mpd = new MetricPointData().setValue(value.getValue());
            value.getAttributes().forEach((key, attr) -> mpd.getAttributes().put(key.getKey(), attr));
            metricTestData.getPoints().add(mpd);
          });
          break;
        case HISTOGRAM:
          metricData.getHistogramData().getPoints().forEach((value) -> {
            MetricPointData mpd = new MetricPointData()
                .setValue(!value.getExemplars().isEmpty() ? value.getExemplars().get(0).getValue()
                    : value.getSum());
            value.getAttributes().forEach((key, attr) -> mpd.getAttributes().put(key.getKey(), attr));
            metricTestData.getPoints().add(mpd);
          });
          break;
        default:
      }
      LOGGER.info(metricTestData.toString());
      return metricTestData;
    }).forEach(metricQueue::add);

    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode flush() {
    return loggingMetricExporter.flush();
  }

  @Override
  public CompletableResultCode shutdown() {
    return flush();
  }

  @Override
  public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
    return AggregationTemporality.CUMULATIVE;
  }

  public static class MetricTestData {
    private String name;
    private String description;
    private String unit;
    private String type;
    private final List<MetricPointData> points = new ArrayList<>();

    public String getName() {
      return name;
    }

    public MetricTestData setName(String name) {
      this.name = name;
      return this;
    }

    public String getDescription() {
      return description;
    }

    public MetricTestData setDescription(String description) {
      this.description = description;
      return this;
    }

    public String getUnit() {
      return unit;
    }

    public MetricTestData setUnit(String unit) {
      this.unit = unit;
      return this;
    }

    public String getType() {
      return type;
    }

    public MetricTestData setType(String type) {
      this.type = type;
      return this;
    }

    public List<MetricPointData> getPoints() {
      return points;
    }

    @Override
    public String toString() {
      return "MetricTestData{" +
          "name='" + name + '\'' +
          ", description='" + description + '\'' +
          ", unit='" + unit + '\'' +
          ", type='" + type + '\'' +
          ", points=" + points +
          '}';
    }
  }

  public static class MetricPointData {
    private Map<String, Object> attributes = new HashMap<>();
    private Object value;

    public Map<String, Object> getAttributes() {
      return attributes;
    }

    public MetricPointData setAttributes(Map<String, Object> attributes) {
      this.attributes = attributes;
      return this;
    }

    public Object getValue() {
      return value;
    }

    public MetricPointData setValue(Object value) {
      this.value = value;
      return this;
    }

    @Override
    public String toString() {
      return "MetricPointData{" +
          "attributes=" + attributes +
          ", value=" + value +
          '}';
    }
  }
}
