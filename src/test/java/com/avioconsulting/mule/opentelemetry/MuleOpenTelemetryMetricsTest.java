package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingMetricsTestExporter;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Test;

import static com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingMetricsTestExporter.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

public class MuleOpenTelemetryMetricsTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "mule-opentelemetry-metrics.xml";
  }

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    super.doSetUpBeforeMuleContextCreation();
    System.setProperty("otel.metrics.exporter", "delegatedLogging");
  }

  @After
  public void afterTest() {
    System.clearProperty("otel.metrics.exporter");
  }

  @After
  public void clearMetricsQueue() {
    Awaitility.await().untilAsserted(() -> assertThat(
        DelegatedLoggingMetricsTestExporter.metricQueue).as(
            "Cleaning Metric queue with @Before. If It is expected to be empty, override clearMetricsQueue from abstract test.")
            .isNotEmpty());
    DelegatedLoggingMetricsTestExporter.metricQueue.clear();
  }

  @Test
  public void verifyCustomMetricData() throws Exception {
    sendRequest(CORRELATION_ID, "/test/metric/custom", 200);
    await().untilAsserted(() -> assertThat(metricQueue)
        .isNotEmpty());
    validateMetricValue("org.business.order.count", 1L);
    validateMetricPointAttribute("org.business.order.count", "org.business.order.source.channel",
        "online");
  }

  @Test
  public void invalidCustomMetricAttribute() throws Exception {
    Throwable exception = catchThrowable(() -> runFlow("mule-opentelemetry-app-invalid-attribute"));
    assertThat(exception)
        .isNotNull()
        .hasMessage(
            "Attributes '[org.business.order.source.channel-2]' are not configured for metric 'org.business.order.count' on the configuration 'OpenTelemetry_Config'");
  }

  @Test
  public void verifyMetricData() throws Exception {
    sendRequest(CORRELATION_ID, "/test/propagation/source", 200);
    await().untilAsserted(() -> assertThat(metricQueue)
        .isNotEmpty());

    validateMetricExists("mule.app.message.count",
        "Number of Mule Messages triggered from Flow Invocations in the Mule Application",
        "1", "LONG_SUM");
    validateMetricValue("mule.app.message.count", 1L);
    validateMetricPointAttribute("mule.app.message.count", "mule.app.flow.name",
        "mule-opentelemetry-app-2-context-propagation-source");
    validateSourceMessageMetric();
    validateMetricExists("mule.app.message.duration", "Duration of Mule Messages", "ms", "HISTOGRAM");
    validateMetricExists("mule.app.processor.request.count",
        "Number of requests processed by a processor during message processing in the Mule Application",
        "1", "LONG_SUM");
    validateMetricValue("mule.app.processor.request.count", 1L);
    validateMetricPointAttribute("mule.app.processor.request.count", "mule.app.processor.name", "request");
    validateMetricPointAttribute("mule.app.processor.request.count", "mule.app.processor.namespace", "http");
    verifyProcessorMessageMetricAttributes("mule.app.processor.request.count", "http", "request",
        "SELF_HTTP_Request_configuration");
    validateMetricExists("mule.app.processor.request.duration", "Duration of Processor execution", "ms",
        "HISTOGRAM");

    // Verify a few standard entries exist
    validateStandardMetricExist("runtime.java.cpu_time");
    validateStandardMetricExist("process.runtime.jvm.threads.count");
    validateStandardMetricExist("process.runtime.jvm.buffer.usage");
    validateStandardMetricExist("process.runtime.jvm.classes.loaded");
    validateStandardMetricExist("process.runtime.jvm.system.cpu.utilization");
  }

  private static void validateMetricExists(String metricName, String description, String unit, String type) {
    await().untilAsserted(() -> assertThat(metricQueue)
        .filteredOn(mtd -> metricName.equals(mtd.getName()))
        .anySatisfy(metric -> {
          assertThat(metric)
              .as(description)
              .extracting("name", "description", "unit", "type")
              .containsOnly(metricName, description,
                  unit, type);
        }));
  }

  private static void validateStandardMetricExist(String metricName) {
    await().untilAsserted(() -> assertThat(metricQueue)
        .filteredOn(mtd -> metricName.equals(mtd.getName()))
        .as("Metrics queue for " + metricName)
        .isNotEmpty());
  }

  private static void validateSourceMessageMetric() {
    verifySourceMessageMetricAttributes("mule.app.message.count");
    verifySourceMessageMetricAttributes("mule.app.message.duration");
  }

  private static void verifySourceMessageMetricAttributes(String metricName) {
    await().untilAsserted(() -> assertThat(metricQueue)
        .filteredOn(mtd -> metricName.equals(mtd.getName()))
        .as("Metrics queue for " + metricName)
        .isNotEmpty()
        .filteredOn(mtd -> !mtd.getPoints().isEmpty())
        .element(0)
        .extracting("points")
        .asInstanceOf(InstanceOfAssertFactories.list(MetricPointData.class))
        .filteredOnAssertions(mpd -> assertThat(mpd.getAttributes()).containsEntry("mule.app.flow.name",
            "mule-opentelemetry-app-2-context-propagation-source"))
        .as("Points filtered for source flow name")
        .allSatisfy(mpd -> assertThat(mpd.getAttributes())
            .containsEntry("mule.app.flow.source.namespace", "http")
            .containsEntry("mule.app.flow.source.name", "listener")
            .containsEntry("mule.app.flow.source.configRef", "HTTP_Listener_config")));
  }

  private static void verifyProcessorMessageMetricAttributes(String metricName, String processorNS,
      String processorName, String configRefValue) {
    await().untilAsserted(() -> assertThat(metricQueue)
        .filteredOn(mtd -> metricName.equals(mtd.getName()))
        .as("Metrics queue for " + metricName)
        .isNotEmpty()
        .filteredOn(mtd -> !mtd.getPoints().isEmpty())
        .element(0)
        .extracting("points")
        .asInstanceOf(InstanceOfAssertFactories.list(MetricPointData.class))
        .filteredOnAssertions(mpd -> assertThat(mpd.getAttributes())
            .containsEntry("mule.app.processor.namespace", processorNS)
            .containsEntry("mule.app.processor.name", processorName))
        .as("Points filtered for target processor")
        .allSatisfy(mpd -> assertThat(mpd.getAttributes())
            .containsEntry("mule.app.processor.configRef", configRefValue)));
  }

  private static void validateMetricValue(String metricName, Object value) {
    await().untilAsserted(() -> assertThat(metricQueue)
        .filteredOn(mtd -> metricName.equals(mtd.getName()))
        .as("Metrics queue for " + metricName)
        .isNotEmpty()
        .anySatisfy(metric -> {
          assertThat(metric.getPoints())
              .as("Metric points")
              .isNotEmpty()
              .element(0)
              .extracting("value")
              .as("Metric value")
              .isEqualTo(value);
        }));
  }

  private static void validateMetricPointAttribute(String metricName, String attributeName, String attributeValue) {
    await().untilAsserted(() -> assertThat(metricQueue)
        .as("Metrics queue for " + metricName)
        .filteredOn(mtd -> metricName.equals(mtd.getName()))
        .as("Metric for " + metricName)
        .anySatisfy(metric -> {
          assertThat(metric.getPoints())
              .as("Metric points")
              .isNotEmpty()
              .anySatisfy(mpd -> assertThat(mpd.getAttributes())
                  .containsEntry(attributeName, attributeValue));
        }));
  }

}
