package com.avioconsulting.mule.opentelemetry.internal.processor.db;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;

/**
 * JDBCUrlParser class provides utility methods to parse and extract information
 * from different types of JDBC database URLs.
 */
public class JDBCUrlParser {
  private static final Pattern POSTGRES_URL = Pattern.compile(
      "jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?]+)(?:\\?(.*))?");
  private static final Pattern MYSQL_URL = Pattern.compile(
      "jdbc:mysql://([^:/]+)(?::(\\d+))?/([^?]+)(?:\\?(.*))?");
  private static final Pattern MSSQL_URL = Pattern.compile(
      "jdbc:sqlserver://([^:/\\\\]+)(?:\\\\([^:]+))?(?::(\\d+))?;(.*)");
  private static final Pattern ORACLE_URL = Pattern.compile(
      "jdbc:oracle:thin:@(?://)?([^:/]+)(?::(\\d+))?(?:/([^:?]+))?(?::([^?]+))?");
  private static final Pattern DB2_URL = Pattern.compile(
      "jdbc:db2://([^:/]+)(?::(\\d+))?/([^?]+)(?:\\?(.*))?");
  private static final Pattern MARIADB_URL = Pattern.compile(
      "jdbc:mariadb://([^:/]+)(?::(\\d+))?/([^?]+)(?:\\?(.*))?");
  private static final Pattern H2_MEM_URL = Pattern.compile(
      "jdbc:h2:mem:([^;]+)(?:;(.*))?");
  private static final Pattern H2_FILE_URL = Pattern.compile(
      "jdbc:h2:file:([^;]+)(?:;(.*))?");
  private static final Pattern DERBY_FILE_URL = Pattern.compile(
      "^jdbc:derby:([^;]+);(.*)$");
  private static final Pattern DERBY_NETWORK_URL = Pattern.compile(
      "^jdbc:derby://([^;/]+)(?::(\\d+))?/([^;]+);(.*)$");
  private static final Pattern DERBY_MEMORY_URL = Pattern.compile(
      "^jdbc:derby:memory:([^;]+);(.*)$");
  private static final Pattern SYBASE_URL = Pattern.compile(
      "jdbc:sybase:Tds:([^:/]+)(?::(\\d+))?/([^?]+)(?:\\?(.*))?");
  private static final ConcurrentMap<String, DBInfo> URL_CACHE = new ConcurrentHashMap<>();
  public static final String POSTGRESQL = "postgresql";
  public static final String MYSQL = "mysql";
  public static final String ORACLE = "oracle";
  public static final String DB_2 = "db2";
  public static final String H2 = "h2";
  public static final String DERBY = "derby";
  public static final String MARIADB = "mariadb";
  public static final String SYBASE = "sybase";
  public static final String MSSQL = "mssql";

  public static DBInfo parse(String jdbcUrl) {
    if (jdbcUrl == null)
      return null;

    return URL_CACHE.computeIfAbsent(jdbcUrl, JDBCUrlParser::parseUrl);
  }

  private static DBInfo parseUrl(String jdbcUrl) {
    if (!isValid(jdbcUrl))
      return null;

    if (jdbcUrl.startsWith("jdbc:postgresql:"))
      return parsePostgres(jdbcUrl);
    if (jdbcUrl.startsWith("jdbc:mysql:"))
      return parseMysql(jdbcUrl);
    if (jdbcUrl.startsWith("jdbc:sqlserver:"))
      return parseMssql(jdbcUrl);
    if (jdbcUrl.startsWith("jdbc:oracle:"))
      return parseOracle(jdbcUrl);
    if (jdbcUrl.startsWith("jdbc:db2:"))
      return parseDb2(jdbcUrl);
    if (jdbcUrl.startsWith("jdbc:mariadb:"))
      return parseMariadb(jdbcUrl);
    if (jdbcUrl.startsWith("jdbc:h2:"))
      return parseH2(jdbcUrl);
    if (jdbcUrl.startsWith("jdbc:derby:"))
      return parseDerby(jdbcUrl);
    if (jdbcUrl.startsWith("jdbc:sybase:"))
      return parseSybase(jdbcUrl);

    return new DBInfo("other_sql", null, null, null, null, null, "", emptyMap());
  }

  private static DBInfo parsePostgres(String url) {
    Matcher m = POSTGRES_URL.matcher(url);
    if (!m.matches())
      return null;

    String host = m.group(1);
    String port = m.group(2);
    String database = m.group(3);

    return new DBInfo(POSTGRESQL,
        host,
        port,
        database,
        null,
        null,
        resolveNamespace(POSTGRESQL, host, port, database, null, null),
        parseQueryParams(m.group(4)));
  }

  private static DBInfo parseMysql(String url) {
    Matcher m = MYSQL_URL.matcher(url);
    if (!m.matches())
      return null;

    String host = m.group(1);
    String port = m.group(2);
    String database = m.group(3);

    return new DBInfo(MYSQL,
        host,
        port,
        database,
        null,
        null,
        resolveNamespace(MYSQL, host, port, database, null, null),
        parseQueryParams(m.group(4)));
  }

  private static DBInfo parseMssql(String url) {
    Matcher m = MSSQL_URL.matcher(url);
    if (!m.matches())
      return null;

    String host = m.group(1);
    String instance = m.group(2);
    String port = m.group(3);
    Map<String, String> params = parseMssqlParams(m.group(4));
    String database = params.get("databaseName");

    return new DBInfo(MSSQL,
        host,
        port,
        database,
        instance,
        null,
        resolveNamespace(MSSQL, host, port, database, instance, null),
        params);
  }

  private static DBInfo parseOracle(String url) {
    Matcher m = ORACLE_URL.matcher(url);
    if (!m.matches())
      return null;

    String host = m.group(1);
    String port = m.group(2);
    String serviceName = m.group(3);
    String instance = m.group(4);

    return new DBInfo(ORACLE,
        host,
        port,
        null,
        instance,
        serviceName,
        resolveNamespace(ORACLE, host, port, null, instance, serviceName),
        emptyMap());
  }

  private static DBInfo parseDb2(String url) {
    Matcher m = DB2_URL.matcher(url);
    if (!m.matches())
      return null;

    String host = m.group(1);
    String port = m.group(2);
    String database = m.group(3);

    return new DBInfo(DB_2,
        host,
        port,
        database,
        null,
        null,
        resolveNamespace(DB_2, host, port, database, null, null),
        parseQueryParams(m.group(4)));
  }

  private static DBInfo parseH2(String url) {
    Matcher memMatcher = H2_MEM_URL.matcher(url);
    Matcher fileMatcher = H2_FILE_URL.matcher(url);

    if (memMatcher.matches()) {
      String database = memMatcher.group(1);
      return new DBInfo(H2,
          null,
          null,
          database,
          null,
          null,
          "mem:" + database,
          parseH2Params(memMatcher.group(2)));
    }

    if (fileMatcher.matches()) {
      String database = fileMatcher.group(1);
      return new DBInfo(H2,
          null,
          null,
          database,
          null,
          null,
          "file:" + database,
          parseH2Params(fileMatcher.group(2)));
    }
    return null;
  }

  private static DBInfo parseDerby(String url) {
    Matcher matcher = DERBY_MEMORY_URL.matcher(url);
    if (matcher.find()) {
      String databaseName = matcher.group(1);
      return new DBInfo(DERBY,
          null,
          null,
          databaseName,
          null,
          null,
          "memory:" + databaseName,
          extractQueryParams(matcher.group(2), ";"));
    }
    matcher = DERBY_NETWORK_URL.matcher(url);
    if (matcher.find()) {

      String host = matcher.group(1);
      String port = matcher.group(2);
      String databaseName = matcher.group(3);

      return new DBInfo(DERBY,
          host,
          port,
          databaseName,
          null,
          null,
          resolveNamespace(DERBY, host, port, databaseName, null, null),
          extractQueryParams(matcher.group(2), ";"));
    }
    matcher = DERBY_FILE_URL.matcher(url);
    if (matcher.find()) {
      String databaseName = matcher.group(1);
      return new DBInfo(DERBY,
          null,
          null,
          databaseName,
          null,
          null,
          "file:" + databaseName,
          extractQueryParams(matcher.group(2), ";"));
    }
    return null;
  }

  private static DBInfo parseMariadb(String url) {
    Matcher m = MARIADB_URL.matcher(url);
    if (!m.matches())
      return null;

    String host = m.group(1);
    String port = m.group(2);
    String database = m.group(3);

    return new DBInfo(MARIADB,
        host,
        port,
        database,
        null,
        null,
        resolveNamespace(MARIADB, host, port, database, null, null),
        parseQueryParams(m.group(4)));
  }

  private static DBInfo parseSybase(String url) {
    Matcher m = SYBASE_URL.matcher(url);
    if (!m.matches())
      return null;

    String host = m.group(1);
    String port = m.group(2);
    String database = m.group(3);

    return new DBInfo(SYBASE,
        host,
        port,
        database,
        null,
        null,
        resolveNamespace(SYBASE, host, port, database, null, null),
        parseQueryParams(m.group(4)));
  }

  static Map<String, String> parseQueryParams(String query) {
    return extractQueryParams(query, "&");
  }

  private static Map<String, String> extractQueryParams(String query, String paramSeparator) {
    if (query == null)
      return emptyMap();
    return Arrays.stream(query.trim().split(paramSeparator)).map(p -> p.split("=")).filter(ps -> ps.length == 2)
        .collect(Collectors.toMap(ps -> ps[0], ps -> ps[1]));
  }

  private static Map<String, String> parseH2Params(String params) {
    return extractQueryParams(params, ";");
  }

  private static Map<String, String> parseMssqlParams(String params) {
    return extractQueryParams(params, ";");
  }

  private static boolean isValid(String url) {
    return url != null && url.startsWith("jdbc:");
  }

  public static void clearCache() {
    URL_CACHE.clear();
  }

  public static int getCacheSize() {
    return URL_CACHE.size();
  }

  public static String resolveNamespace(String system, String host, String port,
      String database, String instance, String serviceName) {
    if (system == null)
      return null;

    switch (system.toLowerCase()) {
      case POSTGRESQL:
      case MYSQL:
      case MARIADB:
      case DB_2:
      case H2:
      case DERBY:
        return database;

      case MSSQL:
        StringBuilder mssqlNs = new StringBuilder();
        if (host != null) {
          mssqlNs.append(host);
          if (instance != null) {
            mssqlNs.append("\\").append(instance);
          }
          if (database != null) {
            mssqlNs.append("/").append(database);
          }
        }
        return mssqlNs.length() > 0 ? mssqlNs.toString() : null;

      case ORACLE:
        StringBuilder oracleNs = new StringBuilder();
        if (host != null) {
          oracleNs.append(host);
          if (port != null) {
            oracleNs.append(":").append(port);
          }
        }
        if (serviceName != null || instance != null) {
          if (oracleNs.length() > 0) {
            oracleNs.append("/");
          }
          if (serviceName != null) {
            oracleNs.append(serviceName);
            if (instance != null) {
              oracleNs.append(":").append(instance);
            }
          } else if (instance != null) {
            oracleNs.append(instance);
          }
        }
        return oracleNs.length() > 0 ? oracleNs.toString() : null;

      default:
        return database;
    }
  }

}