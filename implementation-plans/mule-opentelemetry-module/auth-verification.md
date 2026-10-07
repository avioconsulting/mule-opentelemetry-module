# Exporter authentication verification

## Focused evidence (2026-10-07)

46 tests pass across the new authentication suites and existing exporter/connection/configuration regression suites. Commands use `-Dspotless.check.skip=true` only to bypass the pre-existing untracked SPI stub's formatting violation; changed-file formatting is applied separately.

- `ExporterAuthenticationTest`: opaque credentials, unsafe header rejection, HTTPS URL validation.
- `ExporterAuthenticationXmlTest`: actual Mule DSL loading for all three types, independent headers. All artifact tests use the repository's shared class-loader runner configuration.
- `MuleOAuth2TokenClientTest`: POST and RFC Basic encoding, missing scope, malformed token fields, redirects, oversized response, request timeout, late-stream closure, idempotent close and partial-start cleanup.
- `MuleOAuth2TokenTransportTest`: actual injected Mule HTTP service against a local validating HTTPS token server, both credential placements and redirect refusal. The SDK is disabled in this fixture so token traffic cannot rely on a collector or OAuth provider.
- `OAuth2TokenProviderTest`: short-lifetime refresh, cache reuse, proactive-renewal fallback, cooldown/recovery, concurrent acquisition, bounded waiting and close/renewal race.
- `AuthenticatedExporterTest`: all three signals gate failures before delegation; recovery and synchronous supplier-race isolation; idempotent shutdown without closing shared authentication.
- `OtlpAuthenticationCustomizerTest`: metric selectors/memory mode, effective TLS/header validation, unrelated exporters and system-property overrides disabling all signals without acquisition.
- `OtlpAuthenticationTransportTest`: actual local TLS HTTP/protobuf and HTTP/2 gRPC collectors receive A then B on all signals without exporter reconstruction. Both fixtures require client certificates and use gzip, proving preservation of endpoint, trusted roots, client identity, compression and unrelated headers exactly once. Expired-token outage sends no request; later metric export recovers with C.
- `ConfigurationSanitizerTest`: no dummy header, private-key or URL-secret values in module configuration diagnostics.
- Existing `OpenTelemetryConnectionTest`, `OpenTelemetryExtensionConfigurationTest`, and `OtlpExporterTest` pass.

## Remaining verification notes

The focused evidence is not a claim that every acceptance-matrix permutation has a dedicated automated test. Environment-variable precedence, collector retry timing, all aggregation choices, expired renewal with interrupted waiters, host-name mismatch/untrusted token certificates, streaming-body stalls, and captured end-to-end Mule-flow logs/redeploy still warrant additional coverage. Java 8 source/target is retained; test fixtures use public APIs but the full release gate also requires dependency and full-repository verification below.

Default repository verification, full tests, generated docs and dependency verification results are recorded during AUTH-09. Existing unrelated AsciiDoc include errors and the untracked customizer formatting violation must not be silently repaired or included in feature commits.
