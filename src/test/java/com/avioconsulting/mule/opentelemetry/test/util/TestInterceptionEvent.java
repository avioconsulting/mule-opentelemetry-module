package com.avioconsulting.mule.opentelemetry.test.util;

import org.mule.runtime.api.el.BindingContext;
import org.mule.runtime.api.event.EventContext;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.ItemSequenceInfo;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.security.Authentication;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TestInterceptionEvent implements InterceptionEvent {

  Message message;
  String correlationId;
  Map<String, TypedValue<?>> variables = new HashMap<>();

  public TestInterceptionEvent(String correlationId) {
    this.correlationId = correlationId;
  }

  @Override
  public InterceptionEvent message(Message message) {
    this.message = message;
    return this;
  }

  @Override
  public InterceptionEvent variables(Map<String, Object> variables) {
    return this;
  }

  @Override
  public InterceptionEvent addVariable(String key, Object value, DataType mediaType) {
    this.variables.put(key, new TypedValue<>(value, mediaType));
    return this;
  }

  @Override
  public InterceptionEvent addVariable(String key, Object value) {
    this.variables.put(key, TypedValue.of(value));
    return this;
  }

  @Override
  public InterceptionEvent removeVariable(String key) {
    variables.remove(key);
    return this;
  }

  @Override
  public Map<String, TypedValue<?>> getVariables() {
    return variables;
  }

  @Override
  public Message getMessage() {
    return message;
  }

  @Override
  public Optional<Authentication> getAuthentication() {
    return Optional.empty();
  }

  @Override
  public Optional<Error> getError() {
    return Optional.empty();
  }

  @Override
  public String getCorrelationId() {
    return correlationId;
  }

  @Override
  public Optional<ItemSequenceInfo> getItemSequenceInfo() {
    return Optional.empty();
  }

  @Override
  public EventContext getContext() {
    return null;
  }

  @Override
  public BindingContext asBindingContext() {
    return null;
  }
}
