To install and configure an otel-contrib collector you can choose between the following: 

## System Package Installation

### Debian Systems
Use following instructions (as described in the  
  https://opentelemetry.io/docs/collector/installation/#deb-installation)

```shell
sudo apt-get update
sudo apt-get -y install wget
wget https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v0.119.0/otelcol-contrib_0.119.0_linux_amd64.deb
sudo dpkg -i otelcol_0.119.0_linux_amd64.deb
```
### Red Hat Systems
Use following instructions (as described in the  
https://opentelemetry.io/docs/collector/installation/#rpm-installation)
```shell
sudo yum update
sudo yum -y install wget systemctl
wget https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v0.119.0/otelcol-contrib_0.119.0_linux_amd64.rpm
sudo rpm -ivh otelcol_0.119.0_linux_amd64.rpm
```

### Configure Collector

To setup the connectivity with Datadog, create a Datadog APY Key here - https://app.datadoghq.com/organization-settings/api-keys

Add following two variables at then end in `/etc/otelcol-contrib/otelcol-contrib.conf` file -

`sudo vi /etc/otelcol-contrib/otelcol-contrib.conf`

```
DD_API_KEY=<YOUR_DATADOG_API_KEY>
DD_SITE=datadoghq.com
```

OpenTelemetry Collector's OTLP Receiver endpoint can be configured with or without any bearer token-base authentication. It is recommended to use the authentication. 

Following sections describe both approaches -

#### Using Bearer Token Auth for OTLP Endpoints

1. Modify `otel-collector-config-dd.yml` at line 5 to replace `SOME_SECRET_VALUE` with any complex string.
2. Copy this file to otel collector - 
```shell
mv /etc/otelcol-contrib/config.yaml /etc/otelcol-contrib/config.yaml.bak

cp ./otel-collector-config-dd.yml /etc/otelcol-contrib/config.yaml
```
3. Restart the service using `sudo systemctl start otelcol-contrib`

#### Without Bearer Token Auth for OTLP Endpoints
1. Copy this file to otel collector - 
```shell
mv /etc/otelcol-contrib/config.yaml /etc/otelcol-contrib/config.yaml.bak

cp ./otel-collector-config-dd-noauth.yml /etc/otelcol-contrib/config.yaml
```
2. Restart the service using `sudo systemctl start otelcol-contrib`


## Manual Download and Execute Binary Package
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

This repo contains a sample `span.json` file for testing a trace posting to the collector.

If using bearer token auth for collector - 
```shell
curl -i http://localhost:4318/v1/traces -X POST -H "Content-Type: application/json" -H "Authorization: Bearer <YOUR_BEARER_TOLEN_OF_COLLECOTR>" -d @span.json
```

If NOT using bearer token auth for collector - 
```shell
curl -i http://localhost:4318/v1/traces -X POST -H "Content-Type: application/json" -d @span.json
```

If everything works, you should see a response of `{"partialSuccess":{}}` and Datadog should have a test trace. 