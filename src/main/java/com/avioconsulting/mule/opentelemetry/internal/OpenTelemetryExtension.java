package com.avioconsulting.mule.opentelemetry.internal;

import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;


@Xml(prefix = "opentelemetry")
@Extension(name = "OpenTelemetry")
@Configurations(OpenTelemetryConfiguration.class)
public class OpenTelemetryExtension {

}
