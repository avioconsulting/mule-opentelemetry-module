package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes;
import io.opentelemetry.api.trace.SpanKind;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.metadata.TypedValue;

import java.util.*;

import static io.opentelemetry.semconv.SemanticAttributes.*;

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

  @Override
  protected <A> Map<String, String> getAttributes(Component component, TypedValue<A> attributes) {
    ComponentWrapper componentWrapper = new ComponentWrapper(component, configurationComponentLocator);
    Map<String, String> connectionParams = componentWrapper.getConfigConnectionParameters();

    Map<String, String> tags = new HashMap<>();

    String connectionComponentName = connectionParams.get(ComponentWrapper.COMPONENT_NAME_KEY);
    String connectionName = "other_sql";
    // See DB Recommended system names {@link
    // io.opentelemetry.semconv.trace.attributes.DbSystemValues}
    // Connection name can be null. See Issue #65 and Issue #66.
    if (connectionComponentName != null) {
      connectionName = connectionComponentName.substring(0, connectionComponentName.lastIndexOf("-"));
      if ("generic".equalsIgnoreCase(connectionName) ||
          "data-source".equalsIgnoreCase(connectionName)) {
        connectionName = "other_sql";
      } else {
        if (connectionName.contains("-")) {
          connectionName = connectionName.replace("-", "");
        }
      }
    }

    tags.put(DB_SYSTEM.getKey(), connectionName);

    addTagIfPresent(connectionParams, "host", tags, NET_PEER_NAME.getKey());
    addTagIfPresent(connectionParams, "port", tags, NET_PEER_PORT.getKey());
    addTagIfPresent(connectionParams, "user", tags, DB_USER.getKey());

    tags.put(DB_STATEMENT.getKey(), componentWrapper.getParameter("sql"));
    tags.put(DB_OPERATION.getKey(), component.getIdentifier().getName());

    // data-source-connection
    addTagIfPresent(connectionParams, "dataSourceRef", tags, SemanticAttributes.DB_DATASOURCE.getKey());
    // mssql-connection
    addTagIfPresent(connectionParams, "databaseName", tags, DB_NAME.getKey());
    // mysql-connection
    addTagIfPresent(connectionParams, "database", tags, DB_NAME.getKey());
    addTagIfPresent(connectionParams, "instanceName", tags, DB_MSSQL_INSTANCE_NAME.getKey());

    // oracle-connection
    addTagIfPresent(connectionParams, "instance", tags, "db.oracle.instance");
    addTagIfPresent(connectionParams, "serviceName", tags, "db.oracle.serviceName");

    // generic
    tags.put(DB_JDBC_DRIVER_CLASSNAME.getKey(), connectionParams.get("driverClassName"));

    return tags;
  }

}
