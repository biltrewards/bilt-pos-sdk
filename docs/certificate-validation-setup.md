---
---

# Configuring Certificate Validation

This guide shows how to load the Bilt CA certificate and configure the client to validate the terminal's TLS certificate against it. For the broader security model — TLS, payload encryption, and card data handling — see [Terminal Security](./terminal-security.md).

The terminal presents a leaf certificate issued by a Bilt-operated private Certificate Authority. Because public CAs are not in play, your client must be configured with the **Bilt CA certificate** (the merchant or small-merchant-pool CA that signs device leaf certificates) so it can validate the chain. In development you can bypass this with `trustAllCertificates()`, but production deployments must validate.

---

## What you need from Bilt

During onboarding, Bilt provides the **CA certificate** (PEM-encoded) used to sign your terminals' leaf certificates. You can ship this with your application as a classpath resource, mount it into your container, or load it from any source your deployment supports.

The terminal's leaf certificate carries a synthetic Subject Alternative Name (SAN) that encodes the device identity. The domain depends on the environment:

| Environment | SAN format | Example |
|---|---|---|
| Production | `{Model}-{Serial}.live.pos.bilt.com` | `V240m-ABC123.live.pos.bilt.com` |
| Staging | `{Model}-{Serial}.pos.staging.bilt.dev` | `VL1108-910001044.pos.staging.bilt.dev` |

This hostname does not resolve in DNS — your client connects by IP — so the SDK does **not** match the connection address against the certificate. Instead you provide an expected hostname **pattern** (or an `environment`), and the SDK verifies the leaf's SAN against it after validating the chain.

---

## Building the certificate and client

Trust the CA with `trustCertificate(...)` (Java) / `trustedCertificates = ...` (Kotlin), and declare the expected identity with `environment(...)`. These are mutually exclusive with `trustAllCertificates()` — set one approach or the other.

<div class="code-tabs" data-tabs>
  <div class="code-tabs-nav" role="tablist">
    <button class="code-tab-button active" data-tab="java-setup" role="tab" aria-selected="true">Java</button>
    <button class="code-tab-button" data-tab="kotlin-setup" role="tab" aria-selected="false">Kotlin</button>
  </div>
  <div class="code-tab-panel active" data-tab-panel="java-setup">

{% highlight java %}
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.client.BiltTerminalEnvironment;

// Bundled with your application at src/main/resources/certs/merchant-ca.pem
BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
    .endpoint("https://192.168.1.50:8443/nexo")
    .trustCertificateResource("certs/merchant-ca.pem")
    .environment(BiltTerminalEnvironment.PRODUCTION)
    .securityKey(key)
    .build();
{% endhighlight %}

  </div>
  <div class="code-tab-panel" data-tab-panel="kotlin-setup">

{% highlight kotlin %}
import com.bilt.pos.nexo.client.BiltNexoTerminalClient
import com.bilt.pos.nexo.client.BiltTerminalEnvironment

val client = BiltNexoTerminalClient(
    endpoint = "https://192.168.1.50:8443/nexo",
    trustedCertificates = BiltNexoTerminalClient.certificatesFromResource("certs/merchant-ca.pem"),
    environment = BiltTerminalEnvironment.PRODUCTION,
    securityKey = key
)
{% endhighlight %}

  </div>
</div>

`BiltTerminalEnvironment` is a shorthand that fills in the standard hostname pattern for an environment (`PRODUCTION` → `*.live.pos.bilt.com`, `STAGING` → `*.pos.staging.bilt.dev`). Production and staging use **fully separate CA hierarchies**, so the CA you trust must match the environment you select.

---

## Choosing the hostname pattern explicitly

If you prefer to set the pattern directly instead of using `environment(...)`, use `expectedHostnamePattern(...)` (Java) / `expectedHostnamePattern = ...` (Kotlin). The pattern supports a single leading wildcard label — `*.live.pos.bilt.com` matches `V240m-ABC123.live.pos.bilt.com` (one label before the suffix) but not `a.b.live.pos.bilt.com`. A pattern with no wildcard is matched exactly. Matching is case-insensitive.

<div class="code-tabs" data-tabs>
  <div class="code-tabs-nav" role="tablist">
    <button class="code-tab-button active" data-tab="java-pattern" role="tab" aria-selected="true">Java</button>
    <button class="code-tab-button" data-tab="kotlin-pattern" role="tab" aria-selected="false">Kotlin</button>
  </div>
  <div class="code-tab-panel active" data-tab-panel="java-pattern">

{% highlight java %}
BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
    .endpoint("https://192.168.1.50:8443/nexo")
    .trustCertificateResource("certs/merchant-ca.pem")
    .expectedHostnamePattern("*.pos.staging.bilt.dev")
    .build();
{% endhighlight %}

  </div>
  <div class="code-tab-panel" data-tab-panel="kotlin-pattern">

{% highlight kotlin %}
val client = BiltNexoTerminalClient(
    endpoint = "https://192.168.1.50:8443/nexo",
    trustedCertificates = BiltNexoTerminalClient.certificatesFromResource("certs/merchant-ca.pem"),
    expectedHostnamePattern = "*.pos.staging.bilt.dev"
)
{% endhighlight %}

  </div>
</div>

---

## Loading from other sources

The CA certificate can be loaded from any of the following sources. A single PEM file may contain a chain; all certificates found are added as trust anchors.

| Java (builder method) | Kotlin (companion function) | When to use |
|---|---|---|
| `trustCertificateResource(String)` | `certificatesFromResource(String)` | The CA is bundled with the application JAR. |
| `trustCertificate(Path)` / `trustCertificate(File)` | `certificatesFromPath(Path)` | The CA is mounted into the container or written to disk by your deploy tooling. |
| `trustCertificate(InputStream)` | `certificatesFromStream(InputStream)` | The CA is fetched from a secrets manager, S3, or another runtime source. |
| `trustCertificate(X509Certificate)` | (pass `List<X509Certificate>`) | You already hold a parsed certificate. |
| — | `certificatesFromPem(String)` | The CA is loaded from a config value or environment variable. |

<div class="code-tabs" data-tabs>
  <div class="code-tabs-nav" role="tablist">
    <button class="code-tab-button active" data-tab="java-sources" role="tab" aria-selected="true">Java</button>
    <button class="code-tab-button" data-tab="kotlin-sources" role="tab" aria-selected="false">Kotlin</button>
  </div>
  <div class="code-tab-panel active" data-tab-panel="java-sources">

{% highlight java %}
// From a filesystem path
builder.trustCertificate(Path.of("/etc/bilt/merchant-ca.pem"));

// From an arbitrary stream (e.g. a secrets manager response)
try (InputStream in = secretsClient.openCertStream("merchant-ca")) {
    builder.trustCertificate(in);
}

// trustCertificate(...) may be called more than once to add several anchors.
{% endhighlight %}

  </div>
  <div class="code-tab-panel" data-tab-panel="kotlin-sources">

{% highlight kotlin %}
// From a filesystem path
val fromFile = BiltNexoTerminalClient.certificatesFromPath(
    Path.of("/etc/bilt/merchant-ca.pem")
)

// From an arbitrary stream (e.g. a secrets manager response)
val fromStream = secretsClient.openCertStream("merchant-ca").use { stream ->
    BiltNexoTerminalClient.certificatesFromStream(stream)
}

// From an inline PEM string (e.g. an environment variable)
val fromString = BiltNexoTerminalClient.certificatesFromPem(System.getenv("BILT_POS_CA_PEM"))
{% endhighlight %}

  </div>
</div>

---

## Development vs. production

| Setting | Development | Production |
|---|---|---|
| `.trustAllCertificates()` | Allowed for local testing | **Do not use** — bypasses all chain validation |
| CA trust anchor + hostname pattern | Optional | **Required** — must validate against the Bilt CA |

The builder fails fast at `build()` (Java) / construction (Kotlin) on misconfigurations, rather than producing a confusing handshake error later:

- **Trust anchor without a hostname pattern.** A CA validates the chain, but the connection IP can never match the certificate's synthetic SAN, so the handshake would always fail. Set `environment(...)` or `expectedHostnamePattern(...)`.
- **`trustAllCertificates()` combined with a trust anchor or pattern.** This is contradictory — `trustAllCertificates()` disables all verification and would silently override the CA. Pick one.

---

## Verifying the setup

Once configured, send any request to the terminal — the TLS handshake will fail loudly if the certificate cannot be validated. For a low-impact probe, a `DiagnosisRequest` works well: it does not require an active transaction and exercises the full TLS path.

Failures usually surface as a `BiltNexoClientException` wrapping one of:

- **`SSLHandshakeException: PKIX path building failed`** — the leaf certificate is not signed by the CA you loaded. Confirm you have the correct CA from Bilt onboarding, and that it matches the environment (prod and staging use separate CAs).
- **`SSLPeerUnverifiedException: Hostname … not verified`** — the leaf's SAN does not match your expected pattern. Most often this means the pattern/environment is wrong for the device (e.g. using `PRODUCTION` against a `*.pos.staging.bilt.dev` terminal). The reported hostname is the connection IP; the relevant value is the certificate's SAN.

---

## Common pitfalls

- **Wrong environment for the device.** A staging terminal's SAN is under `pos.staging.bilt.dev`, not `live.pos.bilt.com`. Selecting the wrong `environment` (or pattern) rejects an otherwise-valid certificate.
- **Shipping the wrong PEM.** Bilt issues different CAs for different environments. Make sure the bundled file matches the environment you are deploying to.
- **Stale resource cache.** When loading from the classpath, repackaging the JAR without the updated PEM will silently keep using the old one. Verify the file is on the classpath after build.
- **Using `.trustAllCertificates()` in production.** This disables chain validation entirely; the production checklist in [Terminal Security](./terminal-security.md#development-vs-production-checklist) explicitly forbids it.
- **Embedding the leaf certificate instead of the CA.** The SDK expects the **CA** that signs leaf certificates, not an individual device's leaf. Pinning a single leaf will break as soon as the device's certificate is renewed.
