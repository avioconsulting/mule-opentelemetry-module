# Repository guidance

## Build and focused verification

- Use JDK 8 for the baseline build; retain Java 8 source/API compatibility. `OpenTelemetryExtension` declares runtime support for Java 8, 11, and 17.
- Run commands from the repository root with `./mvnw` (pins Maven 3.8.8; `.sdkmanrc` selects a different Maven version).
- EE batch dependencies require Maven credentials for server ID `mulesoft-ee-releases`. See `.github/workflows/github-pages.yml` for the CI settings setup.
- Full local build/install: `./mvnw clean install`. Format and verify: `./mvnw spotless:apply verify`; formatter rules come from `eclipse-formatter.xml` and inherited Spotless configuration.
- Focus a module and its reactor dependencies: `./mvnw -pl mule-module -am test`.
- Focus a test: `./mvnw -pl mule-module -am test '-Dtest=MuleCoreFlowsTest#testRootSpanStatus_OnError' -Dsurefire.failIfNoSpecifiedTests=false`. The last flag allows upstream modules with no matching test; omit `#methodName` for a whole class.
- Benchmarks are opt-in and expensive: `./mvnw clean install -Pbenchmark` runs JMH during `integration-test` with 10 warmup and 10 measurement iterations.

## Module boundaries and wiring

- The root aggregates `api`, `batch-legacy-adapter`, `batch-runtime-adapter`, and `mule-module`; `examples/` is outside the reactor.
- `mule-module` inherits **a separate external parent**, `avio-mule-modules-parent`, not the root POM. Root dependency/plugin changes do not automatically apply there; inspect both POMs and their parents.
- `api/` contains shared instrumentation SPIs. The deployable `mule-extension` and its configuration/exporter APIs live in `mule-module/`; an `api` Java package does not necessarily belong to the `api` Maven module.
- Batch adapters intentionally compile against different EE APIs (legacy 4.3.0, runtime 4.5.0) and register `OtelBatchNotificationListener` via `META-INF/services`. Preserve that separation.
- Under `mule-module/src/main/java/com/avioconsulting/mule/opentelemetry/`, start with `internal/OpenTelemetryExtension.java` for DSL types/exports and `internal/config/OpenTelemetryExtensionConfiguration.java` for lifecycle and notification registration. Tracing uses both `internal/interceptor/` and notification listeners; inspect both for span-lifecycle changes.
- New connector handlers extend `AbstractProcessorComponent` and must be registered in `mule-module/src/main/resources/META-INF/services/com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent`. `ProcessorComponentService` uses the first matching SPI provider and caches it by component identifier. Check `internal/interceptor/InterceptorProcessorConfig.java` and its `intercept-components.txt` resource when interception coverage changes.
- Preserve fail-open tracing behavior: instrumentation failures must not interrupt Mule flow execution.

## Test harness gotchas

- Artifact tests use **JUnit 4** (`org.junit.Test`), despite the root's `junit-jupiter.version` property. Extend `AbstractMuleArtifactTraceTest`; Mule XML fixtures are in `mule-module/src/test/resources/` and selected through `getConfigFile()`.
- The artifact runner isolates classloaders. Test-visible extension classes and shared libraries are explicitly listed in `AbstractMuleArtifactTraceTest`'s `@ArtifactClassLoaderRunnerConfig`; account for this when adding cross-classloader assertions.
- Span export is asynchronous; use the existing Awaitility/`spanQueue` pattern. Base teardown waits for a nonempty span queue, so tests intentionally producing no spans must override `clearSpansQueue()`.
- Surefire disables unconfigured metrics/log exporters to avoid contacting a default collector. The POM also has deliberate classloading workarounds (explicit OkHttp, `xml-apis`, and batch EE in `provided` scope); read their comments before changing scopes or removing dependencies.

## Generated docs and versions

- Edit user docs in `mule-module/src/docs/asciidoc/`. `verify` produces the Pages payload at `mule-module/target/docs/` (not root `target/docs/`).
- Compilation runs `SystemPropertyExtractor`, which **rewrites a source-tree file**, `mule-module/src/docs/asciidoc/system-properties-reference.adoc`. Document `mule.otel.*` constants with Javadoc descriptions and `@default`; change those sources rather than hand-editing the generated reference.
- For an authorized local version change, use `sh update-version.sh <new-version>`; it passes `-DprocessAllModules=true` to cover the independently parented extension. Example apps are outside the reactor and need their dependency versions checked separately. `CONTRIBUTING.adoc` notes that Anypoint Studio caches modules by version.
