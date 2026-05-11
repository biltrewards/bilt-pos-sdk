# Bilt Terminal Security

This document describes the security architecture for communication between a POS register (Sale System) and a Bilt Terminal Application (BTA). It covers transport-layer security (TLS), payload-level encryption, card data handling, and the differences between communication channels.

---

## Security Overview

Communication between the register and the terminal is protected by up to two independent security layers:

1. **Transport Security (TLS)** — Encrypts the network connection when communicating over HTTPS.
2. **Payload Encryption (CMS + HMAC)** — Encrypts and authenticates the Nexo message body itself, independent of the transport.

When communicating over **HTTPS**, both layers are active. When communicating over **USB serial**, there is no network transport, so **payload encryption is the sole security layer**.

---

## Transport Security (TLS)

The terminal runs an HTTPS server on **port 8443**. Because POS terminals operate on local networks and are accessed by IP address (not a publicly resolvable hostname), standard public CA certificates cannot be used. Instead, terminals are issued certificates from a **Bilt-operated private Certificate Authority**.

### Certificate Format

Each terminal certificate uses a synthetic hostname as its Subject Alternative Name (SAN):

```
{Model}-{Serial}.live.pos.bilt.com
```

For example: `V240m-ABC123.live.pos.bilt.com`. This hostname does not resolve in DNS — it serves as a device identifier.

### Configuring Your Trust Store

During onboarding, Bilt provides your integration with the appropriate **intermediate CA certificate**. Your register application's trust store must be configured with this certificate. When connecting to a terminal:

1. Connect to the terminal's local IP over HTTPS (e.g., `https://192.168.1.50:8443`).
2. The terminal presents its leaf certificate.
3. Your client validates the certificate chain against the trusted intermediate CA.
4. Optionally, validate the SAN matches the expected `{Model}-{Serial}.live.pos.bilt.com` pattern.
5. The TLS handshake completes and the connection is established.

For a full walkthrough — including Kotlin examples and supported certificate loading sources — see [Configuring Certificate Validation](./certificate-validation-setup.md).

### Development Mode

During development, you can bypass certificate validation using `trustAllCertificates()` on the SDK client builder. **This must not be used in production.**

```java
BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
    .endpoint("https://<device-ip>:8443/nexo")
    .trustAllCertificates() // Development only
    .build();
```

---

## Payload Encryption

In addition to (or instead of) TLS, the Nexo message payload is encrypted and authenticated using the `SecurityKey` mechanism. **This is required for all production terminals** — production devices will reject unencrypted requests (with the exception of `DiagnosticRequest`)

### How It Works

When a `SecurityKey` is configured on the client, the SDK:

- **Encrypts** every outgoing request using **CMS EnvelopedData** with **AES-256-CBC**. A fresh session key is generated per message and wrapped using **AES Key Wrap**.
- **Authenticates** the message with **HMAC-SHA256** via a CMS `SecurityTrailer`, ensuring integrity and preventing tampering.
- **Decrypts** incoming responses automatically. If the terminal responds with an unencrypted message (e.g., certain error conditions), the SDK handles it gracefully. Note, however, that unencrypted responses will not be accepted unless they are errors or response to `DiagnoticRequest`.

### SecurityKey Configuration

The `SecurityKey` requires three values:

| Parameter | Description |
|---|---|
| `passphrase` | The shared secret used to derive encryption and HMAC keys. |
| `keyIdentifier` | Identifies which key the terminal should use for decryption. |
| `keyVersion` | The version of the key, allowing for key rotation. |

```java
SecurityKey key = SecurityKey.builder()
    .passphrase("<your-passphrase>")
    .keyIdentifier("<your-key-identifier>")
    .keyVersion(0)
    .build();

BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
    .endpoint("https://<device-ip>:8443/nexo")
    .securityKey(key)
    .build();
```

For a full walkthrough — including Kotlin examples, passphrase loading patterns, and key rotation — see [Configuring Payload Encryption](./payload-encryption-setup.md).

### Passphrase Provisioning

The encryption passphrase (and other security configuration) is not hardcoded on the device. Instead, the terminal **pulls its configuration from the Bilt Terminal Management Service (BTMS)** at onboarding. Configuration can be set at multiple levels — company, merchant, store, or individual device — giving flexibility for different deployment scenarios.

Until the terminal successfully retrieves its passphrase configuration, it enters **maintenance mode** and cannot process transactions. During maintenance mode, most requests will receive a `DeviceOut` error response. The `DiagnosisRequest` remains available and can be used to check whether the terminal has completed its configuration and is ready to process requests.

Your integration's `SecurityKey` values are provided by Bilt during onboarding and correspond to the configuration assigned to your terminals.

---

## Device Authentication (Backend Services)
 
The terminal authenticates to Bilt backend services — including the Bilt Terminal Management Service (BTMS) — using **OAuth 2.0 client credentials**.
 
### How It Works
 
1. Each terminal is provisioned with a `client_id` and `client_secret`.
2. When the terminal needs to communicate with a Bilt service (e.g., to pull configuration), it exchanges its credentials for a **short-lived access token** (JWT) via the OAuth 2.0 token endpoint.
3. The terminal includes this token in subsequent API requests. The backend validates the JWT before processing the request.

### Security Properties
 
- **Short-lived tokens** limit the impact of a leaked token — even if intercepted, it expires quickly and cannot be reused indefinitely.
- **JWT claims and scopes** allow fine-grained authorization at the backend, so different terminals or merchants can be granted different levels of access without additional lookup.
- **Credential revocation** If credentials are disabled, the devices using those credentials lose access to backend services, and any issued certificates become operationally useless even if still cryptographically valid.

### Relevance to Integrators
 
As an integrator, you do not interact with the device-to-backend authentication directly — it is managed between the terminal and Bilt services. However, it is useful to understand this model because:
 
- If a terminal cannot authenticate to the BTMS, it will not receive its configuration (including the encryption passphrase) and will remain in **maintenance mode**.
- Credential provisioning is handled during device onboarding. If you are managing terminal deployment, coordinate with Bilt to ensure credentials are correctly provisioned before the terminal is expected to go live.

---

## Card Data and PAN Handling

The terminal handles sensitive card data with strict controls to minimize PCI scope for all parties.

### PCI-Scoped Cards (Standard Payment Cards)

For standard payment cards (Visa, Mastercard, etc.), the terminal **never exposes the full PAN in plain text**. Instead:

- The **masked PAN** (e.g., `1234 12** **** 1234`) is returned in the response for display and receipt purposes.
- If full card data is needed for off-terminal processing, it is returned as **encrypted PAN** in the `ProtectedCardData` field. This encryption is performed by the device itself. **Bilt does not decrypt this data and cannot access the plain-text PAN.** The encrypted PAN is intended to be passed through to the payment acquirer as-is, ensuring that neither Bilt nor the integrator widens their PCI scope by handling plain-text card data.
- Whether encrypted PAN is included in responses is configured per integration by Verifone coordinatet through Bilt, based on the processing model. If your integration processes payments off-terminal and requires `ProtectedCardData`, coordinate this with Bilt during onboarding.

### Non-PCI Cards (Custom / Private-Label Cards)

For cards that fall outside standard PCI scope (e.g., certain private-label or custom cards), the full PAN may be returned in plain text. This requires explicit allowlisting configured by Verifone on the terminal, coordinated through Bilt. Even in this case, the data is protected in transit by TLS and/or payload encryption.

### Integrator Responsibilities

- **Do not store full PAN data** unless your systems are PCI-DSS certified for that level of cardholder data storage.
- **Pass `ProtectedCardData` through to your acquirer unchanged** — do not attempt to decrypt it.
- Follow PCI-DSS requirements for any sensitive card data your system handles, including masked PAN on receipts.

---

## Communication Channels

The register can communicate with the terminal over two channels. The security implications differ:

### HTTPS (Network)

- **Transport:** TLS (port 8443, private CA certificate).
- **Payload:** CMS encryption + HMAC (via `SecurityKey`).
- **Both layers active.** TLS protects the network connection; payload encryption protects the message content.

### USB Serial

- **Transport:** None — USB serial is a direct physical connection with no TLS.
- **Payload:** CMS encryption + HMAC (via `SecurityKey`).
- **Payload encryption is the only security layer.** This encryption is enforced in production, the device responds with error for any messages not encrypted with then exception of `DiagnosticRequest`.


---

## Development vs. Production Checklist

Before moving to production, verify that your integration meets these requirements:

| Requirement | Development | Production |
|---|---|---|
| Payload encryption (`SecurityKey`) | Optional | **Required** — terminals reject unencrypted requests |
| TLS certificate validation | Can use `trustAllCertificates()` | **Required** — must validate against the provided intermediate CA |
| Trust store configured | Not required | **Required** — intermediate CA certificate installed |
| SecurityKey values | May use test credentials | **Required** — production credentials from Bilt onboarding |
| USB serial encryption | Optional | **Required** — `SecurityKey` must be configured |
| PAN data storage | Follow PCI-DSS | Follow PCI-DSS — do not store full PAN |