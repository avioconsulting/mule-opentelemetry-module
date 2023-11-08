




Benchmark                                               Mode  Cnt       Score      Error   Units
InMemoryTransactionStoreTest.getTraceContext           thrpt    6   26907.709 ± 2006.064  ops/ms
InMemoryTransactionStoreTest.getTraceContextComponent  thrpt    6   12638.620 ±  451.721  ops/ms
InMemoryTransactionStoreTest.getTransactionContext     thrpt    6  180563.877 ±  569.346  ops/ms


After removing componen span lookup
Benchmark                                               Mode  Cnt       Score     Error   Units
InMemoryTransactionStoreTest.getTraceContext           thrpt    6   47940.917 ± 896.087  ops/ms
InMemoryTransactionStoreTest.getTraceContextComponent  thrpt    6   27392.840 ± 430.036  ops/ms
InMemoryTransactionStoreTest.getTransactionContext     thrpt    6  179415.743 ± 770.880  ops/ms

Benchmark                                                     Mode  Cnt       Score      Error   Units
InMemoryTransactionStoreTest.getTraceContext                 thrpt    6   50563.152 ±  793.521  ops/ms
InMemoryTransactionStoreTest.getTraceContextComponent        thrpt    6   13665.884 ±  528.655  ops/ms
InMemoryTransactionStoreTest.getTransactionComponentContext  thrpt    6   23648.576 ±  276.160  ops/ms
InMemoryTransactionStoreTest.getTransactionContext           thrpt    6  180368.524 ± 1052.989  ops/ms




Benchmark                                         Mode  Cnt   Score   Error   Units
ProcessorTracingInterceptorTest.interceptBefore  thrpt    2  22.137          ops/ms

// cached get()
Benchmark                                         Mode  Cnt   Score   Error   Units
ProcessorTracingInterceptorTest.interceptBefore  thrpt    2  22.263          ops/ms

// no removal
Benchmark                                         Mode  Cnt   Score   Error   Units
ProcessorTracingInterceptorTest.interceptBefore  thrpt    2  65.034          ops/ms