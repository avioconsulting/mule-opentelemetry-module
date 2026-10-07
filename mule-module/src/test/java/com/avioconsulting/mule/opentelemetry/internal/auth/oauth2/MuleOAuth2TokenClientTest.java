package com.avioconsulting.mule.opentelemetry.internal.auth.oauth2;

import com.avioconsulting.mule.opentelemetry.api.config.exporter.auth.OAuth2Auth;
import org.junit.Test;
import org.mule.runtime.http.api.HttpService;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientFactory;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MuleOAuth2TokenClientTest {
  private final HttpService service = mock(HttpService.class);
  private final HttpClientFactory factory = mock(HttpClientFactory.class);
  private final HttpClient http = mock(HttpClient.class);
  private HttpRequest request;
  private HttpRequestOptions options;

  private MuleOAuth2TokenClient client(OAuth2Auth auth, int status, String body) {
    when(service.getClientFactory()).thenReturn(factory);
    when(factory.create(any(HttpClientConfiguration.class))).thenReturn(http);
    HttpResponse response = HttpResponse.builder().statusCode(status)
        .entity(new ByteArrayHttpEntity(body.getBytes(StandardCharsets.UTF_8))).build();
    when(http.sendAsync(any(HttpRequest.class), any(HttpRequestOptions.class))).thenAnswer(invocation -> {
      request = invocation.getArgument(0);
      options = invocation.getArgument(1);
      return CompletableFuture.completedFuture(response);
    });
    return new MuleOAuth2TokenClient(service, auth);
  }

  private OAuth2Auth configuration() {
    return new OAuth2Auth("https://localhost/token", "id:+", "secret &", null);
  }

  @Test
  public void encodesPostCredentialsAndOmitsAbsentScope() throws Exception {
    try (MuleOAuth2TokenClient client = client(configuration(), 200,
        "{\"access_token\":\"dummy\",\"token_type\":\"BeArEr\",\"expires_in\":120,\"ignored\":true}")) {
      assertThat(client.acquire().getValue()).isEqualTo("dummy");
      assertThat(request.getMethod()).isEqualTo("POST");
      assertThat(new String(request.getEntity().getBytes(), StandardCharsets.UTF_8))
          .isEqualTo("grant_type=client_credentials&client_id=id%3A%2B&client_secret=secret+%26");
      assertThat(options.isFollowsRedirect()).isFalse();
      assertThat(options.getResponseTimeout()).isEqualTo(8000);
    }
    verify(http).start();
    verify(http).stop();
  }

  @Test
  public void basicEncodesEachCredentialBeforeBase64() throws Exception {
    OAuth2Auth config = new OAuth2Auth("https://localhost/token", "id:+", "secret &", "one two")
        .setCredentialPlacement(OAuth2Auth.CredentialPlacement.CLIENT_SECRET_BASIC);
    try (MuleOAuth2TokenClient client = client(config, 200,
        "{\"access_token\":\"dummy\",\"token_type\":\"bearer\",\"expires_in\":120}")) {
      client.acquire();
      assertThat(request.getHeaderValue("Authorization")).isEqualTo("Basic " + Base64.getEncoder()
          .encodeToString("id%3A%2B:secret+%26".getBytes(StandardCharsets.UTF_8)));
      assertThat(new String(request.getEntity().getBytes(), StandardCharsets.UTF_8))
          .isEqualTo("grant_type=client_credentials&scope=one+two");
    }
  }

  @Test
  public void rejectsInvalidFieldsAndNeverExposesBodies() {
    for (String body : new String[] { "secret response", "{}",
        "{\"access_token\":\"\",\"token_type\":\"bearer\",\"expires_in\":10}",
        "{\"access_token\":\"secret\",\"token_type\":\"mac\",\"expires_in\":10}",
        "{\"access_token\":\"secret\",\"token_type\":\"bearer\",\"expires_in\":-1}",
        "{\"access_token\":\"secret\",\"token_type\":\"bearer\",\"expires_in\":1.5}" }) {
      try (MuleOAuth2TokenClient client = client(configuration(), 200, body)) {
        assertThatThrownBy(client::acquire).isInstanceOf(OAuth2AuthenticationException.class)
            .hasMessageNotContaining("secret").hasCause(null);
      }
    }
  }

  @Test
  public void refusesRedirectAndOversizedResponse() {
    try (MuleOAuth2TokenClient client = client(configuration(), 302, "secret")) {
      assertThatThrownBy(client::acquire).hasMessageContaining("HTTP").hasMessageNotContaining("secret");
    }
    try (MuleOAuth2TokenClient client = client(configuration(), 200,
        new String(new char[65537]).replace('\0', 'x'))) {
      assertThatThrownBy(client::acquire).hasMessageContaining("INVALID_RESPONSE");
    }
  }

  @Test
  public void closeIsIdempotentAndRejectsAcquisition() {
    MuleOAuth2TokenClient client = client(configuration(), 200, "{}");
    client.close();
    client.close();
    assertThatThrownBy(client::acquire).hasMessageContaining("CLOSED");
    verify(http, times(1)).stop();
  }

  @Test
  public void boundsWaitingAndClosesLateResponse() throws Exception {
    OAuth2Auth auth = configuration();
    java.lang.reflect.Field timeout = OAuth2Auth.class.getDeclaredField("requestTimeoutMillis");
    timeout.setAccessible(true);
    timeout.setInt(auth, 10);
    CompletableFuture<HttpResponse> response = new CompletableFuture<>();
    MuleOAuth2TokenClient client = client(auth, 200, "{}");
    when(http.sendAsync(any(HttpRequest.class), any(HttpRequestOptions.class))).thenReturn(response);
    assertThatThrownBy(client::acquire).hasMessageContaining("TIMEOUT");
    java.io.InputStream input = spy(new java.io.ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
    org.mule.runtime.http.api.domain.entity.HttpEntity entity = mock(
        org.mule.runtime.http.api.domain.entity.HttpEntity.class);
    when(entity.getContent()).thenReturn(input);
    response.complete(HttpResponse.builder().statusCode(200).entity(entity).build());
    verify(input).close();
    client.close();
  }

  @Test
  public void closesClientWhenStartFails() {
    when(service.getClientFactory()).thenReturn(factory);
    when(factory.create(any(HttpClientConfiguration.class))).thenReturn(http);
    doThrow(new IllegalStateException("secret")).when(http).start();
    assertThatThrownBy(() -> new MuleOAuth2TokenClient(service, configuration()))
        .hasMessageNotContaining("secret").hasCause(null);
    verify(http).stop();
  }
}
