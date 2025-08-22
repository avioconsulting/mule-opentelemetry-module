package com.avioconsulting.mule.opentelemetry.internal.interceptor;

import com.avioconsulting.mule.opentelemetry.internal.AbstractInternalTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.component.TypedComponentIdentifier;
import org.mule.runtime.api.component.location.ComponentLocation;
import org.mule.runtime.api.component.location.LocationPart;
import org.mule.runtime.api.event.Event;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class InterceptorProcessorConfigDefaultTest extends AbstractInternalTest {

  @Parameterized.Parameter(value = 0)
  public String namespace;

  @Parameterized.Parameter(value = 1)
  public String name;
  private Event event = getEvent();

  @Parameterized.Parameters(name = "{index}: Intercept {0}:{1}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { "batch", "job" },
        { "anypoint-mq", "publish" },
        { "sqs", "send-message" },
        { "sqs", "send-message-batch" },
        { "sns", "publish" },
        { "amqp", "publish" },
        { "amqp", "publish-consume" },
        { "kafka", "publish" },
        { "kafka", "bulk-publish" },
        { "azure-service-bus-messaging", "send" },
        { "azure-service-bus-messaging", "send-message-batch" },
        { "pubsub", "publish-message" },
        { "ibm-mq", "publish" },
        { "ibm-mq", "publish-consume" },
        { "jms", "publish" },
        { "jms", "publish-consume" },
        { "msmq", "send" },
        { "servicebus", "topic-send" },
        { "mqtt3", "publish" },
        { "salesforce-pub-sub", "publish-event" },
        { "mule", "flow-ref" },
        { "mule", "choice" },
        { "mule", "first-successful" },
        { "mule", "until-successful" },
        { "mule", "scatter-gather" },
        { "mule", "round-robin" },
        { "mule", "foreach" },
        { "mule", "parallel-foreach" },
        { "mule", "try" }
    });
  }

  @Test
  public void shouldIntercept() {
    ComponentLocation location = Mockito.mock(ComponentLocation.class);
    when(location.getRootContainerName()).thenReturn("MyFlow");
    when(location.getLocation()).thenReturn("MyFlow/processors/anything-but-0");

    TypedComponentIdentifier logger = mock(TypedComponentIdentifier.class);
    ComponentIdentifier loggerIdentifier = mock(ComponentIdentifier.class);
    when(loggerIdentifier.getNamespace()).thenReturn(namespace);
    when(loggerIdentifier.getName()).thenReturn(name);
    when(logger.getIdentifier()).thenReturn(loggerIdentifier);
    when(location.getComponentIdentifier()).thenReturn(logger);

    LocationPart part1 = mock(LocationPart.class);
    TypedComponentIdentifier identifier = mock(TypedComponentIdentifier.class);
    when(identifier.getType()).thenReturn(TypedComponentIdentifier.ComponentType.FLOW);
    when(part1.getPartIdentifier()).thenReturn(Optional.of(identifier));
    when(location.getParts()).thenReturn(Arrays.asList(part1));
    assertThat(
        new InterceptorProcessorConfig().shouldIntercept(location, event))
            .isTrue();
  }
}