# Mule OpenTelemetry Extension

## OpenTelemetry

### What and Why?

### OpenTelemetry Configuration
Extension uses OpenTelemetry's autoconfigured SDK. In this mode, SDK will configure itself based on the environment variables.
Supported environment variable details can be seen on [open-telemetry/opentelemetry-java](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure).

### Exporters
Extension contains all dependencies needed to send traces to an OpenTelemetry Collector endpoint i.e. when `otel.traces.exporter` is set to `otlp`. Note that `otlp` is the default exporter if the variable is not set.

For all other exporters, application must add additional required dependencies. See [sdk-extensions/autoconfigure#exporters](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure#exporters) for details.

### Propagators


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

## Context Propagation

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



## TODO
- Extension Features
  - [x] Mule SDK Based OpenTelemetry Connection Management
  - [ ] Configuration
    - [ ] Allow configuring OpenTelemetry Collector endpoint in configuration. System variables should override this configuration.
    - [x] Allow disabling the interceptor processing if needed. This will result in loosing context injection in flow variables.
  - [ ] Operations
    - [x] Add an operation to retrieve current trace context. SDK does not allow adding variables. Users may have to use `targetVariable` feature.
    - [ ] If possible, add a DW function to retrieve trace context as a Map. Users can add this map to any existing outbound headers.
  - [ ] Scopes
    - [ ] Add a custom scope container to execute components in a span.
