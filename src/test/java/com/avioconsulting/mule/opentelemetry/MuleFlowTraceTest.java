package com.avioconsulting.mule.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.UUID;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mule.tck.junit4.rule.DynamicPort;

@Ignore
public class MuleFlowTraceTest extends AbstractMuleArtifactTraceTest {

  @Rule
  public DynamicPort serverPort = new DynamicPort("http.port");

  @Override
  protected String getConfigFile() {
    return "SimpleFlowTest.xml";
  }

  @Test
  public void flowTest() throws Exception {
    String correlationId = UUID.randomUUID().toString();
    sendRequest(correlationId, "dummyUser", 200);
    // getCapturedEvent(1_200_000, "Fail");
  }

  @Test
  public void exceptionFlowTest() throws Exception {
    String correlationId = UUID.randomUUID().toString();
    sendRequest(correlationId, "exception", 500);
  }

  @Test
  public void error400FlowTest() throws Exception {
    String correlationId = UUID.randomUUID().toString();
    sendRequest(correlationId, "error/400", 400);
  }

  private void sendRequest(String correlationId, String path, int expectedStatus)
      throws IOException {
    HttpGet getRequest = new HttpGet(
        String.format("http://localhost:%s/test/" + path, serverPort.getValue()));
    getRequest.addHeader("X-CORRELATION-ID", correlationId);
    // getRequest.addHeader("traceparent",
    // "00-3e864597bcb2431935133b0dec678ed4-f75931b2493ab2b2-00");
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      try (CloseableHttpResponse response = httpClient.execute(getRequest)) {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(expectedStatus);
      }
    }
  }
}
