# MuleSoft Observability `<version>` Release Notes

**Release date:** `<Month DD, YYYY>`

**Availability:** `<Datadog Marketplace / Maven repository / both>`

`<Summarize the release scope, customer value, and whether deployed Mule applications require configuration changes.>`

## Customer Action

`<agent_instructions>`

Describe the actions customers should take such as:
- adoption of new assets
- migration from deprecated assets
- upgrade to the latest version

Customer need not manually install any Datadog assets. Datadog auto-installs those in a customer's Datadog instance if the customer is already subscribed to released marketplace integration. DO NOT include actions like install dashboards or install the Datadog integration.

`</agent_instructions>`

`<agent_instructions>`

Include the entire Datadog Integration section only when MuleSoft Observability for Datadog or MuleSoft Anypoint Monitoring for Datadog has a release in the seven calendar days before generation. If the user explicitly requests either integration, include that integration's latest release regardless of date. Otherwise, remove this section and all of its subsections.

`</agent_instructions>`

## Datadog Integration

### MuleSoft Observability for Datadog

`<Omit this subsection when MuleSoft Observability for Datadog has no release in the seven-day window and the user did not explicitly request it.>`

#### Datadog Dashboards

`<Summarize the MuleSoft Observability dashboard assets included, changed, or deprecated in this release.>`

##### Added | Changed | Deprecated

`<Use the appropriate heading for the dashboards under this section. Repeat this section for each dashboard group change.>`

###### `<Dashboard title>`

- **Features:** `<Describe the telemetry, visualizations, and operational capabilities provided.>`
- **Use cases:** `<Describe when customers should use this dashboard and which teams benefit from it.>`

`<Repeat the dashboard section above for each included or materially changed dashboard.>`

#### Datadog Monitor Templates

`<Summarize the MuleSoft Observability monitor templates included, changed, or deprecated in this release.>`

##### Added | Changed | Deprecated

`<Use the appropriate heading for the monitors under this section. Repeat this section for each monitor group change.>`

###### `<Monitor template name>`

- **Features:** `<Describe the monitored signal, detection method, and alert behavior.>`
- **Use cases:** `<Describe when customers should enable this monitor and the operational scenario it addresses.>`

`<Repeat the monitor section above for each included or materially changed monitor template.>`

#### Deprecated Assets

`<Omit this section when no MuleSoft Observability assets are deprecated.>`

| Asset Type | Deprecated Asset | Replacement |
| --- | --- | --- |
| `<Dashboard / Monitor Template / Log Pipeline etc>` | `<Asset title/Name>` | `<Replacement Asset title/Name>` |

### MuleSoft Anypoint Monitoring for Datadog

`<Omit this subsection when MuleSoft Anypoint Monitoring for Datadog has no release in the seven-day window and the user did not explicitly request it.>`

#### Datadog Dashboards

`<Summarize the MuleSoft Anypoint Monitoring dashboard assets included, changed, or deprecated in this release.>`

##### Added | Changed | Deprecated

`<Use the appropriate heading for the dashboards under this section. Repeat this section for each dashboard group change.>`

###### `<Dashboard title>`

- **Features:** `<Describe the telemetry, visualizations, and operational capabilities provided.>`
- **Use cases:** `<Describe when customers should use this dashboard and which teams benefit from it.>`

#### Datadog Monitor Templates

`<Summarize the MuleSoft Anypoint Monitoring monitor templates included, changed, or deprecated in this release.>`

##### Added | Changed | Deprecated

`<Use the appropriate heading for the monitors under this section. Repeat this section for each monitor group change.>`

###### `<Monitor template name>`

- **Features:** `<Describe the monitored signal, detection method, and alert behavior.>`
- **Use cases:** `<Describe when customers should enable this monitor and the operational scenario it addresses.>`

#### Deprecated Assets

`<Omit this section when no MuleSoft Anypoint Monitoring assets are deprecated.>`

| Asset Type | Deprecated Asset | Replacement |
| --- | --- | --- |
| `<Dashboard / Monitor Template / Log Pipeline etc>` | `<Asset title/Name>` | `<Replacement Asset title/Name>` |

## Mule Runtime Components

The following are the core Mule Modules supporting the MuleSoft Observability. The following section describes the changes in the latest stable release of each module.

### OpenTelemetry Module

- **Artifact:** `com.avioconsulting.mule:mule-opentelemetry-module`
- **Latest stable version:** `<version>` (released `<Month DD, YYYY>`)
- **Release notes:**
  `<Added, changed, fixed, or deprecated item from the latest stable release, Include those in the following format:>`
  - **Added**:
  - **Fixed**:
  - **Deprecated**:
  - **Removed**:
  

### Metrics Provider

- **Artifacts:** `com.avioconsulting.mule:mule-opentelemetry-metrics-provider` and `com.avioconsulting.mule:mule-opentelemetry-metrics-provider-impl`
- **Latest stable version:** `<version>` (released `<Month DD, YYYY>`)
- **Release notes:**
  `<Added, changed, fixed, or deprecated item from the latest stable release, Include those in the following format:>`
  - **Added**:
  - **Fixed**:
  - **Deprecated**:
  - **Removed**:

### Log4j2 Appender

- **Artifact:** `com.avioconsulting.mule:mule-opentelemetry-log4j2-appender`
- **Latest stable version:** `<version>` (released `<Month DD, YYYY>`)
- **Release notes:**
  `<Added, changed, fixed, or deprecated item from the latest stable release, Include those in the following format:>`
  - **Added**:
  - **Fixed**:
  - **Deprecated**:
  - **Removed**:

## Compatibility and Support

- **Mule runtime compatibility:** Mule Runtime 4.3+
- **Support:** `datadog-support@avioconsulting.com`
