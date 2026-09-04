---
name: generate-mulesoft-observability-release-notes
description: Generate MuleSoft Observability customer release notes. Use when asked to create, regenerate, or update MuleSoft Observability release notes, dashboard or monitor release summaries, or runtime component release details.
metadata:
  owner: avio_consulting
---

# MuleSoft Observability Release Notes

Generate customer-facing release notes for the MuleSoft Observability product suite.

## Example usage
- `Generate MuleSoft Observability release notes`
- `Generate MuleSoft Observability release notes. Include MuleSoft Observability for Datadog integration release notes.`

## Inputs and Output

- **Template:** `assets/RELEASE_NOTES_TEMPLATE.md` relative to this skill directory
- **Default output:** `mulesoft-observability-release-notes-<MM-DD-YYYY>.md` in the current working directory where the skill is invoked. The output file name must include the release date in `MM-DD-YYYY` format. Use the highest release date from the runtime components or Marketplace products to determine the release date. If no release date is found, use the current date.

Include all products and artifacts in the Required Sources table. Never modify Marketplace assets, manifests, changelogs, runtime modules when generating release notes.

If the requested release version, release date, or output path is not clear, derive the version from the latest stable runtime component release and write to the default output path. Ask one concise question only when the requested scope cannot be determined from the sources.

## Required Sources

Use authenticated GitHub CLI for all release sources. The two paid module repositories are private, so use `gh` rather than public unauthenticated HTTP requests.

| Component | Purpose | Release source |
| --- | --- | --- |
| OpenTelemetry Module | Trace instrumentation for Mule applications | `gh release` with `avioconsulting/mule-opentelemetry-module` |
| Metrics Provider | Metrics collection from Mule applications | `gh release` with `avioconsulting/mule-opentelemetry-metrics-avio-provider` |
| Log4j2 Appender | Exports logs through the OpenTelemetry module | `gh release` with `avioconsulting/mule-opentelemetry-log4j2-appender` |
| MuleSoft Observability for Datadog | Datadog Marketplace MuleSoft observability integration | `DataDog/marketplace`, path `avio_consulting_mulesoft_observability/CHANGELOG.md` |
| MuleSoft Anypoint Monitoring for Datadog | Datadog Marketplace Anypoint Monitoring based observability integration | `DataDog/marketplace`, path `avio_consulting_mulesoft_anypoint_monitoring/CHANGELOG.md` |

## Workflow

1. Read the bundled template and determine the release date and output path.
2. Retrieve the latest changelog for both Marketplace products and decide whether to include the entire `Datadog Integration` section.

```sh
gh api -H "Accept: application/vnd.github.raw+json" "repos/DataDog/marketplace/contents/avio_consulting_mulesoft_observability/CHANGELOG.md?ref=master"
gh api -H "Accept: application/vnd.github.raw+json" "repos/DataDog/marketplace/contents/avio_consulting_mulesoft_anypoint_monitoring/CHANGELOG.md?ref=master"
```

3. Include `Datadog Integration` when at least one Marketplace product has a published release dated within the seven calendar days preceding generation. If the user explicitly requests a Datadog integration by name, include that integration's latest release regardless of its publication date. When neither condition applies, omit the entire `Datadog Integration` section, including dashboards, monitor templates, and deprecated assets.
4. When the Datadog section is included, create a separate subsection for each Marketplace integration with a release in the seven-day window or explicitly requested by the user: `MuleSoft Observability for Datadog` and `MuleSoft Anypoint Monitoring for Datadog`. Retrieve each affected integration's `manifest.json` and only the dashboard and monitor JSON assets identified by its release changelog or change set. Use `gh api` with the raw-media header to retrieve files from `DataDog/marketplace`; do not clone the Marketplace repository.
5. For every dashboard named in the release note, use the root-level JSON `title` as the displayed name. Use the root-level `description`, note widgets, and metric queries to write accurate **Features** and **Use cases** bullets.
6. For every monitor named in the release note, use the JSON `definition.name` exactly. Use its query, threshold or anomaly configuration, and message to write accurate **Features** and **Use cases** bullets.
7. List deprecated Marketplace assets in the `Deprecated Assets` table only when the changelog or change set names a replacement. Do not invent replacements.
8. Retrieve runtime component releases using `gh`, including paid private repositories. Ignore draft and prerelease releases when determining the latest stable version.

```sh
gh release list --repo avioconsulting/mule-opentelemetry-module --limit 10 --exclude-drafts --exclude-pre-releases --json tagName,publishedAt,name
gh release list --repo avioconsulting/mule-opentelemetry-metrics-avio-provider --limit 10 --exclude-drafts --exclude-pre-releases --json tagName,publishedAt,name
gh release list --repo avioconsulting/mule-opentelemetry-log4j2-appender --limit 10 --exclude-drafts --exclude-pre-releases --json tagName,publishedAt,name
gh release view <tag> --repo <owner/repository> --json tagName,publishedAt,name,body,url
```

9. Include the latest stable version and its release notes for all three runtime components, even if the component was not released with the Marketplace integration version. Mark the version `**NEW**` only when the latest stable release is within the previous four months.
10. Classify runtime release-note items under **Added**, **Changed**, **Fixed**, **Deprecated**, or **Removed**. Omit categories with no applicable items. Summarize commit-oriented GitHub release text in customer language; retain concrete behavior changes and avoid raw commit hashes unless they are needed for a customer support reference.
11. Fill every applicable template section. Remove all placeholders, instructional text, and `<agent_instructions>` blocks from the generated release note. Omit optional sections that have no release evidence.
12. Preserve the template order:
    1. Release title, date, availability, and summary
    2. Customer Action
    3. Datadog Integration, only when a Datadog release occurred in the prior seven days or the user explicitly requests an integration
    4. Mule Runtime Components
    5. Compatibility and Support

## Writing Rules

- Keep the release summary customer-focused and state whether Mule application configuration changes are required.
- Keep dashboards, monitor templates, and deprecated assets under their owning Marketplace integration subsection. Do not combine MuleSoft Observability and MuleSoft Anypoint Monitoring assets.
- Group dashboards and monitors under their integration's `##### Added`, `##### Changed`, or `##### Deprecated` headings.
- Use `######` headings for individual dashboard titles and monitor names.
- Give each dashboard and monitor exactly one **Features** bullet and one **Use cases** bullet. Make each bullet specific to the asset and actionable for customers.
- Keep runtime artifact coordinates exact:
  - `com.avioconsulting.mule:mule-opentelemetry-module`
  - `com.avioconsulting.mule:mule-opentelemetry-metrics-provider`
  - `com.avioconsulting.mule:mule-opentelemetry-metrics-provider-impl`
  - `com.avioconsulting.mule:mule-opentelemetry-log4j2-appender`
- Never claim a runtime component was released as part of the Marketplace version unless the user provides evidence that it was bundled in that release.
- If `gh` cannot access a private release repository, report the access issue and ask the user for a version or access. Do not guess a module version or release note.

## Validation

Before finishing:

1. Confirm each listed dashboard title matches its retrieved JSON root `title`.
2. Confirm each listed monitor name matches its retrieved JSON `definition.name`.
3. Validate every downloaded dashboard and monitor JSON file with `jq empty <file>`.
4. State the generated file path, the Datadog inclusion decision (seven-day release or explicit request), and validation result in the final response.
