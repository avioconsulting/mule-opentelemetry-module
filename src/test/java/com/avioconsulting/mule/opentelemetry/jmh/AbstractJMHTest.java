package com.avioconsulting.mule.opentelemetry.jmh;

import org.junit.Assume;
import org.junit.Test;
import org.mule.runtime.api.el.BindingContext;
import org.mule.runtime.api.event.EventContext;
import org.mule.runtime.api.interception.InterceptionEvent;
import org.mule.runtime.api.message.Error;
import org.mule.runtime.api.message.ItemSequenceInfo;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.security.Authentication;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * To run JMH, set runJMH=true on system properties.
 * Eg. Run maven command - `./mvnw test -DrunJMH=true`
 */

public abstract class AbstractJMHTest {
  @Test
  public void runBenchmarks() throws Exception {
    Assume.assumeTrue(System.getProperty("runJMH", "false").equals("true"));
    Options options = new OptionsBuilder()
        .include(getClass().getSimpleName().concat(".*"))
        .mode(Mode.Throughput)
        .warmupTime(TimeValue.seconds(1))
        .warmupIterations(getWarmupIterations())
        .threads(1)
        .measurementIterations(getIterations())
        .forks(1)
        .shouldFailOnError(true)
        .shouldDoGC(true)
        .build();

    new Runner(options).run();
  }

  public int getWarmupIterations() {
    return 6;
  }

  public int getIterations() {
    return 6;
  }

  static class TestInterceptionEvent implements InterceptionEvent {

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

}
