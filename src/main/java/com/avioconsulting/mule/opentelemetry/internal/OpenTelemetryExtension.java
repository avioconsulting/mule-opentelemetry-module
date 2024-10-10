package com.avioconsulting.mule.opentelemetry.internal;

import com.avioconsulting.mule.opentelemetry.api.AppIdentifier;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.GenericExporter;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.LoggingExporter;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OtlpExporter;
import com.avioconsulting.mule.opentelemetry.api.notifications.MetricBaseNotificationData;
import com.avioconsulting.mule.opentelemetry.api.providers.NoopOpenTelemetryMetricsConfigProvider;
import com.avioconsulting.mule.opentelemetry.api.providers.OpenTelemetryMetricsConfigProvider;
import com.avioconsulting.mule.opentelemetry.api.providers.OpenTelemetryMetricsConfigSupplier;
import com.avioconsulting.mule.opentelemetry.api.providers.OpenTelemetryMetricsProvider;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import com.avioconsulting.mule.opentelemetry.logs.api.LogsApiPackageMarker;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.Export;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.SubTypeMapping;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.sdk.api.annotation.JavaVersionSupport;
import org.mule.sdk.api.meta.JavaVersion;

@Xml(prefix = "opentelemetry")
@Extension(name = "OpenTelemetry")
@Configurations(OpenTelemetryExtensionConfiguration.class)
@SubTypeMapping(baseType = OpenTelemetryExporter.class, subTypes = { OtlpExporter.class, LoggingExporter.class,
    GenericExporter.class })
@SubTypeMapping(baseType = OpenTelemetryMetricsConfigProvider.class, subTypes = {
    NoopOpenTelemetryMetricsConfigProvider.class })
@Export(classes = { OpenTelemetryMetricsConfigProvider.class, AppIdentifier.class, OpenTelemetryMetricsProvider.class,
    MetricBaseNotificationData.class, OpenTelemetryMetricsConfigSupplier.class, LogsApiPackageMarker.class })
@JavaVersionSupport({ JavaVersion.JAVA_8, JavaVersion.JAVA_11, JavaVersion.JAVA_17 })
public class OpenTelemetryExtension {
}
