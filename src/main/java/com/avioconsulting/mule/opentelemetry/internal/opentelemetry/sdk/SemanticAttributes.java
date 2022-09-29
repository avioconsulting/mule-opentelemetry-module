package com.avioconsulting.mule.opentelemetry.internal.opentelemetry.sdk;

import io.opentelemetry.api.common.AttributeKey;

/**
 * Defines the attribute keys to be used when capturing mule related span
 * attributes.
 */
public final class SemanticAttributes {
  private SemanticAttributes() {
  }

  /**
   * Absolute path to mule installation.
   */
  public static final AttributeKey<String> MULE_HOME = AttributeKey.stringKey("mule.home");

  /**
   * Mule Correlation Id for the current event.
   */
  public static final AttributeKey<String> MULE_CORRELATION_ID = AttributeKey.stringKey("mule.correlationId");

  /**
   * Mule Server Id that is processing current request.
   */
  public static final AttributeKey<String> MULE_SERVER_ID = AttributeKey.stringKey("mule.serverId");
  public static final AttributeKey<String> MULE_CSORGANIZATION_ID = AttributeKey.stringKey("mule.csOrganization.id");

  /**
   * Most of the Mule users are familiar with organization id instead of
   * CSORGANIZATION ID.
   */
  public static final AttributeKey<String> MULE_ORGANIZATION_ID = AttributeKey.stringKey("mule.organization.id");

  /**
   * Mule Environment ID. See <a src=
   * "https://help.mulesoft.com/s/article/CloudHub-Reserved-Properties">CloudHub-Reserved-Properties</a>.
   */
  public static final AttributeKey<String> MULE_ENVIRONMENT_ID = AttributeKey.stringKey("mule.environment.id");

  /**
   * Mule Environment Type - eg. sandbox or production. See <a src=
   * "https://help.mulesoft.com/s/article/CloudHub-Reserved-Properties">CloudHub-Reserved-Properties</a>.
   */
  public static final AttributeKey<String> MULE_ENVIRONMENT_TYPE = AttributeKey.stringKey("mule.environment.type");

  /**
   * AWS Region in which Application is deployed in. See <a src=
   * "https://help.mulesoft.com/s/article/CloudHub-Reserved-Properties">CloudHub-Reserved-Properties</a>.
   */
  public static final AttributeKey<String> MULE_ENVIRONMENT_AWS_REGION = AttributeKey
      .stringKey("mule.environment.awsRegion");

  /**
   * Mule CloudHub Worker id that is processing current request. See <a src=
   * "https://help.mulesoft.com/s/article/CloudHub-Reserved-Properties">CloudHub-Reserved-Properties</a>.
   */
  public static final AttributeKey<String> MULE_WORKER_ID = AttributeKey.stringKey("mule.worker.id");

  /**
   * Mule Processor Name. For example `http:request` processor will have `request`
   * as processor name.
   */
  public static final AttributeKey<String> MULE_APP_PROCESSOR_NAME = AttributeKey
      .stringKey("mule.app.processor.name");

  /**
   * XML Namespace of the Mule processor. For example `http:request` processor
   * will have `http` as processor namespace.
   */
  public static final AttributeKey<String> MULE_APP_PROCESSOR_NAMESPACE = AttributeKey
      .stringKey("mule.app.processor.namespace");

  /**
   * Documented name of the processor. Usually, the value of `doc:name` attribute
   * on processor.
   */
  public static final AttributeKey<String> MULE_APP_PROCESSOR_DOC_NAME = AttributeKey
      .stringKey("mule.app.processor.docName");

  /**
   * Name of the configuration element, if exists on the processor. Usually, the
   * value of `configRef` attribute on processor.
   */
  public static final AttributeKey<String> MULE_APP_PROCESSOR_CONFIG_REF = AttributeKey
      .stringKey("mule.app.processor.configRef");
  public static final AttributeKey<String> MULE_APP_FLOW_NAME = AttributeKey.stringKey("mule.app.flow.name");

  /**
   * Name of the configuration element used by the flow source component. Usually,
   * the value of `configRef` attribute on source.
   */
  public static final AttributeKey<String> MULE_APP_FLOW_SOURCE_CONFIG_REF = AttributeKey
      .stringKey("mule.app.flow.source.configRef");
  /**
   * XML Namespace of the Source component. For example `http:listener` source
   * will have `http` as the namespace.
   */
  public static final AttributeKey<String> MULE_APP_FLOW_SOURCE_NAMESPACE = AttributeKey
      .stringKey("mule.app.flow.source.namespace");

  /**
   * Mule Flow Source's Name. For example `http:listener` source will have
   * `listener` as the name.
   */
  public static final AttributeKey<String> MULE_APP_FLOW_SOURCE_NAME = AttributeKey
      .stringKey("mule.app.flow.source.name");

  /**
   * Application Name. See <a src=
   * "https://help.mulesoft.com/s/article/CloudHub-Reserved-Properties">CloudHub-Reserved-Properties</a>.
   */
  public static final AttributeKey<String> MULE_APP_DOMAIN = AttributeKey.stringKey("mule.app.domain");
  /**
   * Full DNS of application. See <a src=
   * "https://help.mulesoft.com/s/article/CloudHub-Reserved-Properties">CloudHub-Reserved-Properties</a>.
   */
  public static final AttributeKey<String> MULE_APP_FULL_DOMAIN = AttributeKey.stringKey("mule.app.fullDomain");

}
