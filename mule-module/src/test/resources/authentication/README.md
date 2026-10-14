# Dummy local TLS fixture

`localhost.p12` contains a self-signed localhost RSA key/certificate used only by local authentication tests. Password: `dummy-password`. SANs: `localhost`, `127.0.0.1`. It contains no production credentials. Generated with JDK keytool, PKCS12, RSA 2048, validity 36500 days from 2026-10-07.

Collectors explicitly trust this certificate; the Mule token-transport fixture temporarily points the JVM trust store at this file and restores the original properties. No certificate validation is disabled.
