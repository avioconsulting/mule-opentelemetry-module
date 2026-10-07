package com.avioconsulting.mule.opentelemetry.test.util.auth;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Base64;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Dummy local identity shared by token servers and mutually authenticated
 * collectors.
 */
public final class AuthenticationTlsFixture implements AutoCloseable {
  public static final String PASSWORD = "dummy-password";
  private final KeyStore keys;
  private final Path directory;
  private final Path certificate;
  private final Path privateKey;

  public AuthenticationTlsFixture() throws Exception {
    keys = KeyStore.getInstance("PKCS12");
    try (InputStream input = getClass().getResourceAsStream("/authentication/localhost.p12")) {
      keys.load(input, PASSWORD.toCharArray());
    }
    Files.createDirectories(Paths.get("target"));
    directory = Files.createTempDirectory(Paths.get("target"), "oauth-e2e-tls-");
    certificate = directory.resolve("certificate.pem");
    privateKey = directory.resolve("key.pem");
    Files.write(certificate, pem("CERTIFICATE", keys.getCertificate("localhost").getEncoded()));
    Files.write(privateKey, pem("PRIVATE KEY", keys.getKey("localhost", PASSWORD.toCharArray()).getEncoded()));
  }

  public static String keyStorePath() throws Exception {
    return Paths.get(AuthenticationTlsFixture.class.getResource("/authentication/localhost.p12").toURI())
        .toAbsolutePath().toString();
  }

  public String certificatePath() {
    return certificate.toAbsolutePath().toString();
  }

  public String privateKeyPath() {
    return privateKey.toAbsolutePath().toString();
  }

  public byte[] certificate() throws Exception {
    return Files.readAllBytes(certificate);
  }

  public byte[] privateKey() throws Exception {
    return Files.readAllBytes(privateKey);
  }

  public KeyManagerFactory keyManagers() throws Exception {
    KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    managers.init(keys, PASSWORD.toCharArray());
    return managers;
  }

  public SSLContext serverContext() throws Exception {
    TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(keys);
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(keyManagers().getKeyManagers(), trust.getTrustManagers(), null);
    return context;
  }

  private static byte[] pem(String type, byte[] bytes) {
    return ("-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[] { '\n' })
        .encodeToString(bytes) + "\n-----END " + type + "-----\n").getBytes(StandardCharsets.US_ASCII);
  }

  @Override
  public void close() throws Exception {
    Files.deleteIfExists(privateKey);
    Files.deleteIfExists(certificate);
    Files.deleteIfExists(directory);
  }
}
