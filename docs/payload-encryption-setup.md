---
---

# Configuring Payload Encryption

This guide shows how to provision the encryption passphrase and attach a `SecurityKey` to the client. For the broader security model — TLS, payload encryption, and card data handling — see [Terminal Security](./terminal-security.md).

The terminal and the Sale System must be provisioned with the **same passphrase**. The passphrase is used to derive AES-256 and HMAC-SHA256 keys via PBKDF2, which encrypt and authenticate every Nexo message exchanged with the terminal.

---

## What you need from Bilt

During onboarding, Bilt provides three values that correspond to the configuration assigned to your terminals:

| Value | Description |
|---|---|
| `passphrase` | The shared secret used to derive encryption and HMAC keys. Treat it as a credential — do not commit it to source control. |
| `keyIdentifier` | Identifies which key the terminal should use for decryption. |
| `keyVersion` | Numeric version of the key, allowing for key rotation. Defaults to `0` if not specified. |

The terminal pulls its matching configuration from the Bilt Terminal Management Service (BTMS) at onboarding. If the values on the client and the device do not match, the terminal will reject the request.

---

## Building the SecurityKey and client

Once `securityKey` is set on the client, every outgoing request is encrypted with CMS EnvelopedData and signed with HMAC-SHA256, and incoming responses are decrypted automatically.

<div class="code-tabs" data-tabs>
  <div class="code-tabs-nav" role="tablist">
    <button class="code-tab-button active" data-tab="java-setup" role="tab" aria-selected="true">Java</button>
    <button class="code-tab-button" data-tab="kotlin-setup" role="tab" aria-selected="false">Kotlin</button>
  </div>
  <div class="code-tab-panel active" data-tab-panel="java-setup">

{% highlight java %}
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.security.SecurityKey;

SecurityKey key = SecurityKey.builder()
    .passphrase(System.getenv("BILT_TERMINAL_PASSPHRASE"))
    .keyIdentifier("myTerminal")
    .keyVersion(0)
    .build();

BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
    .endpoint("https://192.168.1.50:8443/nexo")
    .securityKey(key)
    .build();
{% endhighlight %}

The `build()` call validates that `passphrase` and `keyIdentifier` are non-empty.

  </div>
  <div class="code-tab-panel" data-tab-panel="kotlin-setup">

{% highlight kotlin %}
import com.bilt.pos.nexo.client.BiltNexoTerminalClient
import com.bilt.pos.nexo.security.SecurityKey

val key = SecurityKey(
    passphrase = System.getenv("BILT_TERMINAL_PASSPHRASE"),
    keyIdentifier = "myTerminal",
    keyVersion = 0
)

val client = BiltNexoTerminalClient(
    endpoint = "https://192.168.1.50:8443/nexo",
    securityKey = key
)
{% endhighlight %}

`SecurityKey` is a regular constructor with a default `keyVersion` of `0`. The `init` block validates that `passphrase` and `keyIdentifier` are non-blank. You can check whether a client is encrypting traffic via `client.isEncrypted`.

  </div>
</div>

---

## Loading the passphrase

Do not hardcode the passphrase. Load it from an environment variable, a secrets manager, or a configuration system that your deployment environment already trusts. A few common patterns:

| Source | Notes |
|---|---|
| Environment variable | Simple for containers; ensure variables are not logged. |
| Secrets manager (AWS Secrets Manager, GCP Secret Manager, Vault, etc.) | Preferred for production; supports rotation and audit. |
| Encrypted config file | Acceptable if the decryption key is itself sourced from a managed secret. |

---

## Key rotation

`keyVersion` lets you rotate the passphrase without coordinated downtime. The terminal can be provisioned with multiple key versions in BTMS; bump the `keyVersion` on the client once the new key is active for the device.

<div class="code-tabs" data-tabs>
  <div class="code-tabs-nav" role="tablist">
    <button class="code-tab-button active" data-tab="java-rotate" role="tab" aria-selected="true">Java</button>
    <button class="code-tab-button" data-tab="kotlin-rotate" role="tab" aria-selected="false">Kotlin</button>
  </div>
  <div class="code-tab-panel active" data-tab-panel="java-rotate">

{% highlight java %}
SecurityKey rotatedKey = SecurityKey.builder()
    .passphrase(newPassphrase)
    .keyIdentifier("myTerminal")
    .keyVersion(1)
    .build();
{% endhighlight %}

  </div>
  <div class="code-tab-panel" data-tab-panel="kotlin-rotate">

{% highlight kotlin %}
val rotatedKey = SecurityKey(
    passphrase = newPassphrase,
    keyIdentifier = "myTerminal",
    keyVersion = 1
)
{% endhighlight %}

  </div>
</div>

Coordinate rotation timing with Bilt so the new version is active on the device before clients begin using it.

---

## Verifying the setup

After wiring up the `SecurityKey`, send a `DiagnosisRequest` to confirm the terminal is out of maintenance mode and accepting encrypted traffic. `DiagnosisRequest` is the one request type the terminal will respond to even when unencrypted, so it is the safest probe to confirm that:

1. The terminal has retrieved its configuration from BTMS.
2. Your client and the device agree on `keyIdentifier` / `keyVersion`.
3. The passphrase derives matching keys on both ends.

If the terminal returns a `DeviceOut` error, it is still waiting for its configuration — see [Terminal Security › Passphrase Provisioning](./terminal-security.md#passphrase-provisioning).

---

## Common pitfalls

- **Mismatched passphrase.** A single character difference produces decryption failures that look like generic protocol errors. Re-check the value provided by Bilt.
- **Wrong `keyIdentifier`.** The device uses this to pick which key to decrypt with; an incorrect identifier will be rejected even if the passphrase is correct.
- **Production device without a `SecurityKey`.** Production terminals reject unencrypted requests (except `DiagnosisRequest`). If you see consistent rejections in production, confirm `securityKey(...)` is set on the builder.
- **Logging the passphrase.** `SecurityKey.toString()` deliberately omits the passphrase. Avoid logging the object yourself in a way that exposes it.
