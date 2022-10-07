package com.avioconsulting.mule.opentelemetry.internal.processor;

import io.opentelemetry.api.trace.SpanKind;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;

import java.util.*;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.*;

public class DBProcessorComponent extends AbstractProcessorComponent {

  public static final String NAMESPACE = "db";

  @Override
  public boolean canHandle(ComponentIdentifier componentIdentifier) {
    return getNamespace().equalsIgnoreCase(componentIdentifier.getNamespace())
        && getOperations().contains(componentIdentifier.getName().toLowerCase());
  }

  @Override
  protected String getNamespace() {
    return DBProcessorComponent.NAMESPACE;
  }

  @Override
  protected List<String> getOperations() {
    return Arrays.asList("select", "update", "insert", "delete", "bulk-update", "bulk-insert", "bulk-delete",
        "stored-procedure");
  }

  @Override
  protected List<String> getSources() {
    return Collections.emptyList();
  }

  @Override
  protected SpanKind getSpanKind() {
    return SpanKind.CLIENT;
  }

  protected Map<String, String> componentAttributesToTags(Component component) {
    Map<String, String> tags = super.componentAttributesToTags(component);
    ComponentWrapper componentWrapper = new ComponentWrapper(component, configurationComponentLocator);
    Map<String, String> connectionParams = componentWrapper.getConfigConnectionParameters();
    String connectionComponentName = connectionParams.get(ComponentWrapper.COMPONENT_NAME_KEY);
    String connectionName = connectionComponentName.split("-")[0];
    if ("generic".equalsIgnoreCase(connectionName)) {
      connectionName = "other_sql";
    }
    tags.put(DB_SYSTEM.getKey(), connectionName);
    addTagIfPresent(connectionParams, "databaseName", tags, DB_NAME.getKey());
    addTagIfPresent(connectionParams, "database", tags, DB_NAME.getKey());
    addTagIfPresent(connectionParams, "host", tags, NET_PEER_NAME.getKey());
    addTagIfPresent(connectionParams, "port", tags, NET_PEER_PORT.getKey());
    addTagIfPresent(connectionParams, "user", tags, DB_USER.getKey());
    addTagIfPresent(connectionParams, "instanceName", tags, DB_MSSQL_INSTANCE_NAME.getKey());
    addTagIfPresent(connectionParams, "instance", tags, "db.oracle.instance");
    addTagIfPresent(connectionParams, "serviceName", tags, "db.oracle.serviceName");
    tags.put(DB_JDBC_DRIVER_CLASSNAME.getKey(), connectionParams.get("driverClassName"));
    tags.put(DB_STATEMENT.getKey(), componentWrapper.getParameter("sql"));
    tags.put(DB_OPERATION.getKey(), component.getIdentifier().getName());
    tags.put(DB_USER.getKey(), connectionParams.get("user"));
    return tags;
  }

}
