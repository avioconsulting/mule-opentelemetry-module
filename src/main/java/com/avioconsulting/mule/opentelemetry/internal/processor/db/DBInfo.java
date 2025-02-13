package com.avioconsulting.mule.opentelemetry.internal.processor.db;

import java.util.Map;

public class DBInfo {
  private final String system;
  private final String host;
  private final String port;
  private final String database;
  private final String instance;
  private final String serviceName;
  private final String namespace;
  private final Map<String, String> parameters;

  public DBInfo(String system, String host, String port, String database,
      String instance, String serviceName, String namespace,
      Map<String, String> parameters) {
    this.system = system;
    this.host = host;
    this.port = port;
    this.database = database;
    this.instance = instance;
    this.serviceName = serviceName;
    this.namespace = namespace;
    this.parameters = parameters;
  }

  public String getSystem() {
    return system;
  }

  public String getHost() {
    return host;
  }

  public String getPort() {
    return port;
  }

  public String getDatabase() {
    return database;
  }

  public String getInstance() {
    return instance;
  }

  public String getServiceName() {
    return serviceName;
  }

  public String getNamespace() {
    return namespace;
  }

  public Map<String, String> getParameters() {
    return parameters;
  }

}