# Mule OpenTelemetry Extension

## OpenTelemetry

### What and Why?

## Configurations
Extension uses OpenTelemetry's autoconfigured SDK. In this mode, SDK will configure itself based on the environment variables.
Supported environment variable details can be seen on [open-telemetry/opentelemetry-java](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure).

### Exporters
Extension contains all dependencies needed to send traces to an OpenTelemetry Collector endpoint i.e. when `otel.traces.exporter` is set to `otlp`. Note that `otlp` is the default exporter if the variable is not set.

For all other exporters, application must add additional required dependencies. See [sdk-extensions/autoconfigure#exporters](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure#exporters) for details.

### Propagators

### Extension Configuration
Extension allows to configure some resource and exporter attributes at individual application level. This configuration is minimal required to successfully send traces to OpenTelemetry collector.

Following example shows an OpenTelementery Config with OTLP Exporter configured -

```xml
  <opentelemetry:config name="OpenTelemetry_Config" doc:name="OpenTelemetry Config" serviceName="api-app-1">
    <opentelemetry:exporter >
      <opentelemetry:otlp-exporter collectorEndpoint="http://localhost:55681/v1" protocol="HTTP_PROTOBUF" >
         <opentelemetry:headers >
          <opentelemetry:header key="testHeader" value="testHeaderValue" />
        </opentelemetry:headers>
      </opentelemetry:otlp-exporter>
    </opentelemetry:exporter>
    <opentelemetry:resource-attributes >
      <opentelemetry:attribute key="mule.env" value="Dev" />
    </opentelemetry:resource-attributes>
  </opentelemetry:config>
```

## Context Propagation

### Context Extraction
Extension supports extracting Open Telemetry Trace context extraction from inbound HTTP Requests.
Based on configured propagator (see above), extension will set attach to any parent span if exists.

### Context Injection

#### Auto Injection to Flow Variables
Extension uses a processor interceptor.
OpenTelemetry's tracing context will automatically added a flow variable before the first processor is invoked.
It is injected under key **OTEL_TRACE_CONTEXT** always.

**NOTE:** In case interception needs to be disabled, set the system property **"mule.otel.interceptor.processor.enable"** to **"false"**.

![auto-context-flow-injection.png](./docs/images/auto-context-flow-injection.png)

#### Manual Injection
If needed, `<opentelemetry:get-trace-context />` operation can be used to manually inject trace context into flow.

**NOTE:** `target` must be used to set operation output to a flow variable.
```xml
<opentelemetry:get-trace-context doc:name="Get Trace Context" config-ref="OpenTelemetry_Config" target="traceContext"/>
```
![manual-context-flow-injection.png](./docs/images/manual-context-flow-injection.png)

### HTTP Request Context Injection
Extension does **NOT** support automatic context propagation. But using the context injection described above, this can be manually be achieved.

When using default W3C Trace Context Propagators, you can add trace headers in default headers section of HTTP Requester configuration.
This will ensure sending context headers for each outbound request.

```xml
    <http:request-config name="HTTP_Request_configuration_App2" doc:name="HTTP Request configuration" doc:id="23878620-099a-4c33-8a3a-31cdc4f912d1">
    <http:request-connection host="localhost" port="8082" />
    <http:default-headers >
      <http:default-header key="traceparent" value="#[(vars.OTEL_TRACE_CONTEXT.traceparent as String) default '']" />
    </http:default-headers>
  </http:request-config>
```

As described above in context extraction, if the target endpoint is another mule app with this extension configured, it will be able to extract this context on the listener and attach its own span to it.

## Testing

### Local Collector with Zipkin

`src/test/docker` contains two files:
- docker-compose.yml: This config file configures two services -
- OpenTelemetry Collector: [Collector](https://opentelemetry.io/docs/collector/getting-started/#docker) service to receive traces.
- OpenZipkin: [Zipkin](https://zipkin.io/) as a tracing backend.
- otel-local-config.yml: Collector configuration file. Collector service uses this and forwards traces to zipkin.

### Local configuration
Following environment variables must be set to send traces to OpenTelemetry collector -

```properties
otel.traces.exporter=otlp
otel.exporter.otlp.endpoint=http://localhost:55681/v1
otel.exporter.otlp.traces.endpoint=http://localhost:55681/v1/traces
otel.exporter.otlp.protocol=http/protobuf
otel.metrics.exporter=none
otel.resource.attributes=deployment.environment=dev,service.name=test-api
```

-Dotel.traces.exporter=otlp
-Dotel.metrics.exporter=none
-Dotel.exporter.otlp.endpoint=http://localhost:55681/v1
-Dotel.exporter.otlp.traces.endpoint=http://localhost:55681/v1/traces
-Dotel.exporter.otlp.protocol=http/protobuf -Dotel.resource.attributes=deployment.environment=dev


## TODO
- Extension Features
  - OpenTelemetry SDK
    - [ ] Create Mule Environment [Resource](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure#resource-provider-spi)
  - [x] Mule SDK Based OpenTelemetry Connection Management
  - Configuration
    - [x] Allow setting service name on configuration
    - [x] Allow configuring OpenTelemetry Collector endpoint in configuration. System variables should override this configuration.
    - [x] Allow disabling the interceptor processing if needed. This will result in loosing context injection in flow variables.
  - Operations
    - [x] Add an operation to retrieve current trace context. SDK does not allow adding variables. Users may have to use `targetVariable` feature.
    - [ ] If possible, add a DW function to retrieve trace context as a Map. Users can add this map to any existing outbound headers.
  - Scopes
    - [ ] Add a custom scope container to execute components in a span.
