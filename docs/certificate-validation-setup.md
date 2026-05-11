---
---

# Configuring Certificate Validation

This guide shows how to load the Bilt intermediate CA certificate and configure the client to validate the terminal's TLS certificate against it. For the broader security model — TLS, payload encryption, and card data handling — see [Terminal Security](./terminal-security.md).

The terminal presents a leaf certificate issued by a Bilt-operated private Certificate Authority. Because public CAs are not in play, your client must be configured with the **Bilt intermediate CA certificate** so it can validate the chain. In development you can bypass this with `trustAllCertificates()`, but production deployments must validate.

---

## What you need from Bilt

During onboarding, Bilt provides the **intermediate CA certificate** (PEM-encoded) used to sign terminal leaf certificates. You can ship this with your application as a classpath resource, mount it into your container, or load it from any source your deployment supports.

The terminal's leaf certificate has a synthetic Subject Alternative Name of the form `{Model}-{Serial}.live.pos.bilt.com` (e.g. `V240m-ABC123.live.pos.bilt.com`). This hostname does not resolve in DNS — your client connects by IP — so the SDK installs a hostname verifier that accepts any SAN matching this pattern after validating the chain.

---

## Building the certificate and client

`BiltTerminalCertificate` exposes factory methods for the most common loading sources. The result is passed to `.certificate(...)` on the client builder. This is mutually exclusive with `.trustAllCertificates()` — set one or the other.

<div class="code-tabs" data-tabs>
  <div class="code-tabs-nav" role="tablist">
    <button class="code-tab-button active" data-tab="java-setup" role="tab" aria-selected="true">Java</button>
    <button class="code-tab-button" data-tab="kotlin-setup" role="tab" aria-selected="false">Kotlin</button>
  </div>
  <div class="code-tab-panel active" data-tab-panel="java-setup">

{% highlight java %}
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.security.BiltTerminalCertificate;

// Bundled with your application at src/main/resources/certs/bilt-pos-intermediate.pem
BiltTerminalCertificate cert =
    BiltTerminalCertificate.fromClasspath("/certs/bilt-pos-intermediate.pem");

BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
    .endpoint("https://192.168.1.50:8443/nexo")
    .certificate(cert)
    .securityKey(key)
    .build();
{% endhighlight %}

  </div>
  <div class="code-tab-panel" data-tab-panel="kotlin-setup">

{% highlight kotlin %}
import com.bilt.pos.nexo.client.BiltNexoTerminalClient
import com.bilt.pos.nexo.security.BiltTerminalCertificate

val cert = BiltTerminalCertificate.fromClasspath("/certs/bilt-pos-intermediate.pem")

val client = BiltNexoTerminalClient(
    endpoint = "https://192.168.1.50:8443/nexo",
    certificate = cert,
    securityKey = key
)
{% endhighlight %}

  </div>
</div>

---

## Loading from other sources

`BiltTerminalCertificate` accepts a certificate from any of the following sources:

| Factory | Input | When to use |
|---|---|---|
| `fromClasspath(String resource)` | Resource path on the classpath | The CA is bundled with the application JAR. |
| `fromPath(Path path)` | Filesystem path | The CA is mounted into the container or written to disk by your deploy tooling. |
| `fromInputStream(InputStream in)` | Any byte stream | The CA is fetched from a secrets manager, S3, or another runtime source. |
| `fromPem(String pem)` | Inline PEM string | The CA is loaded from a config value or environment variable. |

<div class="code-tabs" data-tabs>
  <div class="code-tabs-nav" role="tablist">
    <button class="code-tab-button active" data-tab="java-sources" role="tab" aria-selected="true">Java</button>
    <button class="code-tab-button" data-tab="kotlin-sources" role="tab" aria-selected="false">Kotlin</button>
  </div>
  <div class="code-tab-panel active" data-tab-panel="java-sources">

{% highlight java %}
// From a filesystem path
BiltTerminalCertificate fromFile =
    BiltTerminalCertificate.fromPath(Path.of("/etc/bilt/bilt-pos-intermediate.pem"));

// From an arbitrary stream (e.g. a secrets manager response)
BiltTerminalCertificate fromStream;
try (InputStream in = secretsClient.openCertStream("bilt-pos-intermediate")) {
    fromStream = BiltTerminalCertificate.fromInputStream(in);
}

// From an inline PEM string (e.g. an environment variable)
BiltTerminalCertificate fromString =
    BiltTerminalCertificate.fromPem(System.getenv("BILT_POS_CA_PEM"));
{% endhighlight %}

  </div>
  <div class="code-tab-panel" data-tab-panel="kotlin-sources">

{% highlight kotlin %}
// From a filesystem path
val fromFile = BiltTerminalCertificate.fromPath(
    Path.of("/etc/bilt/bilt-pos-intermediate.pem")
)

// From an arbitrary stream (e.g. a secrets manager response)
val fromStream = secretsClient.openCertStream("bilt-pos-intermediate").use { stream ->
    BiltTerminalCertificate.fromInputStream(stream)
}

// From an inline PEM string (e.g. an environment variable)
val fromString = BiltTerminalCertificate.fromPem(System.getenv("BILT_POS_CA_PEM"))
{% endhighlight %}

  </div>
</div>

---

## Development vs. production

| Setting | Development | Production |
|---|---|---|
| `.trustAllCertificates()` | Allowed for local testing | **Do not use** — bypasses all chain validation |
| `.certificate(BiltTerminalCertificate)` | Optional | **Required** — must validate against the Bilt intermediate CA |

The two builder methods are mutually exclusive. Calling both will throw at `build()` time.

---

## Verifying the setup

Once configured, send any request to the terminal — the TLS handshake will fail loudly if the certificate cannot be validated. For a low-impact probe, a `DiagnosisRequest` works well: it does not require an active transaction and exercises the full TLS path.

Failures usually surface as one of:

- **`SSLHandshakeException: PKIX path building failed`** — the leaf certificate is not signed by the CA you loaded. Confirm you have the correct intermediate from Bilt onboarding.
- **`SSLPeerUnverifiedException: Hostname … not verified`** — the leaf's SAN does not match the expected `{Model}-{Serial}.live.pos.bilt.com` pattern. This typically means the device is misconfigured; contact Bilt support.

---

## Common pitfalls

- **Shipping the wrong PEM.** Bilt issues different intermediates for different environments. Make sure the bundled file matches the environment you are deploying to.
- **Stale resource cache.** When loading from the classpath, repackaging the JAR without the updated PEM will silently keep using the old one. Verify the file is on the classpath after build.
- **Using `.trustAllCertificates()` in production.** This disables chain validation entirely; the production checklist in [Terminal Security](./terminal-security.md#development-vs-production-checklist) explicitly forbids it.
- **Embedding the leaf certificate instead of the intermediate.** The SDK expects the **CA** that signs leaf certificates, not an individual device's leaf. Pinning a single leaf will break as soon as the device's certificate is renewed.
