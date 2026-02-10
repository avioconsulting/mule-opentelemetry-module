# JMH Test

JMH Annotations needs to be generated for running JMH tests. This generation is hooked into `benchmark` maven profile. Run following command to generate annotations -

```
./mvnw clean process-test-sources -P benchmark

## Run all JMH tests
./mvnw clean -PrunJMH test "-Dtest=com.avioconsulting.mule.opentelemetry.jmh.*JMHTest" -pl mule-module -Pbenchmark
```

JMH Tests do not have any assertions, these were used to manually run and see the performance.

## Previous results

| Benchmark | Mode | Cnt | Score | Error | Units |
| :--- | :--- | :--- | :--- | :--- | :--- |
| InMemoryTransactionStoreTest.getTraceContext | thrpt | 6 | 26907.709 | ± 2006.064 | ops/ms |
| InMemoryTransactionStoreTest.getTraceContextComponent | thrpt | 6 | 12638.620 | ± 451.721 | ops/ms |
| InMemoryTransactionStoreTest.getTransactionContext | thrpt | 6 | 180563.877 | ± 569.346 | ops/ms |

### After removing component span lookup

| Benchmark | Mode | Cnt | Score | Error | Units |
| :--- | :--- | :--- | :--- | :--- | :--- |
| InMemoryTransactionStoreTest.getTraceContext | thrpt | 6 | 47940.917 | ± 896.087 | ops/ms |
| InMemoryTransactionStoreTest.getTraceContextComponent | thrpt | 6 | 27392.840 | ± 430.036 | ops/ms |
| InMemoryTransactionStoreTest.getTransactionContext | thrpt | 6 | 179415.743 | ± 770.880 | ops/ms |

### Further improvements

| Benchmark | Mode | Cnt | Score | Error | Units |
| :--- | :--- | :--- | :--- | :--- | :--- |
| InMemoryTransactionStoreTest.getTraceContext | thrpt | 6 | 50563.152 | ± 793.521 | ops/ms |
| InMemoryTransactionStoreTest.getTraceContextComponent | thrpt | 6 | 13665.884 | ± 528.655 | ops/ms |
| InMemoryTransactionStoreTest.getTransactionComponentContext | thrpt | 6 | 23648.576 | ± 276.160 | ops/ms |
| InMemoryTransactionStoreTest.getTransactionContext | thrpt | 6 | 180368.524 | ± 1052.989 | ops/ms |

### Interceptor Results

| Benchmark | Mode | Cnt | Score | Error | Units |
| :--- | :--- | :--- | :--- | :--- | :--- |
| ProcessorTracingInterceptorTest.interceptBefore | thrpt | 2 | 6163.569 | | ops/ms |

## PeriodicLogger Results (Issue #311)

Run command:
```bash
./mvnw clean -PrunJMH test -Dtest=com.avioconsulting.mule.opentelemetry.jmh.PeriodicLoggerBenchmarkJMHTest -pl mule-module -Pbenchmark
```

### Results

| Benchmark | Mode | Cnt | Score | Error | Units |
| :--- | :--- | :--- | :--- | :--- | :--- |
| PeriodicLoggerBenchmarkJMHTest.concurrentSuppressedCalls | thrpt | 2 | 43.798 | | ops/us |
| PeriodicLoggerBenchmarkJMHTest.directLogCall | thrpt | 2 | 0.152 | | ops/us |
| PeriodicLoggerBenchmarkJMHTest.suppressedLogCall | thrpt | 2 | 43.890 | | ops/us |

### Analysis
*   **Minimal Overhead**: Suppressing a log call takes ~23ns (`43.89 ops/us`). This ensures that even when errors occur, the tracing interceptor adds negligible latency to the Mule flow.
*   **Effective Throttling**: The suppressed path is ~288x faster than a direct log call (`0.15 ops/us`). PeriodicLogger successfully protects the system from the performance penalty of log bursts.
*   **Scale & Concurrency**: Throughput remains consistent under concurrent load (10 threads), verifying the effectiveness of the optimistic lock-free read path in the cache implementation.
