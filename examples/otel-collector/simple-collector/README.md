To install and configure an otel-contrib collector you can choose between the following: 

## System Package Installation
- Follow the instructions on the OpenTelemetry side to install the DEB or RPM package.
https://opentelemetry.io/docs/collector/installation/#deb-installation
- Modify otel config file to at /etc/otelcol/config.yml
- Start the service using `sudo systemctl start otelcol`

## Download and Execute Binary Package
- Use the download.sh script provided to download and unpack the binary release. (This uses curl and tar)
Example to download 0.119.0 release: ```./download.sh 0.119.0```
- Optionally use any other client to download the release from a URL in the format (https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v0.119.0/otelcol_0.119.0_linux_amd64.tar.gz) and extract the binary.
- Copy the provided otel-collector-config-dd.yml to the directory where the collector binary was extracted
- If you wish to enable token authentication uncommment the bearertokenauth section at the top of the config
- Set the required environment variables:
```export DD_SITE=[datadoghq.com,us3.datadoghq.com,etc..]```  
```export DD_API_KEY=YOU_DD_API_KEY```
- Run the collector: ```./otelcol-contrib --config=./otel-collector-config-dd.yml```

## Testing the Collector
Install otel-cli - https://github.com/equinix-labs/otel-cli?tab=readme-ov-file#getting-started
Export following vaiables to the terminal -
```
OTEL_EXPORTER_OTLP_ENDPOINT=localhost:4317
OTEL_EXPORTER_OTLP_HEADERS="Authorization=Bearer <Token>"
```


Execute to generate a test span -

```
`otel-cli exec --service my-service --name "curl google" curl https://google.com`
```