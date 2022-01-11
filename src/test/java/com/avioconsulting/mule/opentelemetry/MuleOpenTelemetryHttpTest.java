package com.avioconsulting.mule.opentelemetry;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Rule;
import org.junit.Test;
import org.mule.tck.junit4.rule.DynamicPort;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class MuleOpenTelemetryHttpTest extends AbstractMuleArtifactTraceTest {

    @Rule
    public DynamicPort serverPort = new DynamicPort("http.port");

    @Override
    protected String getConfigFile() {
        return "mule-opentelemetry-http.xml";
    }

    private void sendRequest(String correlationId, String path, int expectedStatus) throws IOException {
        HttpGet getRequest = new HttpGet(String.format("http://localhost:%s/" + path, serverPort.getValue()));
        getRequest.addHeader("X-CORRELATION-ID", correlationId);
//        getRequest.addHeader("traceparent", "00-3e864597bcb2431935133b0dec678ed4-f75931b2493ab2b2-00");
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try (CloseableHttpResponse response = httpClient.execute(getRequest)) {
                assertThat(response.getStatusLine().getStatusCode()).isEqualTo(expectedStatus);
            }
        }
    }

    @Test
    public void testHttpTracing() throws Exception {
        sendRequest(UUID.randomUUID().toString(), "test", 200);
    }
}
