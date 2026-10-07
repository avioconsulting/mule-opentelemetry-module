# Exporter authentication verification

## Focused evidence (2026-10-07)

The initial focused run passed 46 tests across the new authentication suites and existing exporter/connection/configuration regression suites. Additional regression tests cover interrupted waiters, stalled streamed bodies, credential metadata and extension-startup cleanup. Commands use `-Dspotless.check.skip=true` only to bypass the pre-existing untracked SPI stub's formatting violation; changed-file formatting is applied separately.

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

The focused evidence is not a claim that every acceptance-matrix permutation has a dedicated automated test. Environment-variable precedence, collector retry timing, all aggregation choices, host-name mismatch/untrusted token certificates, and captured end-to-end Mule-flow logs/redeploy still warrant additional coverage. Existing SDK precedence and transport settings are retained via standard autoconfiguration and public `toBuilder()` methods; application code contains no internal SDK imports.

## AUTH-09 release checks

- Actual build JDK: Zulu OpenJDK **1.8.0_382**. Compilation, focused tests, local collectors, actual Mule HTTP service and the full reactor build run on Java 8, not just Java 8 source/target settings.
- `./mvnw -o verify`: fails at the existing untracked `CustomAutoConfigurationCustomizer.java` formatting violation. That stub remains byte-for-byte unchanged and uncommitted.
- `./mvnw -o verify -Dspotless.check.skip=true`: final full reactor rerun succeeds after all regression/doc changes, including plugin packaging and generated documentation. Surefire reports total **461 tests**, **0 failures**, **0 errors**, **12 existing skipped tests** across the two batch adapters and Mule module.
- `./mvnw -o -pl mule-module dependency:tree -Dincludes=com.google.code.gson:gson,io.grpc`: Gson resolves to direct compile dependency **2.10.1**; gRPC collector dependencies resolve to **1.65.1**, test scope only. The real Mule-service token test proves parser visibility in the plugin class loader.
- `./mvnw -o -pl mule-module enforcer:enforce -Drules=dependencyConvergence`: fails only for existing `kotlin-stdlib-jdk8` **1.9.10 vs 1.8.21**, inherited through the unchanged OpenTelemetry OkHttp sender. Running the same check against the original module POM produces the same conflict. No unrelated dependency upgrade is applied.
- `jreleaser.yml` generates release notes from conventional commits; feature/fix/test/docs commits follow that existing convention, without creating an unrelated changelog or publishing a release.
- Existing AsciiDoc errors concern the missing `elastic.adoc`, `dynatrace.adoc`, and `splunk.adoc` includes in `apm-tests/apmTesting.adoc`. The authentication configuration page itself is generated successfully.
- Generated `mule-module/target/docs/module-config.html` includes the authentication examples and timeout/renewal guidance; JavaDoc generation succeeds. Captured final verification output contains none of the dummy token/credential markers used by the authentication fixtures.
- Formatting is applied only to changed files using `spotlessFiles`, preserving unrelated working-tree content. `git diff --check` passes.

These baseline limitations and remaining coverage permutations mean the strict completion gate is not represented as an unconditional clean release gate. No remote push, release or deployment is performed.
