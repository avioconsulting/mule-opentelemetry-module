# Mule OpenTelemetry Extension

## OpenTelemetry

### What and Why?

### OpenTelemetry Configuration
Extension uses OpenTelemetry's autoconfigured SDK. In this mode, SDK will configure itself based on the environment variables.
Supported environment variable details can be seen on [open-telemetry/opentelemetry-java](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure).

### Exporters
Extension contains all dependencies needed to send traces to an OpenTelemetry Collector endpoint i.e. when `otel.traces.exporter` is set to `otlp`. Note that `otlp` is the default exporter if the variable is not set.  

For all other exporters, application must add additional required dependencies. See [sdk-extensions/autoconfigure#exporters](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure#exporters) for details.


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
