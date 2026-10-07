---
---

# Terminal Bridge — Setup Guide

The Terminal Bridge is a small menu-bar application that runs on the register machine, embeds the Java SDK and the Session Host (`:host`), and serves them on `127.0.0.1` so a browser-based POS page can drive a Bilt terminal on the store LAN. It bridges exactly one POS to exactly one terminal (POI): the terminal is configured when the bridge starts, and whatever `poiId` the page sends is passed through as the Nexo `POIID` of the messages to that terminal rather than used to pick one. The page never sees the terminal's address, certificate or payload passphrase; it talks the Session Protocol (HTTP, Server-Sent Events and WebSocket) to the bridge, and the bridge speaks Nexo over HTTPS to the terminal. The page side is the [JavaScript & React SDK](./javascript-sdk-integration.md); the wire contract is the [Session Protocol reference](./session-protocol-reference.html).

The design is in Notion: *Bilt POS SDK — Terminal Bridge, Session Protocol & JavaScript SDK (Design)*. This page covers the **development-mode** bridge in this repository: configuration from a local file, no pairing, no authentication, macOS packaging only.

> **Development mode.** The bridge in this iteration allows any browser origin by default (`allowedOrigins: ["*"]`), accepts no bearer tokens, and lets the terminal run unencrypted with certificate checks off. Use it against development terminals on a trusted network only.

---

## What it does

- Runs the Session Host on `http://127.0.0.1:48333`: `GET /health`, `GET /v1/terminal`, `POST /v1/sessions` and the rest of the Session Protocol. If that port is taken it tries the next ten (`48334`…`48343`) and logs the one it chose. It never binds a LAN interface, even if the config file asks it to.
- Builds one `BiltNexoTerminalClient` for the terminal in the config file, using the same builder options documented in the [Integration Guide](integration.html), [Certificate Validation](certificate-validation-setup.html) and [Terminal Security](terminal-security.html), and hands it to the host. Every terminal session and session-less device operation runs on it; the `poiId` a request names only fills the Nexo header, and the host sends `bilt-session-host` when the request names none.
- Admits browser requests only from the origins in `allowedOrigins` (requests without an `Origin` header, such as curl, always pass).
- Shows a menu-bar icon with the listener status, whether a terminal is configured, the number of sessions active, and actions to open the config file, reload it, open the logs folder, copy a redacted diagnostics summary, toggle *Start at login*, and quit.
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

### First run

On the first start the bridge

1. creates its configuration file (see [Configuration file](#configuration-file)) with a commented example terminal you replace with yours, and its log folder;
2. binds `127.0.0.1:48333`, or the next free port up to `48343`, and writes the port it chose to the log and the menu's status line;
3. starts serving `/health` at once. Without a `terminal` in the config file `local` sessions (basket, member, context and widgets without a terminal) still work, which is enough to bring a register page up.

Then:

1. Open the config file from the menu (**Open config**), replace the example terminal with yours (LAN `host` and `port`, and either `trustAll: true` for a lab device or the Bilt CA and environment), save, and choose **Reload config** or wait for the file watcher. The menu shows whether a terminal is configured.
2. Run [Verify](#verify) below.
3. Open the register page. The JavaScript SDK finds the bridge on its own: `BiltPos.connect(localBridge())`, or `BridgeGate` in React, which shows an install prompt while nothing answers on loopback and lets the page through once the bridge is up. The first time a page from an `https://` origin reaches `127.0.0.1`, Chrome asks once whether the page may connect to software on this computer; choose *Allow*. See [Bridge detection and install](./javascript-sdk-integration.md#bridge-detection-and-install) in the SDK guide.

### Verify

```bash
curl http://127.0.0.1:48333/health
```

```json
{"host":"bridge","hostVersion":"0.32.0","sdkVersion":"0.32.0","protocolVersions":["2"],"terminal":{"label":"Lane 1","model":"VictaLane"}}
```

`terminal` is absent when none is configured; `GET /v1/terminal` returns the same object, or `404` without a terminal. If the default port was taken, the bridge log (and the tray's status line) names the port that was bound; `curl http://127.0.0.1:48334/health` and so on finds it, and the SDK probes the same range on its own.

To check that a terminal is reachable through the bridge without a session:

```bash
curl -X POST http://127.0.0.1:48333/v1/terminal/diagnose
```

`?poiId=...` sets the Nexo `POIID` the diagnosis request carries; without it the bridge sends its default.

A terminal that cannot be reached answers with a `SessionError` whose `code` is `NETWORK` or `TIMEOUT`; the [troubleshooting](#troubleshooting) section says what to check.

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
  "terminal": {
    "label": "Lane 1",
    "model": "VictaLane",
    "host": "192.168.4.108",
    "port": 8443,
    "encryption": false,
    "passphrase": null,
    "keyId": null,
    "trustAll": true,
    "caCertificatePath": null,
    "environment": null
  }
}
```

| Key | Default | Meaning |
|-----|---------|---------|
| `bindAddress` | `127.0.0.1` | Must be a loopback address; anything else is refused at load time. |
| `port` | `48333` | First port to try; the next ten are fallbacks. Changing it takes effect after a restart. |
| `allowedOrigins` | `["*"]` | Browser origins admitted, e.g. `https://pos.example.com`; a request whose `Origin` is not listed gets `401`. `*` is development-only and logs a warning at start. |
| `terminal` | none | The one terminal the bridge connects the POS to. Without it only `local` sessions work. It has no `poiId`: the page's `poiId` is passed through as the Nexo `POIID`. |
| `terminal.label`, `model` | none | Descriptive only; reported to the page in `/health` and `GET /v1/terminal`. |
| `terminal.host`, `port` | required, `8443` | The terminal's LAN address. |
| `terminal.encryption` | `false` | Nexo payload encryption. When `true`, `passphrase` and `keyId` are required; `keyVersion` defaults to `0`. |
| `terminal.trustAll` | `false` | Skip TLS verification (development terminals). Cannot be combined with `caCertificatePath` or `environment`. |
| `terminal.caCertificatePath`, `environment` | required unless `trustAll` | The Bilt CA PEM and `PRODUCTION` or `STAGING`, which selects the certificate hostname pattern. |

A config file from before this change, with a `terminals` list or a `poiId` on the terminal, is refused with a message saying what to change: replace the list with its one entry as `terminal` and delete the `poiId`. The bridge keeps running on its previous configuration (or the defaults) and shows the error in the menu until the file is fixed.

Passphrases never appear in logs, in `toString()` output or in the diagnostics summary.

### Pointing a browser page at the bridge

The JavaScript SDK does the probing: `localBridge()` tries `GET /health` on port 48333 and the ten ports above it, 400 ms each, and `BiltPos.connect` rejects with `BridgeMissingError` or `BridgeOutdatedError` when nothing suitable answers. A page can also probe by hand:

```javascript
const res = await fetch("http://127.0.0.1:48333/health", { signal: AbortSignal.timeout(400) });
const health = await res.json();   // health.protocolVersions tells the SDK what it can speak
```

Two browser-side caveats in this iteration:

- **CORS response headers are not sent yet.** The Session Host owns the HTTP layer and does not expose a CORS option, so a page on another origin can reach the bridge only where the browser does not enforce CORS for it: a page served from `http://127.0.0.1`/`http://localhost` itself (same-origin), a page whose dev server proxies `/health` and `/v1` to the bridge (what the [browser POS example](https://github.com/biltrewards/bilt-pos-sdk/tree/main/js/examples/browser-pos) does), a browser launched with web security disabled for development, or an Electron/WebView host. Adding an `allowedOrigins` CORS option to the host is the follow-up that lifts this.
- Chrome asks once per origin for permission to reach loopback (its Local Network Access check); that preflight needs `Access-Control-Allow-Private-Network: true`, which lands with the same host follow-up.

---

## Troubleshooting

**Nothing answers on 48333 (`BridgeMissingError`, the install prompt stays up).** Check the menu-bar icon is there; if it is not, launch the app (and clear the quarantine flag on an ad-hoc build, see [Install](#install-on-macos)). If it is, read the status line: the bridge may have bound a fallback port because 48333 was taken, which the SDK finds on its own but a hand-written probe or a dev-server proxy pointed at 48333 does not. `lsof -nP -iTCP:48333 -sTCP:LISTEN` names the process holding the port; quit it or set `"port"` to another value and restart the bridge. A bridge that cannot bind any port of its range logs the failure and shows it in the menu.

**Chrome never asks for permission, or the page cannot reach loopback.** Chrome 142+ gates requests from a public page to `127.0.0.1` behind a one-time *allow this site to connect to software on your computer* prompt, remembered per origin. If it was dismissed, open the site settings (the icon left of the address bar) and reset the local-network permission, then reload. Managed fleets can pre-grant it by policy. A page served from `http://localhost` or `http://127.0.0.1` is itself loopback and needs no permission. Firefox does not ask; Safari behaviour is unconfirmed.

**`401` from the bridge, `UNAUTHORIZED` in the page.** The page's `Origin` is not in `allowedOrigins`. Add the exact origin (`https://pos.example.com`, scheme and port included) or, for development, `"*"`, and reload the config. Requests without an `Origin` header (curl, server-side tools) always pass.

**The request succeeds in curl but fails in the browser with a CORS error.** The bridge does not send CORS headers in this iteration (see above). Serve the page same-origin, proxy `/health` and `/v1` through the page's own dev server, or wait for the host's CORS option.

**Starting a terminal session fails with `TIMEOUT` or `NETWORK`.** The bridge is up but cannot reach the terminal: `POST /v1/terminal/diagnose` fails the same way. Check the `host` and `port` in the config against the terminal's network screen, that the register machine is on the same LAN or routed to it, and that a firewall is not dropping 8443. A TLS failure shows as `NETWORK` with a certificate message in the bridge log: for a lab device set `"trustAll": true`; for a boarded one give `caCertificatePath` and the right `environment`. A mismatch between `encryption` and the terminal's own setting fails the first exchange with a `TERMINAL_ERROR` or a decode failure in the log. The admin exchange that opens a session times out after about two minutes, so a wrong address looks like a hang before it fails.

**Starting a terminal session fails with `UNSUPPORTED`, or `GET /v1/terminal` answers `404`.** The bridge has no terminal configured, so only `local` sessions work. Add a `terminal` object to the config file (the menu says *No terminal configured* until it loads); if the file still has the old `terminals` list, the menu shows the config error explaining the change. The `poiId` the page sends never causes this: it is passed through to the one terminal whatever its value.

**A terminal session cannot be started because one is already open on the terminal (`INVALID_STATE`).** A session the register did not end is still alive on the bridge: a page that was closed or reloaded without `end()`, or a crashed register. Sessions outlive the page by design (the bridge is where settlement runs). End the stale one through the bridge, `curl -X DELETE http://127.0.0.1:48333/v1/sessions/<id>`, and if it refuses because money is unresolved, finish the unwind or force-end it as the SDK guide's [session section](./javascript-sdk-integration.md#start-and-end-a-session) describes. Restarting the bridge drops its sessions and tells the terminal to discard its session data on the next start.

**`BridgeOutdatedError` / the update prompt.** The bridge's `protocolVersions` does not include the version the SDK was generated from. Install the newer bridge; there is no update feed in the development build, so download the `.dmg` and reinstall.

**Where are the logs?** `~/Library/Logs/Bilt Terminal Bridge/` on macOS (**Open logs** in the menu); the diagnostics summary from the menu is safe to paste into a ticket, it redacts passphrases.

---

## Development

```bash
./gradlew :bridge:run                                   # tray app from the build output
./gradlew :bridge:run --args="--config /path/to/config.json"
./gradlew :bridge:test
```

`-Dbilt.bridge.dir=/some/dir` pins both the config and the log directory, which keeps a development instance away from the installed one.

To try the bridge with a register right away, the browser emulator starts both: `./gradlew :emulator:browser:runWithBridge -PterminalHost=<terminal ip>` (or `-Plocal` without a terminal), or `scripts/browser-emulator.sh --terminal <terminal ip>` without Gradle. See [`js/apps/emulator`](https://github.com/biltrewards/bilt-pos-sdk/tree/main/js/apps/emulator) for the options.

The Session Host alone, without the tray, runs from `./gradlew :host:run --args=dev-host.json`; its config format is the host's own and documented on the [Session Host](./session-host.html) page.

### Packaging details

- `org.beryx.runtime` builds a jlink image and a jpackage app image from the non-modular classpath. Modules: `java.base, java.logging, java.net.http, java.xml, java.desktop, java.instrument, java.management, java.naming, java.security.jgss, java.sql, jdk.crypto.ec, jdk.unsupported` (the `instrument`/`management`/`naming`/`jgss` ones are Jetty's). Re-check after dependency changes with `./gradlew :bridge:installDist && jdeps --multi-release 21 --print-module-deps --ignore-missing-deps bridge/build/install/bridge/lib/*.jar`.
- The dmg is produced by `hdiutil create` (task `packageDmg`) rather than jpackage's installer step, whose Finder AppleScript hangs in non-interactive sessions. Both the `.app` and the `.dmg` land in `bridge/build/jpackage/`.
- macOS refuses a bundle version whose first number is zero, so a `0.x.y` SDK version becomes `CFBundleVersion x.y` while `CFBundleShortVersionString` keeps the real version.
- The app is ad-hoc signed (`codesign --force --deep -s -`). Developer ID signing and notarization are a follow-up.
- Launcher JVM options: `-Dapple.awt.UIElement=true` (no Dock icon), `-Dapple.awt.enableTemplateImages=true` (the monochrome tray glyph follows light and dark menu bars), `-Djava.net.preferIPv4Stack=true`.
- The app icon is a placeholder. `bridge/scripts/make-icons.sh` regenerates `bridge.icns` from `bridge.svg` with `qlmanage`, `sips` and `iconutil`.

---

## Limitations of the development build

- **Development mode only**: no pairing, no per-origin tokens, `allowedOrigins: ["*"]` by default, secrets stored in a plain JSON file.
- **macOS packaging only** so far; the application code is platform-neutral (`AppDirs` already knows the Windows and Linux locations) but there is no MSI, deb or rpm, and *Start at login* is implemented for macOS only.
- **Ad-hoc signed, not notarized**; Gatekeeper needs the one-time override above.
- **No CORS response headers** until the Session Host exposes a CORS option (see above); `allowedOrigins` is enforced server-side in the meantime.
- **Sessions do not reattach.** The bridge keeps a session alive when its page goes away, but this iteration of the JavaScript SDK has no call to pick an existing session up again; a reloaded page starts a new one and the old one must be ended (see [troubleshooting](#troubleshooting)).
- **No retail media creatives.** Widgets attach when the embedding host has an ad decision service; the development host wires an in-memory one with no creatives, so placements stay empty.
- No update feed, no cloud configuration.

---

## Next steps

- [JavaScript & React SDK Integration Guide](./javascript-sdk-integration.md) — the page side: sessions, basket, settlement, widgets and the React hooks.
- [Session Protocol Reference](./session-protocol-reference.html) — every request and event the bridge serves.
- [TerminalShopperSession Integration Guide](./checkout-session-integration.md) — the Java engine the bridge embeds, for what each operation does on the terminal.
