package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import io.opentelemetry.api.common.AttributeKey;

public class SemanticAttributes {
  private SemanticAttributes() {
  }

  public static final AttributeKey<String> MULE_HOME = AttributeKey.stringKey("mule.home");
  public static final AttributeKey<String> MULE_SERVER_ID = AttributeKey.stringKey("mule.serverId");
  public static final AttributeKey<String> MULE_CSORGANIZATION_ID = AttributeKey.stringKey("mule.csOrganization.id");
  public static final AttributeKey<String> MULE_ENVIRONMENT_NAME = AttributeKey.stringKey("mule.environment.name");
  public static final AttributeKey<String> MULE_ENVIRONMENT_ID = AttributeKey.stringKey("mule.environment.id");
  public static final AttributeKey<String> MULE_ENVIRONMENT_TYPE = AttributeKey.stringKey("mule.environment.type");
  public static final AttributeKey<String> MULE_ENVIRONMENT_AWS_REGION = AttributeKey
      .stringKey("mule.environment.awsRegion");
  public static final AttributeKey<String> MULE_WORKER_ID = AttributeKey.stringKey("mule.worker.id");

  public static final AttributeKey<String> MULE_APP_PROCESSOR_NAME = AttributeKey
      .stringKey("mule.app.processor.name");
  public static final AttributeKey<String> MULE_APP_PROCESSOR_NAMESPACE = AttributeKey
      .stringKey("mule.app.processor.namespace");
  public static final AttributeKey<String> MULE_APP_PROCESSOR_DOC_NAME = AttributeKey
      .stringKey("mule.app.processor.docName");
  public static final AttributeKey<String> MULE_APP_PROCESSOR_CONFIG_REF = AttributeKey
      .stringKey("mule.app.processor.configRef");
  public static final AttributeKey<String> MULE_APP_FLOW_NAME = AttributeKey.stringKey("mule.app.flow.name");

  public static final AttributeKey<String> MULE_APP_DOMAIN = AttributeKey.stringKey("mule.app.domain");
  public static final AttributeKey<String> MULE_APP_FULL_DOMAIN = AttributeKey.stringKey("mule.app.fullDomain");

}
