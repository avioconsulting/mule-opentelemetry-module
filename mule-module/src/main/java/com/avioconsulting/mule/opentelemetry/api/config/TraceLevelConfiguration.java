package com.avioconsulting.mule.opentelemetry.api.config;

import org.mule.runtime.api.util.MultiMap;
import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Example;
import org.mule.runtime.extension.api.annotation.param.display.Placement;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.List;

import static java.util.Collections.emptyList;

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
  @Placement(order = 2)
  @DisplayName(value = "Disable Spans for")
  @Summary("When generating spans for all processors, this list defines the processors that should be skipped from tracing. No spans will be generated for these components.")
  private List<MuleComponent> ignoreMuleComponents;

  @Parameter
  @NullSafe
  @Optional
  @Placement(order = 3)
  @DisplayName(value = "Additional Spans for")
  @Summary("List of additional processors that should be traced. This can allow span generation for certain components without turning on Span for All processors flag.")
  private List<MuleComponent> spanAdditionalMuleComponents;

  @Parameter
  @NullSafe
  @Optional
  @Placement(order = 4)
  @DisplayName(value = "Disable Interception for")
  @Example(value = "<opentelemetry:mule-component namespace=\"http\" name=\"requet\" />")
  @Summary("Module uses message processor interception mechanism to inject trace context variable. Any specific message processor (namespace:name) or specific namespace (namespace:*) can be excluded from this interception process. See Context Propagation docs for default included.")
  private List<MuleComponent> interceptionDisabledComponents;

  @Parameter
  @NullSafe
  @Optional
  @Placement(order = 5)
  @DisplayName(value = "Enable Interception for")
  @Summary("Module uses message processor interception mechanism to inject trace context variable. Any specific message processor (namespace:name) or specific namespace (namespace:*) can be included from this interception process.")
  private List<MuleComponent> interceptionEnabledComponents;
  private MultiMap<String, String> spanAdditionalMuleComponentsMap;
  private MultiMap<String, String> ignoreMuleComponentsMap;

  public TraceLevelConfiguration() {
  }

  public TraceLevelConfiguration(boolean spanAllProcessors, List<MuleComponent> ignoreMuleComponents) {
    this(spanAllProcessors, ignoreMuleComponents, emptyList(), emptyList());
  }

  public TraceLevelConfiguration(boolean spanAllProcessors, List<MuleComponent> ignoreMuleComponents,
      List<MuleComponent> spanAdditionalMuleComponents) {
    this(spanAllProcessors, ignoreMuleComponents, spanAdditionalMuleComponents, emptyList(), emptyList());
  }

  public TraceLevelConfiguration(boolean spanAllProcessors, List<MuleComponent> ignoreMuleComponents,
      List<MuleComponent> interceptionDisabledComponents, List<MuleComponent> interceptionEnabledComponents) {
    this(spanAllProcessors, ignoreMuleComponents, emptyList(), interceptionDisabledComponents,
        interceptionEnabledComponents);
  }

  public TraceLevelConfiguration(boolean spanAllProcessors, List<MuleComponent> ignoreMuleComponents,
      List<MuleComponent> spanAdditionalMuleComponents,
      List<MuleComponent> interceptionDisabledComponents, List<MuleComponent> interceptionEnabledComponents) {
    this.spanAllProcessors = spanAllProcessors;
    this.ignoreMuleComponents = ignoreMuleComponents;
    this.spanAdditionalMuleComponents = spanAdditionalMuleComponents;
    this.interceptionDisabledComponents = interceptionDisabledComponents;
    this.interceptionEnabledComponents = interceptionEnabledComponents;
    initMuleComponentsMap();
  }

  public boolean isSpanAllProcessors() {
    return spanAllProcessors;
  }

  public List<MuleComponent> getIgnoreMuleComponents() {
    return ignoreMuleComponents;
  }

  public MultiMap<String, String> getIgnoreMuleComponentsMap() {
    return ignoreMuleComponentsMap;
  }

  public List<MuleComponent> getSpanAdditionalMuleComponents() {
    return spanAdditionalMuleComponents;
  }

  public MultiMap<String, String> getSpanAdditionalMuleComponentsMap() {
    return spanAdditionalMuleComponentsMap;
  }

  public void initMuleComponentsMap() {
    spanAdditionalMuleComponentsMap = muleComponentsListToMultiMap(getSpanAdditionalMuleComponents());
    ignoreMuleComponentsMap = muleComponentsListToMultiMap(getIgnoreMuleComponents());
  }

  public MultiMap<String, String> muleComponentsListToMultiMap(List<MuleComponent> list) {
    MultiMap<String, String> mm = new MultiMap<>();
    for (MuleComponent component : list) {
      mm.put(component.getNamespace(), component.getName());
    }
    return mm.toImmutableMultiMap();
  }

  public List<MuleComponent> getInterceptionDisabledComponents() {
    return interceptionDisabledComponents;
  }

  public List<MuleComponent> getInterceptionEnabledComponents() {
    return interceptionEnabledComponents;
  }

}
