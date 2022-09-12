package com.avioconsulting.mule.opentelemetry.api.config;

import org.mule.runtime.api.meta.ExpressionSupport;
import org.mule.runtime.extension.api.annotation.Expression;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

public class TraceLevelConfiguration {

  @Parameter
  @Optional(defaultValue = "false")
  @DisplayName(value = "Span All Processors")
  @Summary("By default, Spans are created for known components only. Setting this flag to true will create trace spans for every processor in a flow.")
  @Expression(ExpressionSupport.NOT_SUPPORTED)
  private boolean spanAllProcessors = false;

  public boolean isSpanAllProcessors() {
    return spanAllProcessors;
  }

}
