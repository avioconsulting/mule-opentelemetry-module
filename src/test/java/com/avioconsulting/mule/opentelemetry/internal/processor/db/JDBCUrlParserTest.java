package com.avioconsulting.mule.opentelemetry.internal.processor.db;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class JDBCUrlParserTest {
  private static final String POSTGRES_TEST_URL = "jdbc:postgresql://localhost:5432/testdb";
  private static final String MYSQL_TEST_URL = "jdbc:mysql://dbhost:3306/mydb?user=admin&ssl=true";
  private static final String H2_MEM_TEST_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";

  @Before
  public void setUp() {
    JDBCUrlParser.clearCache();
  }

  @Test
  public void shouldParseValidPostgresUrl() {
    DBInfo info = JDBCUrlParser.parse(POSTGRES_TEST_URL);

    assertThat(info)
        .isNotNull()
        .satisfies(i -> {
          assertThat(i.getSystem()).isEqualTo("postgresql");
          assertThat(i.getHost()).isEqualTo("localhost");
          assertThat(i.getPort()).isEqualTo("5432");
          assertThat(i.getDatabase()).isEqualTo("testdb");
          assertThat(i.getInstance()).isNull();
          assertThat(i.getServiceName()).isNull();
        });
  }

  @Test
  public void shouldParseMySQLWithParameters() {
    DBInfo info = JDBCUrlParser.parse("jdbc:mysql://dbhost:3306/mydb?user=admin&ssl=true");

    assertThat(info)
        .isNotNull()
        .satisfies(i -> {
          assertThat(i.getSystem()).isEqualTo("mysql");
          assertThat(i.getParameters())
              .containsEntry("user", "admin")
              .containsEntry("ssl", "true");
        });
  }

  @Test
  public void shouldParseOracleServiceName() {
    DBInfo info = JDBCUrlParser.parse("jdbc:oracle:thin:@//dbhost:1521/ORCL.world");

    assertThat(info)
        .isNotNull()
        .satisfies(i -> {
          assertThat(i.getSystem()).isEqualTo("oracle");
          assertThat(i.getServiceName()).isEqualTo("ORCL.world");
        });
  }

  @Test
  public void shouldParseMSSQLWithInstance() {
    DBInfo info = JDBCUrlParser.parse("jdbc:sqlserver://server\\INSTANCE:1433;databaseName=testdb");

    assertThat(info)
        .isNotNull()
        .satisfies(i -> {
          assertThat(i.getSystem()).isEqualTo("mssql");
          assertThat(i.getInstance()).isEqualTo("INSTANCE");
        });
  }

  @Test
  public void shouldParseH2Memory() {
    DBInfo info = JDBCUrlParser.parse("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");

    assertThat(info)
        .isNotNull()
        .satisfies(i -> {
          assertThat(i.getSystem()).isEqualTo("h2");
          assertThat(i.getDatabase()).isEqualTo("testdb");
        });
  }

  @Test
  public void shouldHandleNullUrl() {
    assertThat(JDBCUrlParser.parse(null)).isNull();
  }

  @Test
  public void shouldHandleInvalidUrl() {
    assertThat(JDBCUrlParser.parse("invalid:url")).isNull();
  }

  @Test
  public void shouldParseQueryParameters() {
    Map<String, String> params = JDBCUrlParser.parseQueryParams("key1=value1&key2=value2");

    assertThat(params)
        .hasSize(2)
        .containsEntry("key1", "value1")
        .containsEntry("key2", "value2");
  }

  @Test
  public void shouldHandleEmptyQueryParameters() {
    assertThat(JDBCUrlParser.parseQueryParams(null))
        .isNotNull()
        .isEmpty();
  }

  @Test
  public void shouldCacheParseResults() {
    String url = "jdbc:postgresql://localhost:5432/testdb";

    DBInfo info1 = JDBCUrlParser.parse(url);
    DBInfo info2 = JDBCUrlParser.parse(url);

    assertThat(info1)
        .isNotNull()
        .isSameAs(info2);
    assertThat(JDBCUrlParser.getCacheSize()).isEqualTo(1);
  }

  @Test
  public void shouldClearCache() {
    JDBCUrlParser.parse("jdbc:mysql://localhost:3306/testdb");
    assertThat(JDBCUrlParser.getCacheSize()).isEqualTo(1);

    JDBCUrlParser.clearCache();
    assertThat(JDBCUrlParser.getCacheSize()).isZero();
  }

  @Test
  public void shouldGenerateCorrectNamespaceForH2() {
    assertThat(JDBCUrlParser.parse("jdbc:h2:mem:testdb"))
        .satisfies(info -> assertThat(info.getNamespace()).isEqualTo("mem:testdb"));

    assertThat(JDBCUrlParser.parse("jdbc:h2:file:/data/sample"))
        .satisfies(info -> assertThat(info.getNamespace()).isEqualTo("file:/data/sample"));
  }

  @Test
  public void shouldGenerateCorrectNamespaceForDerby() {
    assertThat(JDBCUrlParser.parse("jdbc:derby:memory:testdb;create=true"))
        .satisfies(info -> assertThat(info.getNamespace()).isEqualTo("memory:testdb"));

    assertThat(JDBCUrlParser.parse("jdbc:derby://localhost:5421/testdb;create=true"))
        .satisfies(info -> assertThat(info.getNamespace()).isEqualTo("testdb"));

    assertThat(JDBCUrlParser.parse("jdbc:derby:/path/to/database;create=true"))
        .satisfies(info -> assertThat(info.getNamespace()).isEqualTo("file:/path/to/database"));
  }

  @Test
  public void shouldGenerateCorrectNamespaceForAllDatabases() {
    assertThat(JDBCUrlParser.parse("jdbc:postgresql://localhost:5432/testdb"))
        .satisfies(info -> assertThat(info.getNamespace()).isEqualTo("testdb"));

    assertThat(JDBCUrlParser.parse("jdbc:mysql://dbhost:3306/mydb"))
        .satisfies(info -> assertThat(info.getNamespace()).isEqualTo("mydb"));

    assertThat(JDBCUrlParser.parse("jdbc:sqlserver://server\\INSTANCE:1433;databaseName=testdb"))
        .satisfies(info -> assertThat(info.getNamespace()).isEqualTo("server\\INSTANCE/testdb"));

    assertThat(JDBCUrlParser.parse("jdbc:oracle:thin:@//host:1521/SERVICE"))
        .satisfies(info -> assertThat(info.getNamespace()).isEqualTo("host:1521/SERVICE"));

    assertThat(JDBCUrlParser.parse("jdbc:h2:mem:testdb"))
        .satisfies(info -> assertThat(info.getNamespace()).isEqualTo("mem:testdb"));

    assertThat(JDBCUrlParser.parse("jdbc:derby:memory:testdb;create=true"))
        .satisfies(info -> assertThat(info.getNamespace()).isEqualTo("memory:testdb"));

    assertThat(JDBCUrlParser.parse("jdbc:mariadb://localhost:3306/db"))
        .satisfies(info -> assertThat(info.getNamespace()).isEqualTo("db"));

    assertThat(JDBCUrlParser.parse("jdbc:sybase:Tds:host:5000/mydb"))
        .satisfies(info -> assertThat(info.getNamespace()).isEqualTo("mydb"));
  }

  @Test
  public void shouldParseOracleSID() {
    DBInfo info = JDBCUrlParser.parse("jdbc:oracle:thin:@dbhost:1521:ORCL");

    assertThat(info)
        .isNotNull()
        .satisfies(i -> {
          assertThat(i.getSystem()).isEqualTo("oracle");
          assertThat(i.getHost()).isEqualTo("dbhost");
          assertThat(i.getPort()).isEqualTo("1521");
          assertThat(i.getInstance()).isEqualTo("ORCL");
          assertThat(i.getNamespace()).isEqualTo("dbhost:1521/ORCL");
        });
  }

  @Test
  public void shouldParseDB2Url() {
    DBInfo info = JDBCUrlParser.parse("jdbc:db2://localhost:50000/SAMPLE");

    assertThat(info)
        .isNotNull()
        .satisfies(i -> {
          assertThat(i.getSystem()).isEqualTo("db2");
          assertThat(i.getHost()).isEqualTo("localhost");
          assertThat(i.getPort()).isEqualTo("50000");
          assertThat(i.getDatabase()).isEqualTo("SAMPLE");
        });
  }

  @Test
  public void shouldParseSpecialCharactersInParameters() {
    DBInfo info = JDBCUrlParser.parse("jdbc:mysql://localhost/db?user=test@domain&password=p@ss+word");

    assertThat(info.getParameters())
        .containsEntry("user", "test@domain")
        .containsEntry("password", "p@ss+word");
  }

  @Test
  public void shouldHandleMultipleQueryParameters() {
    DBInfo info = JDBCUrlParser.parse(
        "jdbc:postgresql://localhost/db?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory&socketTimeout=10");

    assertThat(info.getParameters())
        .hasSize(3)
        .containsEntry("ssl", "true")
        .containsEntry("sslfactory", "org.postgresql.ssl.NonValidatingFactory")
        .containsEntry("socketTimeout", "10");
  }
}