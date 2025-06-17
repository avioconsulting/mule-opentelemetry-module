package com.avioconsulting.mule.opentelemetry.api;

import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.el.ExpressionManager;

import java.util.Objects;

public final class AppIdentifier {
  private final String identifier;
  private final String name;
  private final String orgId;
  private final String envId;

  public AppIdentifier(String name, String orgId, String envId) {
    Objects.requireNonNull(name);
    Objects.requireNonNull(envId);
    this.name = name;
    this.orgId = orgId;
    this.envId = envId;
    identifier = (envId + "+" + name);
  }

  public String getName() {
    return name;
  }

  public String getOrgId() {
    return orgId;
  }

  public String getEnvId() {
    return envId;
  }

  public String getIdentifier() {
    return identifier;
  }

  @Override
  public String toString() {
    return identifier;
  }

  public static AppIdentifier fromEnvironment(ExpressionManager expressionManager) {
    TypedValue<String> appName = expressionManager.evaluate("#[app.name]", DataType.STRING);
    String orgId = getPropertyValue(expressionManager, "csorganization.id", "ORG_ID",
        "anypoint.platform.client_id");
    if (orgId == null) {
      orgId = "DEFAULT_ORG";
    }
    String envId = getPropertyValue(expressionManager, "environment.id", "ENV_ID", "anypoint.platform.client_id");
    if (envId == null) {
      envId = ((TypedValue<String>) expressionManager.evaluate("#[server.host]", DataType.STRING)).getValue();
    }
    return new AppIdentifier(appName.getValue(), orgId, envId);
  }

  private static String getPropertyValue(ExpressionManager expressionManager, String property, String... envKeys) {
    String envId = getStringValue(expressionManager, property);
    if (envId == null) {
      for (String envKey : envKeys) {
        envId = getStringValue(expressionManager, envKey);
        if (envId != null) {
          break;
        }
      }
    }
    return envId;
  }

  private static String getStringValue(ExpressionManager expressionManager, String property) {
    TypedValue evaluate = expressionManager.evaluate("#[p('" + property + "')]");
    if (evaluate == null || evaluate.getValue() == null)
      return null;
    return evaluate.getValue().toString();
  }
}
