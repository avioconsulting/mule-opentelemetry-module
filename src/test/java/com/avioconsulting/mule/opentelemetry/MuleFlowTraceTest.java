package com.avioconsulting.mule.opentelemetry;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Rule;
import org.junit.Test;
import org.mule.functional.junit4.MuleArtifactFunctionalTestCase;
import org.mule.runtime.core.api.util.IOUtils;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.test.runner.ArtifactClassLoaderRunnerConfig;

import java.io.IOException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@ArtifactClassLoaderRunnerConfig(applicationSharedRuntimeLibs = {
        "com.avioconsulting:open-telemetry-mule4-agent",
        "io.opentelemetry:opentelemetry-api",
        "io.opentelemetry:opentelemetry-sdk-extension-autoconfigure",
        "io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi",
        "io.opentelemetry:opentelemetry-sdk",
        "io.opentelemetry:opentelemetry-semconv",
        "io.opentelemetry:opentelemetry-sdk-common",
        "io.opentelemetry:opentelemetry-context",
        "io.opentelemetry:opentelemetry-sdk-metrics",
        "io.opentelemetry:opentelemetry-api-metrics",
        "io.opentelemetry:opentelemetry-sdk-trace",
})
public class MuleFlowTraceTest extends MuleArtifactFunctionalTestCase {

    @Rule
    public DynamicPort serverPort = new DynamicPort("http.port");

    @Override
    protected String getConfigFile() {
        return "SimpleFlowTest.xml";
    }

    @Test
    public void flowTest() throws Exception {
        sendRequest("Req-1");
        sendRequest("Req-2");
    }

    private void sendRequest(String requestNum) throws IOException {
        HttpGet getRequest = new HttpGet(String.format("http://localhost:%s/test", serverPort.getValue()));
        getRequest.addHeader("Request-Num", requestNum);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try (CloseableHttpResponse response = httpClient.execute(getRequest)) {
                assertThat(IOUtils.toString(response.getEntity().getContent()), is("Done"));
            }
        }
    }
}