package com.avioconsulting.mule.opentelemetry.api.sdk;

import io.opentelemetry.api.common.AttributeKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

/**
 * Defines the attribute keys to be used when capturing mule related span
 * attributes.
 */
public final class SemanticAttributes {
  private SemanticAttributes() {
  }

  /**
   * Override {@link io.opentelemetry.semconv.SemanticAttributes#SERVER_PORT} type
   * from Long to String
   */
  public static final AttributeKey<String> SERVER_PORT_SA = stringKey("server.port");
  /**
   * Override
   * {@link io.opentelemetry.semconv.SemanticAttributes#HTTP_RESPONSE_STATUS_CODE}
   * type from Long to String
   */
  public static final AttributeKey<String> HTTP_RESPONSE_STATUS_CODE_SA = stringKey("http.response.status_code");

  public static final AttributeKey<String> HTTP_RESPONSE_HEADER_CONTENT_LENGTH = stringKey(
      "http.response.header.content-length");
  public static final AttributeKey<String> MULE_APP_PROCESSOR_FLOW_REF_NAME = stringKey(
      "mule.app.processor.flowRef.name");
  /**
   * Absolute path to mule installation.
   */
  public static final AttributeKey<String> MULE_HOME = stringKey("mule.home");

  /**
   * Mule Correlation Id for the current event.
   */
  public static final AttributeKey<String> MULE_CORRELATION_ID = stringKey("mule.correlationId");

  /**
   * Mule Server Id that is processing current request.
   */
  public static final AttributeKey<String> MULE_SERVER_ID = stringKey("mule.serverId");
  public static final AttributeKey<String> MULE_CSORGANIZATION_ID = stringKey("mule.csOrganization.id");

  /**
   * Most of the Mule users are familiar with organization id instead of
   * CSORGANIZATION ID.
   */
  public static final AttributeKey<String> MULE_ORGANIZATION_ID = stringKey("mule.organization.id");

  /**
   * Mule Environment ID. See <a src=
   * "https://help.mulesoft.com/s/article/CloudHub-Reserved-Properties">CloudHub-Reserved-Properties</a>.
   */
  public static final AttributeKey<String> MULE_ENVIRONMENT_ID = stringKey("mule.environment.id");

  /**
   * Mule Environment Type - eg. sandbox or production. See <a src=
   * "https://help.mulesoft.com/s/article/CloudHub-Reserved-Properties">CloudHub-Reserved-Properties</a>.
   */
  public static final AttributeKey<String> MULE_ENVIRONMENT_TYPE = stringKey("mule.environment.type");

  /**
   * AWS Region in which Application is deployed in. See <a src=
   * "https://help.mulesoft.com/s/article/CloudHub-Reserved-Properties">CloudHub-Reserved-Properties</a>.
   */
  public static final AttributeKey<String> MULE_ENVIRONMENT_AWS_REGION = stringKey("mule.environment.awsRegion");

  /**
   * Mule CloudHub Worker id that is processing current request. See <a src=
   * "https://help.mulesoft.com/s/article/CloudHub-Reserved-Properties">CloudHub-Reserved-Properties</a>.
   */
  public static final AttributeKey<String> MULE_WORKER_ID = stringKey("mule.worker.id");

  /**
   * Mule Processor Name. For example `http:request` processor will have `request`
   * as processor name.
   */
  public static final AttributeKey<String> MULE_APP_PROCESSOR_NAME = stringKey("mule.app.processor.name");

  /**
   * XML Namespace of the Mule processor. For example `http:request` processor
   * will have `http` as processor namespace.
   */
  public static final AttributeKey<String> MULE_APP_PROCESSOR_NAMESPACE = stringKey("mule.app.processor.namespace");

  /**
   * Documented name of the processor. Usually, the value of `doc:name` attribute
   * on processor.
   */
  public static final AttributeKey<String> MULE_APP_PROCESSOR_DOC_NAME = stringKey("mule.app.processor.docName");

  /**
   * Name of the configuration element, if exists on the processor. Usually, the
   * value of `configRef` attribute on processor.
   */
  public static final AttributeKey<String> MULE_APP_PROCESSOR_CONFIG_REF = stringKey("mule.app.processor.configRef");
  public static final AttributeKey<String> MULE_APP_FLOW_NAME = stringKey("mule.app.flow.name");

  /**
   * Name of the configuration element used by the flow source component. Usually,
   * the value of `configRef` attribute on source.
   */
  public static final AttributeKey<String> MULE_APP_FLOW_SOURCE_CONFIG_REF = stringKey(
      "mule.app.flow.source.configRef");
  /**
   * XML Namespace of the Source component. For example `http:listener` source
   * will have `http` as the namespace.
   */
  public static final AttributeKey<String> MULE_APP_FLOW_SOURCE_NAMESPACE = stringKey(
      "mule.app.flow.source.namespace");

  /**
   * Mule Flow Source's Name. For example `http:listener` source will have
   * `listener` as the name.
   */
  public static final AttributeKey<String> MULE_APP_FLOW_SOURCE_NAME = stringKey("mule.app.flow.source.name");

  /**
   * Application Name. See <a src=
   * "https://help.mulesoft.com/s/article/CloudHub-Reserved-Properties">CloudHub-Reserved-Properties</a>.
   */
  public static final AttributeKey<String> MULE_APP_DOMAIN = stringKey("mule.app.domain");
  /**
   * Full DNS of application. See <a src=
   * "https://help.mulesoft.com/s/article/CloudHub-Reserved-Properties">CloudHub-Reserved-Properties</a>.
   */
  public static final AttributeKey<String> MULE_APP_FULL_DOMAIN = stringKey("mule.app.fullDomain");

  /**
   * Key to define datasource name for db connections
   */
  public static final AttributeKey<String> DB_DATASOURCE = stringKey("db.datasource");

  /**
   * Key to capture Error types
   */
  public static final AttributeKey<String> ERROR_TYPE = stringKey("error.type");

  public static final AttributeKey<String> MULE_APP_SCOPE_SUBFLOW_NAME = stringKey("mule.app.scope.subflow.name");

  public static final AttributeKey<String> WSC_CONSUMER_OPERATION = stringKey("mule.wsc.consumer.operation");
  public static final AttributeKey<String> WSC_CONFIG_SERVICE = stringKey("mule.wsc.config.service");
  public static final AttributeKey<String> WSC_CONFIG_PORT = stringKey("mule.wsc.config.port");
  public static final AttributeKey<String> WSC_CONFIG_ADDRESS = stringKey("mule.wsc.config.address");
}
