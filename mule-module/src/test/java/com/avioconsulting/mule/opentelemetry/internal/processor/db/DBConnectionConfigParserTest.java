package com.avioconsulting.mule.opentelemetry.internal.processor.db;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

public class DBConnectionConfigParserTest {

  @Test
  public void shouldParseMySQLConfig() {
    Map<String, String> params = new HashMap<>();
    params.put("host", "localhost");
    params.put("port", "3306");
    params.put("database", "testdb");

    DBInfo info = DBConnectionConfigParser.getDBInfo("mysql", params);

    assertThat(info)
        .isNotNull()
        .satisfies(i -> {
          assertThat(i.getSystem()).isEqualTo("mysql");
          assertThat(i.getHost()).isEqualTo("localhost");
          assertThat(i.getPort()).isEqualTo("3306");
          assertThat(i.getDatabase()).isEqualTo("testdb");
          assertThat(i.getNamespace()).isEqualTo("testdb");
        });
  }

  @Test
  public void shouldParseOracleConfig() {
    Map<String, String> params = new HashMap<>();
    params.put("host", "oracle-server");
    params.put("port", "1521");
    params.put("serviceName", "ORCL");
    params.put("instance", "PROD");

    DBInfo info = DBConnectionConfigParser.getDBInfo("oracle", params);

    assertThat(info)
        .isNotNull()
        .satisfies(i -> {
          assertThat(i.getSystem()).isEqualTo("oracle");
          assertThat(i.getHost()).isEqualTo("oracle-server");
          assertThat(i.getPort()).isEqualTo("1521");
          assertThat(i.getServiceName()).isEqualTo("ORCL");
          assertThat(i.getInstance()).isEqualTo("PROD");
          assertThat(i.getNamespace()).isEqualTo("oracle-server:1521/ORCL:PROD");
        });
  }

  @Test
  public void shouldParseMSSQLConfig() {
    Map<String, String> params = new HashMap<>();
    params.put("host", "sql-server");
    params.put("port", "1433");
    params.put("databaseName", "testdb");
    params.put("instanceName", "MSSQLSERVER");

    DBInfo info = DBConnectionConfigParser.getDBInfo("mssql", params);

    assertThat(info)
        .isNotNull()
        .satisfies(i -> {
          assertThat(i.getSystem()).isEqualTo("mssql");
          assertThat(i.getHost()).isEqualTo("sql-server");
          assertThat(i.getPort()).isEqualTo("1433");
          assertThat(i.getDatabase()).isEqualTo("testdb");
          assertThat(i.getInstance()).isEqualTo("MSSQLSERVER");
          assertThat(i.getNamespace()).isEqualTo("sql-server\\MSSQLSERVER/testdb");
        });
  }

  @Test
  public void shouldHandleNullParameters() {
    Map<String, String> params = new HashMap<>();

    DBInfo info = DBConnectionConfigParser.getDBInfo("mysql", params);

    assertThat(info)
        .isNotNull()
        .satisfies(i -> {
          assertThat(i.getSystem()).isEqualTo("mysql");
          assertThat(i.getHost()).isNull();
          assertThat(i.getPort()).isNull();
          assertThat(i.getDatabase()).isNull();
        });
  }

  @Test
  public void shouldHandleUnknownDatabaseType() {
    Map<String, String> params = new HashMap<>();
    params.put("host", "localhost");

    DBInfo info = DBConnectionConfigParser.getDBInfo("unknown", params);

    assertThat(info)
        .isNotNull()
        .satisfies(i -> {
          assertThat(i.getSystem()).isEqualTo("other_sql");
        });
  }
}