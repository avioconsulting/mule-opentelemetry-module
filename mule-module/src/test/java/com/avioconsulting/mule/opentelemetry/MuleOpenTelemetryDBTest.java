package com.avioconsulting.mule.opentelemetry;

import com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk.test.DelegatedLoggingSpanTestExporter;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.test.runner.RunnerDelegateTo;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@RunnerDelegateTo(JUnitParamsRunner.class)
public class MuleOpenTelemetryDBTest extends AbstractMuleArtifactTraceTest {

  @Override
  protected String getConfigFile() {
    return "db-flows.xml";
  }

  private static boolean dbInitialized = false;

  @Override
  protected void doSetUpBeforeMuleContextCreation() throws Exception {
    System.setProperty("Database_Config.otel.db.system.fromprop", "derby_Sys");
    super.doSetUpBeforeMuleContextCreation();
  }

  @Before
  public void initDB() throws Exception {
    if (!dbInitialized) {
      runFlow("init-db");
      await().untilAsserted(() -> {
        assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
            .isNotEmpty()
            .anySatisfy(span -> assertThat(span)
                .extracting("spanName", "spanKind")
                .containsOnly("init-db", "INTERNAL"));
      });
      DelegatedLoggingSpanTestExporter.spanQueue.clear();
      dbInitialized = true;
    }
  }

  private void assertDBSpan(DelegatedLoggingSpanTestExporter.Span span, String docName, String statement) {
    assertThat(span)
        .as("Span for db:" + docName)
        .extracting("spanName", "spanKind")
        .containsOnly(docName.toLowerCase() + ":" + docName, "CLIENT");

    assertThat(span.getAttributes())
        .containsEntry("mule.app.processor.configRef", "Database_Config")
        .containsEntry("mule.app.processor.name", docName.toLowerCase())
        .containsEntry("db.query.text", statement)
        .containsEntry("db.system", "derby")
        .containsEntry("mule.app.processor.namespace", "db")
        .containsEntry("db.operation.name", docName.toLowerCase())
        .containsEntry("mule.app.processor.docName", docName)
        .as("System set property").containsEntry("db.system.fromprop", "derby_Sys");
    if (!docName.equalsIgnoreCase("select")) {
      assertThat(span.getAttributes())
          .containsEntry("db.operation.parameter.userId", "500");
    }
  }

  @Test
  @Parameters(method = "CRUD_Parameters")
  public void testValid_DB_CRUD_Tracing(String path, String docName, String statement) throws Exception {
    sendRequest(UUID.randomUUID().toString(), "/test/db/" + path, 200);
    // TODO: This works but Exporter provider is in main package
    // and requires plugin class exporting to make it visible in test.
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .isNotEmpty()
        .anySatisfy(span -> assertDBSpan(span, docName, statement)));
  }

  @Test
  public void selectByValidId() throws Exception {
    sendRequest(UUID.randomUUID().toString(), "/test/db/select-by-id?userId=100", 200);
    // TODO: This works but Exporter provider is in main package
    // and requires plugin class exporting to make it visible in test.
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .isNotEmpty()
        .anySatisfy(span -> assertDBSpan(span, "Select", "select * from testdb.users where userId=:userId"))
        .anySatisfy(span -> {
          assertThat(span.getAttributes())
              .containsEntry("db.operation.parameter.userId", "100");
        }));
  }

  @Test
  @Ignore
  public void testStoredProcedure() throws Exception {
    CoreEvent coreEvent = runFlow("DB-invoke-SP");
    // TODO: This works but Exporter provider is in main package
    // and requires plugin class exporting to make it visible in test.
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .isNotEmpty()
        .anySatisfy(span -> assertDBSpan(span, "Select", "select * from testdb.users where userId=:userId"))
        .anySatisfy(span -> {
          assertThat(span.getAttributes())
              .containsEntry("db.operation.parameter.userId", "100");
        }));
  }

  @Test
  public void selectByNullId() throws Exception {
    sendRequest(UUID.randomUUID().toString(), "/test/db/select-by-id?", 200);
    // TODO: This works but Exporter provider is in main package
    // and requires plugin class exporting to make it visible in test.
    await().untilAsserted(() -> assertThat(DelegatedLoggingSpanTestExporter.spanQueue)
        .isNotEmpty()
        .anySatisfy(span -> assertDBSpan(span, "Select", "select * from testdb.users where userId=:userId"))
        .anySatisfy(span -> {
          assertThat(span.getAttributes())
              .containsEntry("db.operation.parameter.userId", "null");
        }));
  }

  private Object[] CRUD_Parameters() {
    return new Object[] {
        new Object[] { "delete", "Delete", "delete from testdb.users where userId = :userId" },
        new Object[] { "update", "Update",
            "UPDATE testdb.users set username = 'User500' where userId = :userId" },
        new Object[] { "insert", "Insert",
            "INSERT INTO testdb.users (userId, username) values (:userId, 'User5')" },
        new Object[] { "select", "Select", "select * from testdb.users" }
    };
  }
}
