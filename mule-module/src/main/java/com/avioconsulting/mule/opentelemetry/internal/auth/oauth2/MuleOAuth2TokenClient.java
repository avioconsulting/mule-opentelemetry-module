package com.avioconsulting.mule.opentelemetry.internal.auth.oauth2;

import com.avioconsulting.mule.opentelemetry.api.config.exporter.auth.OAuth2Auth;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mule.runtime.http.api.HttpService;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.request.HttpRequestBuilder;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import static com.avioconsulting.mule.opentelemetry.internal.auth.oauth2.OAuth2AuthenticationException.Category.*;

/**
 * Mule HTTP service client with default validating TLS and no implicit
 * application proxy.
 */
public final class MuleOAuth2TokenClient implements OAuth2TokenClient {
  static final int MAX_RESPONSE_BYTES = 65536;
  private final HttpClient client;
  private final OAuth2Auth configuration;
  private final ExecutorService parser = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<>(1), r -> {
        Thread thread = new Thread(r, "opentelemetry-token-response");
        thread.setDaemon(true);
        return thread;
      }, new ThreadPoolExecutor.AbortPolicy());
  private boolean closed;
  private Request active;

  public MuleOAuth2TokenClient(HttpService service, OAuth2Auth configuration) {
    configuration.validate();
    this.configuration = configuration;
    client = service.getClientFactory().create(new HttpClientConfiguration.Builder()
        .setName("opentelemetry-oauth2").setMaxConnections(1).setStreaming(true)
        .setResponseBufferSize(8192).setDecompress(false).build());
    try {
      client.start();
    } catch (RuntimeException e) {
      parser.shutdownNow();
      try {
        client.stop();
      } catch (RuntimeException ignored) {
        /* No sensitive causes. */ }
      throw new OAuth2AuthenticationException(TRANSPORT);
    }
  }

  @Override
  public OAuth2AccessToken acquire() {
    Request request = new Request();
    synchronized (this) {
      if (closed) {
        throw new OAuth2AuthenticationException(CLOSED);
      }
      if (active != null) {
        throw new OAuth2AuthenticationException(TRANSPORT);
      }
      active = request;
    }
    try {
      CompletableFuture<HttpResponse> response = client.sendAsync(tokenRequest(), HttpRequestOptions.builder()
          .responseTimeout(configuration.getRequestTimeoutMillis()).followsRedirect(false).build());
      // A response can complete before a streaming body arrives. Consume off the
      // caller so
      // its overall budget still applies. Single-flight bounds this to one parsing
      // operation.
      CompletableFuture<OAuth2AccessToken> parsed = response.thenCompose(r -> {
        InputStream stream = r.getEntity().getContent();
        if (!request.setStream(stream)) {
          closeStream(stream);
          throw new OAuth2AuthenticationException(CLOSED);
        }
        return CompletableFuture.supplyAsync(() -> parseResponse(r, stream), parser);
      });
      return parsed.get(configuration.getRequestTimeoutMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OAuth2AuthenticationException(INTERRUPTED);
    } catch (TimeoutException e) {
      throw new OAuth2AuthenticationException(TIMEOUT);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof OAuth2AuthenticationException) {
        throw (OAuth2AuthenticationException) e.getCause();
      }
      throw new OAuth2AuthenticationException(TRANSPORT);
    } catch (RuntimeException e) {
      if (e instanceof OAuth2AuthenticationException) {
        throw e;
      }
      throw new OAuth2AuthenticationException(TRANSPORT);
    } finally {
      request.close();
      synchronized (this) {
        if (active == request) {
          active = null;
        }
      }
    }
  }

  private HttpRequest tokenRequest() {
    String form = "grant_type=client_credentials";
    HttpRequestBuilder builder = HttpRequest.builder().uri(configuration.getTokenUrl()).method("POST")
        .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        .addHeader("Accept", "application/json");
    if (configuration.getCredentialPlacement() == OAuth2Auth.CredentialPlacement.CLIENT_SECRET_BASIC) {
      String credentials = encode(configuration.getClientId()) + ":" + encode(configuration.getClientSecret());
      builder.addHeader("Authorization", "Basic " + Base64.getEncoder()
          .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
    } else {
      form += "&client_id=" + encode(configuration.getClientId())
          + "&client_secret=" + encode(configuration.getClientSecret());
    }
    if (configuration.getScopes() != null && !configuration.getScopes().trim().isEmpty()) {
      form += "&scope=" + encode(configuration.getScopes());
    }
    return builder.entity(new ByteArrayHttpEntity(form.getBytes(StandardCharsets.UTF_8))).build();
  }

  private static String encode(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8");
    } catch (UnsupportedEncodingException impossible) {
      throw new IllegalStateException("UTF-8 unavailable");
    }
  }

  private OAuth2AccessToken parseResponse(HttpResponse response, InputStream stream) {
    try (InputStream input = stream) {
      if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
        throw new OAuth2AuthenticationException(HTTP);
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int count;
      while ((count = input.read(buffer)) != -1) {
        if (output.size() + count > MAX_RESPONSE_BYTES) {
          throw new OAuth2AuthenticationException(INVALID_RESPONSE);
        }
        output.write(buffer, 0, count);
      }
      JsonElement element = JsonParser.parseString(new String(output.toByteArray(), StandardCharsets.UTF_8));
      if (!element.isJsonObject()) {
        throw new OAuth2AuthenticationException(INVALID_RESPONSE);
      }
      JsonObject object = element.getAsJsonObject();
      String token = string(object, "access_token");
      if (!"bearer".equalsIgnoreCase(string(object, "token_type"))) {
        throw new OAuth2AuthenticationException(INVALID_RESPONSE);
      }
      JsonElement expires = object.get("expires_in");
      if (expires == null || !expires.isJsonPrimitive() || !expires.getAsJsonPrimitive().isNumber()) {
        throw new OAuth2AuthenticationException(INVALID_RESPONSE);
      }
      long seconds = expires.getAsBigDecimal().longValueExact();
      return new OAuth2AccessToken(token, seconds);
    } catch (IOException e) {
      throw new OAuth2AuthenticationException(TRANSPORT);
    } catch (RuntimeException e) {
      if (e instanceof OAuth2AuthenticationException) {
        throw e;
      }
      throw new OAuth2AuthenticationException(INVALID_RESPONSE);
    }
  }

  private static String string(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new OAuth2AuthenticationException(INVALID_RESPONSE);
    }
    return value.getAsString();
  }

  @Override
  public void close() {
    Request request;
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
      request = active;
    }
    if (request != null) {
      request.close();
    }
    parser.shutdownNow();
    try {
      client.stop();
    } catch (RuntimeException e) {
      throw new OAuth2AuthenticationException(TRANSPORT);
    }
  }

  private static void closeStream(InputStream stream) {
    if (stream != null) {
      try {
        stream.close();
      } catch (IOException ignored) {
        /* Never retain sensitive causes. */ }
    }
  }

  private static final class Request {
    private boolean closed;
    private InputStream stream;

    synchronized boolean setStream(InputStream stream) {
      if (closed) {
        return false;
      }
      this.stream = stream;
      return true;
    }

    void close() {
      InputStream input;
      synchronized (this) {
        if (closed) {
          return;
        }
        closed = true;
        input = stream;
        stream = null;
      }
      closeStream(input);
      // Keep the response completion handler attached: cancelling its future can
      // discard
      // a late response without transferring stream ownership to this client.
    }
  }
}
