---
---

# Terminal Bridge (development preview)

The Terminal Bridge is a small menu-bar application that runs on the register machine, embeds the Java SDK and the Session Host (`:host`), and serves them on `127.0.0.1` so a browser-based POS page can drive a Bilt terminal on the store LAN. The page never sees the terminal's address, certificate or payload passphrase; it talks the Session Protocol (HTTP, Server-Sent Events and WebSocket) to the bridge, and the bridge speaks Nexo over HTTPS to the terminal.

The design is in Notion: *Bilt POS SDK — Terminal Bridge, Session Protocol & JavaScript SDK (Design)*. This page covers the **development-mode** bridge in this repository: configuration from a local file, no pairing, no authentication, macOS packaging only.

> **Development mode.** The bridge in this iteration allows any browser origin by default (`allowedOrigins: ["*"]`), accepts no bearer tokens, and lets terminals run unencrypted with certificate checks off. Use it against development terminals on a trusted network only.

---

## What it does

- Runs the Session Host on `http://127.0.0.1:48333`: `GET /health`, `GET /v1/terminals`, `POST /v1/sessions` and the rest of the Session Protocol. If that port is taken it tries the next ten (`48334`…`48343`) and logs the one it chose. It never binds a LAN interface, even if the config file asks it to.
- Builds a `BiltNexoTerminalClient` per terminal in the config file, using the same builder options documented in the [Integration Guide](integration.html), [Certificate Validation](certificate-validation-setup.html) and [Terminal Security](terminal-security.html), and hands them to the host by `poiId`.
- Admits browser requests only from the origins in `allowedOrigins` (requests without an `Origin` header, such as curl, always pass).
- Shows a menu-bar icon with the listener status, the number of terminals configured and sessions active, and actions to open the config file, reload it, open the logs folder, copy a redacted diagnostics summary, toggle *Start at login*, and quit.
- Reloads the config file when it changes or when *Reload config* is chosen. A broken file keeps the previous configuration in force (or the defaults on first start) and the error shows in the menu.
- Logs to rotating files and to stderr.
- Runs headless: without a system tray (SSH session, CI, server) it stays a foreground process until interrupted.

---

## Install on macOS

Either download `Bilt Terminal Bridge-<version>.dmg` from the *Terminal Bridge (macOS)* workflow artifact on GitHub Actions, or build it locally:

```bash
./gradlew :bridge:jpackage
open bridge/build/jpackage/
```

Open the dmg and drag **Bilt Terminal Bridge** to **Applications**, then launch it. The build is **ad-hoc signed**, not notarized, so the first launch needs *Open* from the right-click menu, or:

```bash
xattr -dr com.apple.quarantine "/Applications/Bilt Terminal Bridge.app"
```

A bridge icon appears in the menu bar; there is no Dock icon. To start it at login, choose **Start at login** from the menu: it writes `~/Library/LaunchAgents/com.bilt.pos.bridge.plist`, nothing more.

Requirements for building: JDK 21 with `jlink` and `jpackage` (Temurin works), Xcode command-line tools for `codesign` and `hdiutil`.

### Verify

```bash
curl http://127.0.0.1:48333/health
```

```json
{"kind":"bridge","hostVersion":"0.30.0","sdkVersion":"0.30.0","protocolVersions":["1"],"terminals":1,"sessions":0}
```

`GET /v1/terminals` lists the configured `poiId`s. If the default port was taken, the bridge log (and the tray's status line) names the port that was bound.

---

## Configuration file

| Platform | Config file | Logs |
|----------|-------------|------|
| macOS | `~/Library/Application Support/Bilt Terminal Bridge/config.json` | `~/Library/Logs/Bilt Terminal Bridge/` |
| Windows | `%APPDATA%\Bilt Terminal Bridge\config.json` | `%LOCALAPPDATA%\Bilt Terminal Bridge\Logs\` |
| Linux | `$XDG_CONFIG_HOME/bilt-terminal-bridge/config.json` | `$XDG_STATE_HOME/bilt-terminal-bridge/logs/` |

The file is created on first start with a commented example (JSON has no comments, so the notes live in a `_comment` field; any key starting with `_` is ignored). Unknown keys are rejected so a typo cannot silently disable a setting.

```json
{
  "port": 48333,
  "allowedOrigins": ["*"],
  "terminals": [
    {
      "poiId": "VictaLane-275839164",
      "host": "192.168.4.108",
      "port": 8443,
      "encryption": false,
      "passphrase": null,
      "keyId": null,
      "trustAll": true,
      "caCertificatePath": null,
      "environment": null
    }
  ]
}
```

| Key | Default | Meaning |
|-----|---------|---------|
| `bindAddress` | `127.0.0.1` | Must be a loopback address; anything else is refused at load time. |
| `port` | `48333` | First port to try; the next ten are fallbacks. Changing it takes effect after a restart. |
| `allowedOrigins` | `["*"]` | Browser origins admitted, e.g. `https://pos.example.com`; a request whose `Origin` is not listed gets `401`. `*` is development-only and logs a warning at start. |
| `terminals[].poiId` | required | The terminal id sessions refer to. Must be unique. |
| `terminals[].host`, `port` | required, `8443` | The terminal's LAN address. |
| `terminals[].encryption` | `false` | Nexo payload encryption. When `true`, `passphrase` and `keyId` are required; `keyVersion` defaults to `0`. |
| `terminals[].trustAll` | `false` | Skip TLS verification (development terminals). Cannot be combined with `caCertificatePath` or `environment`. |
| `terminals[].caCertificatePath`, `environment` | required unless `trustAll` | The Bilt CA PEM and `PRODUCTION` or `STAGING`, which selects the certificate hostname pattern. |

Passphrases never appear in logs, in `toString()` output or in the diagnostics summary.

### Pointing a browser page at the bridge

Until the JavaScript SDK ships, a page can probe the bridge directly:

```javascript
const res = await fetch("http://127.0.0.1:48333/health", { signal: AbortSignal.timeout(400) });
const health = await res.json();   // health.protocolVersions tells the SDK what it can speak
```

Two browser-side caveats in this iteration:

- **CORS response headers are not sent yet.** The Session Host owns the HTTP layer and does not expose a CORS option, so a page on another origin can reach the bridge only where the browser does not enforce CORS for it: a page served from `http://127.0.0.1`/`http://localhost` itself (same-origin), a browser launched with web security disabled for development, or an Electron/WebView host. Adding an `allowedOrigins` CORS option to the host is the follow-up that lifts this.
- Chrome asks once per origin for permission to reach loopback (its Local Network Access check); that preflight needs `Access-Control-Allow-Private-Network: true`, which lands with the same host follow-up.

The JS SDK's `localBridge()` transport will wrap the probe, the install prompt and reconnection.

---

## Development

```bash
./gradlew :bridge:run                                   # tray app from the build output
./gradlew :bridge:run --args="--config /path/to/config.json"
./gradlew :bridge:test
```

`-Dbilt.bridge.dir=/some/dir` pins both the config and the log directory, which keeps a development instance away from the installed one.

### Packaging details

- `org.beryx.runtime` builds a jlink image and a jpackage app image from the non-modular classpath. Modules: `java.base, java.logging, java.net.http, java.xml, java.desktop, java.instrument, java.management, java.naming, java.security.jgss, java.sql, jdk.crypto.ec, jdk.unsupported` (the `instrument`/`management`/`naming`/`jgss` ones are Jetty's). Re-check after dependency changes with `./gradlew :bridge:installDist && jdeps --multi-release 21 --print-module-deps --ignore-missing-deps bridge/build/install/bridge/lib/*.jar`.
- The dmg is produced by `hdiutil create` (task `packageDmg`) rather than jpackage's installer step, whose Finder AppleScript hangs in non-interactive sessions. Both the `.app` and the `.dmg` land in `bridge/build/jpackage/`.
- macOS refuses a bundle version whose first number is zero, so a `0.x.y` SDK version becomes `CFBundleVersion x.y` while `CFBundleShortVersionString` keeps the real version.
- The app is ad-hoc signed (`codesign --force --deep -s -`). Developer ID signing and notarization are a follow-up.
- Launcher JVM options: `-Dapple.awt.UIElement=true` (no Dock icon), `-Dapple.awt.enableTemplateImages=true` (the monochrome tray glyph follows light and dark menu bars), `-Djava.net.preferIPv4Stack=true`.
- The app icon is a placeholder. `bridge/scripts/make-icons.sh` regenerates `bridge.icns` from `bridge.svg` with `qlmanage`, `sips` and `iconutil`.

---

## Known limitations

- **Development mode only**: no pairing, no per-origin tokens, `allowedOrigins: ["*"]` by default, secrets stored in a plain JSON file.
- **macOS packaging only** so far; the application code is platform-neutral (`AppDirs` already knows the Windows and Linux locations) but there is no MSI, deb or rpm, and *Start at login* is implemented for macOS only.
- **Ad-hoc signed, not notarized**; Gatekeeper needs the one-time override above.
- **No CORS response headers** until the Session Host exposes a CORS option (see above); `allowedOrigins` is enforced server-side in the meantime.
- The tray's *Sessions active* count is read from the host's own `/health`, since the host does not expose it programmatically yet.
- No update feed, no cloud configuration.
