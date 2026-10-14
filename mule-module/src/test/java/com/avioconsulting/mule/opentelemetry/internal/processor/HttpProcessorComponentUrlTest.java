package com.avioconsulting.mule.opentelemetry.internal.processor;

import com.avioconsulting.mule.opentelemetry.api.traces.TraceComponent;
import com.avioconsulting.mule.opentelemetry.api.processor.ProcessorComponent;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ComponentRegistryService;
import com.avioconsulting.mule.opentelemetry.internal.processor.service.ProcessorComponentService;
import com.avioconsulting.mule.opentelemetry.internal.util.OpenTelemetryUtil;
import org.junit.Test;
import org.mule.runtime.api.component.Component;
import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.runtime.api.el.BindingContext;
import org.mule.runtime.api.event.Event;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.core.api.el.ExpressionManager;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HttpProcessorComponentUrlTest extends AbstractProcessorComponentTest {

  @Test
  public void literalUrlOverridesConnectionBasePathAndPath() {
    TraceComponent trace = trace("https://actual.example:8443/orders/123", "/ignored", "POST", "/base");
    assertUrl(trace, "https", "actual.example", "8443", "/orders/123");
    assertThat(trace.getReadOnlyTags()).containsEntry("url.full", "https://actual.example:8443/orders/123");
    assertThat(trace.getSpanName()).isEqualTo("POST");
  }

  @Test
  public void urlWithNoPathParameterDoesNotConcatenateNullBasePath() {
    TraceComponent trace = trace("http://actual.example/resource", null, "GET", "/base");
    assertUrl(trace, "http", "actual.example", "80", "/resource");
  }

  @Test
  public void httpsDefaultPortAndDefaultMethod() {
    TraceComponent trace = trace("https://actual.example/resource", null, null, null);
    assertUrl(trace, "https", "actual.example", "443", "/resource");
    assertThat(trace.getReadOnlyTags()).containsEntry("http.request.method", "GET");
    assertThat(trace.getSpanName()).isEqualTo("GET");
  }

  @Test
  public void removesCredentialsFragmentAndAllQueryValues() {
    TraceComponent trace = trace(
        "https://private-user:private-password@actual.example/a%2Fb?token=private-token&search=private-search#private-fragment",
        "/ignored", "GET", null);
    assertUrl(trace, "https", "actual.example", "443", "/a%2Fb");
    assertThat(trace.getReadOnlyTags()).containsKeys("url.full", "url.query");
    String query = trace.getTag("url.query");
    assertThat(query).contains("token=", "search=").doesNotContain("private-");
    assertThat(trace.getTag("url.full")).startsWith("https://actual.example/a%2Fb?").endsWith(query);
    assertThat(trace.getReadOnlyTags().toString()).doesNotContain("private-");
    assertThat(trace.getSpanName()).isEqualTo("GET");
  }

  @Test
  public void preservesEncodedPathQueryKeysAndExplicitPort() {
    TraceComponent trace = trace(
        "http://actual.example:8080/a%2Fb/c%20d?api%5Fkey=secret%2Fvalue&api%5Fkey=another-secret",
        null, "GET", null);
    assertUrl(trace, "http", "actual.example", "8080", "/a%2Fb/c%20d");
    assertThat(trace.getTag("url.query")).contains("api%5Fkey=").doesNotContain("secret", "value");
    assertThat(trace.getTag("url.full")).startsWith("http://actual.example:8080/a%2Fb/c%20d?");
  }

  @Test
  public void ipv6UrlHasExplicitPort() {
    TraceComponent trace = trace("http://[::1]:8080/resource", null, "GET", null);
    assertThat(trace.getTag("server.address")).isIn("::1", "[::1]");
    assertThat(trace.getReadOnlyTags()).containsEntry("server.port", "8080")
        .containsEntry("url.path", "/resource").doesNotContainKey("http.route");
    assertThat(trace.getTag("url.full")).isEqualTo("http://[::1]:8080/resource");
  }

  @Test
  public void malformedUrlKeepsMethodWithoutFalseConnectionOrRawSecrets() {
    assertUnavailableUrl("https://private-user:private-password@bad host/path?token=private-token", "POST");
  }

  @Test
  public void unsupportedAndRelativeUrlsDoNotFallbackToConnection() {
    assertUnavailableUrl("ftp://private-user:private-password@actual.example/path?token=private-token", "GET");
    assertUnavailableUrl("/relative/path?token=private-token", "GET");
  }

  @Test
  public void unresolvedExpressionDoesNotLeakOrUseConnection() {
    assertUnavailableUrl("#[vars.privateUrl]", "GET");
  }

  @Test
  public void absentUrlRetainsConnectionPathBehavior() {
    TraceComponent trace = trace(null, "/resource", "POST", "/base");
    assertThat(trace.getReadOnlyTags()).containsEntry("server.address", "configured.invalid")
        .containsEntry("server.port", "9999").containsEntry("url.scheme", "http")
        .containsEntry("http.route", "/base/resource").containsEntry("http.request.method", "POST");
    assertThat(trace.getSpanName()).isEqualTo("/base/resource");
  }

  @Test
  public void dynamicUrlIsEvaluatedExactlyOnceForEachEventIncludingGenericResolution() {
    String expression = "#[vars.targetUrl]";
    RequestFixture fixture = fixture(expression, null, "GET", "/ignored");
    ExpressionManager manager = mock(ExpressionManager.class);
    fixture.processor.withExpressionManager(manager);
    when(manager.isExpression(expression)).thenReturn(true);
    Event first = requestEvent();
    Event second = requestEvent();
    BindingContext firstBindings = BindingContext.builder().build();
    BindingContext secondBindings = BindingContext.builder().build();
    when(first.asBindingContext()).thenReturn(firstBindings);
    when(second.asBindingContext()).thenReturn(secondBindings);
    doReturn(TypedValue.of("https://first.example/first?token=first-private-secret"))
        .when(manager).evaluate(expression, DataType.STRING, firstBindings);
    doReturn(TypedValue.of("http://second.example:8080/second?token=second-private-secret"))
        .when(manager).evaluate(expression, DataType.STRING, secondBindings);

    TraceComponent firstTrace = fixture.processor.getStartTraceComponent(fixture.component, first);
    OpenTelemetryUtil.resolveExpressions(firstTrace, manager, first);
    TraceComponent secondTrace = fixture.processor.getStartTraceComponent(fixture.component, second);
    OpenTelemetryUtil.resolveExpressions(secondTrace, manager, second);

    assertUrl(firstTrace, "https", "first.example", "443", "/first");
    assertUrl(secondTrace, "http", "second.example", "8080", "/second");
    assertThat(firstTrace.getReadOnlyTags().toString()).doesNotContain("private-secret", expression);
    assertThat(secondTrace.getReadOnlyTags().toString()).doesNotContain("private-secret", expression);
    verify(manager, times(1)).evaluate(expression, DataType.STRING, firstBindings);
    verify(manager, times(1)).evaluate(expression, DataType.STRING, secondBindings);
    verify(manager, times(2)).isExpression(expression);
  }

  @Test
  public void listenerLookupWithNullManagerPreservesRequesterInjection() throws Exception {
    // Isolate service state: production's singleton caches handlers across tests.
    Constructor<ProcessorComponentService> constructor = ProcessorComponentService.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    ProcessorComponentService service = constructor.newInstance();
    ExpressionManager manager = mock(ExpressionManager.class);
    String expression = "#[vars.targetUrl]";
    RequestFixture fixture = fixture(expression, null, "GET", null);
    ComponentIdentifier request = ComponentIdentifier.builder().namespace("http").name("request").build();
    ComponentIdentifier listener = ComponentIdentifier.builder().namespace("http").name("listener").build();
    ProcessorComponent requesterHandler = service.getProcessorComponentFor(request, manager, fixture.registry);
    ProcessorComponent listenerHandler = service.getProcessorComponentFor(listener, null, fixture.registry);
    assertThat(listenerHandler).isSameAs(requesterHandler);
    assertThat(service.getProcessorComponentFor(request, manager, fixture.registry)).isSameAs(requesterHandler);
    Event event = requestEvent();
    BindingContext bindings = BindingContext.builder().build();
    when(event.asBindingContext()).thenReturn(bindings);
    when(manager.isExpression(expression)).thenReturn(true);
    doReturn(TypedValue.of("https://actual.example/after-listener"))
        .when(manager).evaluate(expression, DataType.STRING, bindings);
    TraceComponent trace = requesterHandler.getStartTraceComponent(fixture.component, event);
    assertUrl(trace, "https", "actual.example", "443", "/after-listener");
    verify(manager, times(1)).evaluate(expression, DataType.STRING, bindings);
  }

  @Test
  public void missingOrEmptyUriPathUsesRootPath() {
    for (String url : new String[] { "https://actual.example", "https://actual.example?token=private-secret" }) {
      TraceComponent trace = trace(url, null, "GET", "/ignored");
      assertUrl(trace, "https", "actual.example", "443", "/");
      assertThat(trace.getTag("url.full")).startsWith("https://actual.example/").doesNotContain("private-secret");
    }
  }

  @Test
  public void invalidPortsAndMissingHostReturnMethodOnly() {
    for (String url : new String[] { "https://actual.example:65536/path", "http://actual.example:-2/path",
        "https:///path?token=private-secret", "https://:443/path?token=private-secret" }) {
      assertUnavailableUrl(url, "POST");
    }
  }

  @Test
  public void emptyAndPresentNullUrlReturnMethodOnly() {
    assertUnavailableUrl("", "GET");
    RequestFixture fixture = fixture("placeholder", null, "GET", "/ignored");
    fixture.parameters.put("url", null);
    assertMethodOnly(fixture.processor.getStartTraceComponent(fixture.component, requestEvent()), "GET");
  }

  @Test
  public void nullEvaluationResultReturnsMethodOnly() {
    assertFailedEvaluation("null-result");
  }

  @Test
  public void nullEvaluatedValueReturnsMethodOnly() {
    assertFailedEvaluation("null-value");
  }

  @Test
  public void throwingEvaluatorReturnsMethodOnlyWithoutExceptionSecrets() {
    assertFailedEvaluation("throws");
  }

  @Test
  public void throwingExpressionClassificationReturnsMethodOnlyWithoutSecrets() {
    String expression = "#[vars.privateUrl]";
    RequestFixture fixture = fixture(expression, null, "GET", "/ignored");
    ExpressionManager manager = mock(ExpressionManager.class);
    fixture.processor.withExpressionManager(manager);
    when(manager.isExpression(expression)).thenThrow(new IllegalStateException("private-classification-secret"));
    assertMethodOnly(fixture.processor.getStartTraceComponent(fixture.component, requestEvent()), "GET");
  }

  @Test
  public void duplicateKeysFlagsEmptyParametersAndSemicolonValuesAreSafe() {
    TraceComponent trace = trace(
        "https://actual.example/path?token=private-one&token=private-two&flag&empty=&mixed=private-three;password=private-four&&",
        null, "GET", null);
    String query = trace.getTag("url.query");
    assertThat(query).isNotNull().doesNotContain("private-", "password");
    String[] parameters = query.split("&", -1);
    assertThat(parameters).hasSize(7);
    assertThat(parameters[0]).startsWith("token=");
    assertThat(parameters[1]).startsWith("token=");
    assertThat(parameters[2]).startsWith("flag");
    assertThat(parameters[3]).startsWith("empty=");
    assertThat(parameters[4]).startsWith("mixed=");
    assertThat(trace.getTag("url.full")).isEqualTo("https://actual.example/path?" + query);
  }

  private void assertFailedEvaluation(String behavior) {
    String expression = "#[vars.privateUrl]";
    RequestFixture fixture = fixture(expression, null, "POST", "/ignored");
    ExpressionManager manager = mock(ExpressionManager.class);
    fixture.processor.withExpressionManager(manager);
    Event event = requestEvent();
    BindingContext bindings = BindingContext.builder().build();
    when(event.asBindingContext()).thenReturn(bindings);
    when(manager.isExpression(expression)).thenReturn(true);
    if ("throws".equals(behavior)) {
      when(manager.evaluate(expression, DataType.STRING, bindings))
          .thenThrow(new IllegalStateException(
              "https://private-user:private-password@actual.example?token=private-token"));
    } else if ("null-value".equals(behavior)) {
      when(manager.evaluate(expression, DataType.STRING, bindings)).thenReturn(TypedValue.of(null));
    }
    assertMethodOnly(fixture.processor.getStartTraceComponent(fixture.component, event), "POST");
    verify(manager, times(1)).evaluate(expression, DataType.STRING, bindings);
  }

  private void assertUnavailableUrl(String url, String method) {
    TraceComponent trace = trace(url, "/ignored", method, "/base");
    assertMethodOnly(trace, method);
  }

  private void assertMethodOnly(TraceComponent trace, String method) {
    assertThat(trace).isNotNull();
    assertThat(trace.getReadOnlyTags()).containsEntry("http.request.method", method)
        .doesNotContainKeys("server.address", "server.port", "url.scheme", "url.full", "url.path", "url.query",
            "http.route");
    assertThat(trace.getReadOnlyTags().toString()).doesNotContain("private-", "#[", "configured.invalid");
    assertThat(trace.getSpanName()).isEqualTo(method);
  }

  private void assertUrl(TraceComponent trace, String scheme, String host, String port, String path) {
    assertThat(trace.getReadOnlyTags()).containsEntry("url.scheme", scheme).containsEntry("server.address", host)
        .containsEntry("server.port", port).containsEntry("url.path", path).doesNotContainKey("http.route");
  }

  private TraceComponent trace(String url, String path, String method, String basePath) {
    RequestFixture fixture = fixture(url, path, method, basePath);
    return fixture.processor.getStartTraceComponent(fixture.component, requestEvent());
  }

  private RequestFixture fixture(String url, String path, String method, String basePath) {
    Map<String, String> parameters = new HashMap<>();
    if (url != null)
      parameters.put("url", url);
    if (path != null)
      parameters.put("path", path);
    if (method != null)
      parameters.put("method", method);
    Component component = getComponent(getComponentLocation("http", "request"), parameters, "http", "request");
    ComponentWrapper wrapper = mock(ComponentWrapper.class);
    when(wrapper.getParameters()).thenReturn(parameters);
    Map<String, String> connection = new HashMap<>();
    connection.put("protocol", "HTTP");
    connection.put("host", "configured.invalid");
    connection.put("port", "9999");
    when(wrapper.getConfigConnectionParameters()).thenReturn(connection);
    when(wrapper.getConfigParameters())
        .thenReturn(basePath == null ? Collections.emptyMap() : Collections.singletonMap("basePath", basePath));
    when(wrapper.staticParametersAsReadOnlyMap()).thenReturn(Collections.emptyMap());
    when(wrapper.getDefaultSpanName()).thenReturn("request:request");
    ComponentRegistryService registry = mock(ComponentRegistryService.class);
    when(registry.getComponentWrapper(component)).thenReturn(wrapper);
    HttpProcessorComponent processor = new HttpProcessorComponent();
    processor.withComponentRegistryService(registry);
    return new RequestFixture(component, registry, processor, parameters);
  }

  private Event requestEvent() {
    Event event = getEvent();
    Message message = getMessage(null);
    when(event.getMessage()).thenReturn(message);
    return event;
  }

  private static class RequestFixture {
    private final Component component;
    private final ComponentRegistryService registry;
    private final HttpProcessorComponent processor;
    private final Map<String, String> parameters;

    private RequestFixture(Component component, ComponentRegistryService registry, HttpProcessorComponent processor,
        Map<String, String> parameters) {
      this.component = component;
      this.registry = registry;
      this.processor = processor;
      this.parameters = parameters;
    }
  }
}
