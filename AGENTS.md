# Repository guidance

## Build and focused verification

- From the root, use JDK 8 and `./mvnw` (Maven 3.8.8, not `.sdkmanrc`'s Maven 3.9.4). Keep Java 8 API compatibility even though the extension also declares Java 11 and 17 runtime support.
- Resolving uncached EE batch dependencies requires Maven settings credentials under server ID `mulesoft-ee-releases`; see `.github/workflows/github-pages.yml`.
- Format before verifying: `./mvnw spotless:apply verify`. The extension uses the root `eclipse-formatter.xml` plus inherited Spotless settings.
- Focus a module and its reactor dependencies: `./mvnw -pl mule-module -am test`.
- Focus a test: `./mvnw -pl mule-module -am test '-Dtest=MuleCoreFlowsTest#testRootSpanStatus_OnError' -Dsurefire.failIfNoSpecifiedTests=false`. The last flag allows upstream modules with no matching test; omit `#methodName` for a whole class.
- Keep `-Pbenchmark` out of routine verification: it runs JMH at `integration-test` with 10 warmup and 10 measurement iterations.

## Module boundaries and wiring

- The root aggregates `mule-module` but does not parent it: the deployable extension inherits `avio-mule-modules-parent`, so root dependency/plugin settings don't carry over. The extension owns configuration/exporter APIs; the `api/` Maven module owns the shared batch SPI.
- Keep the two batch adapters separate: they compile against EE batch 4.3.0 (legacy) and 4.5.0 (runtime), and supply `OtelBatchNotificationListener` via `META-INF/services`. Extension artifact tests load the legacy adapter; test the runtime adapter separately with `./mvnw -pl batch-runtime-adapter -am test`.
- In the extension, start at `OpenTelemetryExtension` for the DSL, `OpenTelemetryExtensionConfiguration` for lifecycle/listeners, and `OpenTelemetryConnection` for SDK setup. For span lifecycle changes, check both notification listeners and the interceptor factories wired by `registry-bootstrap.properties`. Keep per-event tracing fail-open.
- For connector coverage, extend `AbstractProcessorComponent` and register it in `mule-module/src/main/resources/META-INF/services/com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent`. `ProcessorComponentService` caches the first matching provider per component identifier; also check `InterceptorProcessorConfig` and `intercept-components.txt`.

## Test harness gotchas

- Artifact tests are JUnit 4, not Jupiter. `AbstractMuleArtifactTraceTest` loads XML from `mule-module/src/test/resources/` via `getConfigFile()`; its `@ArtifactClassLoaderRunnerConfig` controls which extension classes and libraries the test can share.
- Use Awaitility with the delegated test exporter's `spanQueue`; export is asynchronous. Override `clearSpansQueue()` for intentional no-span tests because the default teardown waits for a span.
- Normal artifact trace tests use the in-process exporter, not the Docker collector. Surefire sets unconfigured metrics/log exporters to `none`. Keep the explicit OkHttp, test `xml-apis`, and `provided` EE batch dependencies unless the classloading issues documented in `mule-module/pom.xml` are addressed.

## Generated docs and versions

- `verify` produces the Pages payload in `mule-module/target/docs/`. Even `test` runs the compile-phase `SystemPropertyExtractor` and rewrites `mule-module/src/docs/asciidoc/system-properties-reference.adoc`; update the `mule.otel.*` constants' Javadoc descriptions and `@default`, not the generated page.
- For version bumps use `sh update-version.sh <new-version>` (`-DprocessAllModules=true` reaches the separately parented extension). Update `examples/` dependencies separately; Studio caches extension artifacts by version.
