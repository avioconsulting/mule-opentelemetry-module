package com.avioconsulting.mule.opentelemetry.internal;

import com.avioconsulting.mule.opentelemetry.api.config.exporter.GenericExporter;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.LoggingExporter;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OpenTelemetryExporter;
import com.avioconsulting.mule.opentelemetry.api.config.exporter.OtlpExporter;
import com.avioconsulting.mule.opentelemetry.internal.config.OpenTelemetryExtensionConfiguration;
import com.avioconsulting.mule.opentelemetry.internal.notifications.MetricNotificationAction;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.SubTypeMapping;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.runtime.extension.api.annotation.notification.NotificationActions;

@Xml(prefix = "opentelemetry")
@Extension(name = "OpenTelemetry")
@NotificationActions(MetricNotificationAction.class)
@Configurations(OpenTelemetryExtensionConfiguration.class)
@SubTypeMapping(baseType = OpenTelemetryExporter.class, subTypes = { OtlpExporter.class, LoggingExporter.class,
    GenericExporter.class })
public class OpenTelemetryExtension {
}
