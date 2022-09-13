package com.avioconsulting.mule.opentelemetry.api.config;

import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.List;

public class TraceLevelConfiguration {

  @Parameter
  @Optional(defaultValue = "false")
  @Placement(order = 1)
  @DisplayName(value = "Span All Processors")
  @Summary("By default, Spans are created for known components only. Setting this flag to true will create trace spans for every processor in a flow.")
  private boolean spanAllProcessors = false;

  @Parameter
  @NullSafe
  @Optional
  @Placement(order = 1)
  @DisplayName(value = "Disable Spans for")
  @Summary("When generating spans for all processors, this list defines the processors that should be skipped from tracing. No spans will be generated for these components.")
  private List<MuleComponent> ignoreMuleComponents;

  public TraceLevelConfiguration() {
  }

  public TraceLevelConfiguration(boolean spanAllProcessors, List<MuleComponent> ignoreMuleComponents) {
    this.spanAllProcessors = spanAllProcessors;
    this.ignoreMuleComponents = ignoreMuleComponents;
  }

  public boolean isSpanAllProcessors() {
    return spanAllProcessors;
  }

  public List<MuleComponent> getIgnoreMuleComponents() {
    return ignoreMuleComponents;
  }
}
