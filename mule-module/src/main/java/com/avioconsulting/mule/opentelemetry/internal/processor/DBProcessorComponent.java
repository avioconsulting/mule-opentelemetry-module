package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.internal.processor.db.DBConnectionConfigParser;
import com.avioconsulting.mule.opentelemetry.internal.processor.db.DBInfo;
import com.avioconsulting.mule.opentelemetry.internal.processor.db.JDBCUrlParser;
import com.avioconsulting.mule.opentelemetry.internal.util.memoizers.BiFunctionMemoizer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.el.ExpressionExecutionException;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.metadata.TypedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.avioconsulting.mule.opentelemetry.api.sdk.SemanticAttributes.DB_DATASOURCE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;

public class DBProcessorComponent extends AbstractProcessorComponent {
  private static final Logger LOGGER = LoggerFactory.getLogger(DBProcessorComponent.class);
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
  public TraceComponent getStartTraceComponent(Component component, Event event) {
    TraceComponent startTraceComponent = super.getStartTraceComponent(component, event);
    if (startTraceComponent.getTags().containsKey("inputParameters")) {
      String inputParametersExpression = startTraceComponent.getTags().remove("inputParameters");
      try {
        if (inputParametersExpression != null && expressionManager.isExpression(inputParametersExpression)) {
          TypedValue<?> parameters = expressionManager.evaluate(inputParametersExpression,
              event.asBindingContext());
          if (parameters.getValue() instanceof Map) {
            Map<String, Object> value = (Map<String, Object>) parameters.getValue();
            value.forEach((k, v) -> {
              startTraceComponent.getTags().put(
                  DbIncubatingAttributes.DB_OPERATION_PARAMETER.getAttributeKey(k).getKey(),
                  v == null ? "null" : v.toString());
            });
          }
        }
      } catch (ExpressionExecutionException e) {
        LOGGER.warn(
            "Failed to evaluate input parameters expression, capturing the SQL operation parameters for {} at {} will be skipped",
            startTraceComponent.getName(), startTraceComponent.getLocation(), e);
      }
    }
    return startTraceComponent;
  }

  private final BiFunctionMemoizer<String, ComponentWrapper, DBInfo> dbInfoBiFunctionMemoizer = BiFunctionMemoizer
      .memoize((configName, cw) -> getDbInfo(cw));

  @Override
  protected <A> void addAttributes(Component component, TypedValue<A> attributes, Map<String, String> collector) {
    ComponentWrapper componentWrapper = componentRegistryService.getComponentWrapper(component);

    DBInfo dbInfo = dbInfoBiFunctionMemoizer.apply(componentWrapper.getConfigRef(), componentWrapper);

    collector.put(DB_SYSTEM.getKey(), dbInfo.getSystem());
    if (dbInfo.getDatasourceRef() != null) {
      collector.put(DB_DATASOURCE.getKey(), dbInfo.getDatasourceRef());
    }
    if (dbInfo.getNamespace() != null) {
      collector.put(DB_NAMESPACE.getKey(), dbInfo.getNamespace());
    }
    if (dbInfo.getHost() != null) {
      collector.put(SERVER_ADDRESS.getKey(), dbInfo.getHost());
    }
    if (dbInfo.getPort() != null) {
      collector.put(SERVER_PORT.getKey(), dbInfo.getPort());
    }
    collector.put(DB_QUERY_TEXT.getKey(), componentWrapper.getParameter("sql"));
    collector.put(DB_OPERATION_NAME.getKey(), component.getIdentifier().getName());
    collector.put("inputParameters", componentWrapper.getParameter("inputParameters"));
  }

  private DBInfo getDbInfo(ComponentWrapper componentWrapper) {
    Map<String, String> connectionParams = componentWrapper.getConfigConnectionParameters();
    String connectionComponentName = connectionParams.get(ComponentWrapper.COMPONENT_NAME_KEY);
    String connectionName = "other_sql";
    DBInfo dbInfo = new DBInfo("other_sql", null, null, null, null, null, "", null);
    if (connectionComponentName != null) {
      connectionName = connectionComponentName.substring(0, connectionComponentName.lastIndexOf("-"));
      if ("generic".equalsIgnoreCase(connectionName)) {
        String jdbcUrl = connectionParams.get("url");
        dbInfo = JDBCUrlParser.parse(jdbcUrl);
      } else if ("data-source".equalsIgnoreCase(connectionName)) {
        String dataSourceRef = connectionParams.get("dataSourceRef");
        dbInfo = new DBInfo("other_sql", null, null, null, null, null,
            dataSourceRef, null);
        if (dataSourceRef != null) {
          dbInfo.setDatasourceRef(connectionParams.get("dataSourceRef"));
        }
      } else {
        if (connectionName.contains("-")) {
          connectionName = connectionName.replace("-", "");
        }
        dbInfo = DBConnectionConfigParser.getDBInfo(connectionName, connectionParams);
      }
    }
    return dbInfo;
  }
}
