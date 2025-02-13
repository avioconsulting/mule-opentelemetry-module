package com.avioconsulting.mule.opentelemetry.internal.processor.db;

import java.util.Map;

import static java.util.Collections.emptyMap;

public class DBConnectionConfigParser {

  /**
   * Retrieves the DBInfo object based on the connection parameters provided.
   *
   * These are the connections parameters as defined on the Database config.
   * 
   * <pre>
   *
   *   <db:config name="Database_Config2" doc:name="Database Config">
   * 		<db:mssql-connection host="a" instanceName="a" port="a" user=
  "a" password="a" databaseName="a" />
   * 	</db:config>
   *
   * </pre>
   * 
   * @param connectionName
   *            the name of the database connection (e.g., "mysql", "oracle",
   *            "mssql", "derby")
   * @param connectionParams
   *            a map containing the connection parameters such as host, port,
   *            database, instance, serviceName, etc.
   *            The keys expected in the map are "host", "port", "database",
   *            "instance", "serviceName", "instanceName",
   *            "databaseName", and "subsubProtocol" (for "derby" connection
   *            type).
   * @return a DBInfo object representing the database connection information
   *         obtained from the provided parameters.
   */
  public static DBInfo getDBInfo(String connectionName, Map<String, String> connectionParams) {
    String host = connectionParams.get("host");
    String port = connectionParams.get("port");
    String database = connectionParams.get("database");
    String instance = connectionParams.get("instance");
    String serviceName = connectionParams.get("serviceName");
    switch (connectionName) {
      case "mysql":
        return new DBInfo("mysql", host, port,
            database, instance,
            serviceName,
            JDBCUrlParser.resolveNamespace("mysql", host, port, database, instance, serviceName),
            connectionParams);
      case "oracle":
        return new DBInfo("oracle", host, port,
            database, instance,
            serviceName,
            JDBCUrlParser.resolveNamespace("oracle", host, port, database, instance, serviceName),
            connectionParams);
      case "mssql":
        instance = connectionParams.get("instanceName");
        database = connectionParams.get("databaseName");
        return new DBInfo("mssql", host, port,
            database, instance,
            serviceName,
            JDBCUrlParser.resolveNamespace("mssql", host, port, database, instance, serviceName),
            connectionParams);
      case "derby":
        String subsubProtocol = connectionParams.getOrDefault("subsubProtocol", "");
        return new DBInfo("mssql", host, port,
            database, instance,
            serviceName, subsubProtocol + ":" + database,
            connectionParams);
      default:
        return new DBInfo("other_sql", null, null, null, null, null, "", emptyMap());
    }
  }
}
