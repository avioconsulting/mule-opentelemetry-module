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

The focused evidence is not a claim that every acceptance-matrix permutation has a dedicated automated test. Environment-variable precedence, collector retry timing, all aggregation choices, hostname mismatch, captured end-to-end Mule-flow logs and platform redeploy still warrant additional coverage. Untrusted token certificates and local connection recreation are now covered by the WireMock end-to-end suites below. Existing SDK precedence and transport settings are retained via standard autoconfiguration and public `toBuilder()` methods; application code contains no internal SDK imports.

## WireMock end-to-end coverage

- `oauth2-exporter-e2e.xml` creates the real Mule connection, authentication provider and enabled SDK. Token acquisition uses the injected Mule HTTP service against a dynamic-port WireMock HTTPS endpoint; it is never mocked. The collectors use real mTLS and gzip HTTP/protobuf or gRPC transports.
- Four concrete suites cover both transports and both POST/Basic credential placements: **28 cases** covering initial single acquisition, credential encoding/scope, all-signal A/B rotation with unchanged SDK identity, concurrent signal flushes, proactive outage fallback, expired-token suppression, retry cooldown, business-flow success during outages, C recovery, malformed response, redirect refusal, timeout, idempotent stop, fresh connection/cache recreation and token TLS rejection.
- Collector assertions check independent tenant headers and authorization exactly once. Initial trace payloads are checked for absence of token-endpoint processor spans. Provider diagnostics are checked for dummy credentials. These checks do not replace comprehensive captured-log or thread-leak testing.
- Tests advance a connection-local monotonic clock through a package-private pre-acquisition seam; no token lifetime sleeps, global time hooks or new Mule DSL options. Fixture teardown stops servers, deletes temporary TLS files and restores overridden JVM properties and the JVM default SSL context. The full regression run exposed a default-TLS cache dependency; both real-token fixtures now explicitly install and restore their test context.
- Extracted collector/TLS fixtures retain the existing focused mocked-token transport coverage. `WireMockOAuthServerTest` independently verifies HTTPS fixture rotation and outage responses.
- The enabled-SDK artifact tests exposed missing runtime-transitive classloader visibility. OTLP exporter common modules and OkHttp sender (**1.45.0**) and OkHttp (**4.12.0**) are now explicit compile dependencies at their existing resolved versions; no dependency upgrade was introduced. A fresh filtered `dependency:tree` confirms those versions and scopes.
- Focused command: `./mvnw -o -q -pl mule-module -am test '-Dtest=OAuth2*ExporterEndToEndTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.check.skip=true`.
- Final full reactor command: `./mvnw -o verify -Dspotless.check.skip=true`, on Zulu Java **1.8.0_382**, succeeds with **490 tests, 0 failures, 0 errors, 12 existing skips**. New and existing suites execute together. Evidence: `tmp/oauth-e2e-full-verify.log` and reactor Surefire XML reports. The full log contains none of the E2E dummy client ID, client secret or A/B/C token markers. Changed-file formatting and `git diff --check` pass. The baseline formatting, dependency-convergence and AsciiDoc limitations below still apply.

### Follow-up: exporter property isolation

The initial passing full build still contained attempted metric exports to the SDK default `localhost:4317` endpoint. Passing tests alone did not establish collector isolation.

- OAuth overrides now use `ScopedSystemProperties`, a JUnit rule that restores original values (including absent values) after all inherited teardown methods. The fixture also remembers superclass-modified batch delay, timeout and HTTP host properties before superclass setup.
- `ScopedSystemPropertiesTest` verifies restoration after superclass teardown and startup failure, preserving an existing non-OTLP exporter and removing a previously absent endpoint.
- Surefire explicitly defaults unconfigured metrics/log exporters to `none`, so standard trace-logging fixtures do not fall back to the SDK's OTLP defaults. OAuth tests override these defaults with `otlp` and restore them afterward. Production exporter defaults are unchanged.
- Final follow-up verification: `./mvnw -o verify -Dspotless.check.skip=true` succeeds on Java 8 with **492 tests, 0 failures, 0 errors, 12 existing skips**. `tmp/oauth-property-isolation-verify.log` contains **zero** default-collector `localhost:4317` connection diagnostics or failed metric/log export messages. Changed-file formatting and `git diff --check` pass.

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
