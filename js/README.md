# Bilt POS SDK for JavaScript

The pnpm workspace for the JavaScript side of the SDK. The browser cannot talk to a Bilt
terminal itself, so these packages speak the [Session Protocol](../schema/session-protocol)
to a host that embeds the Java SDK — the Terminal Bridge on the register machine today, the
Cloud Session Service later.

| Package              | Contents                                                                                                                                                                                                                                                  |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `@bilt/pos-protocol` | Types and a typed `fetch` client generated from `schema/session-protocol/openapi.yaml`; the TypeScript domain model.                                                                                                                                      |
| `@bilt/pos-sdk`      | The SDK: `BiltPos`, `ShopperSession`, `TerminalShopperSession`, settlement handlers, `Operation`, errors; `@bilt/pos-sdk/bridge` is the Terminal Bridge engine, `@bilt/pos-sdk/internal` the engine seam.                                                 |
| `@bilt/pos-react`    | React bindings: `BiltPosProvider`, session, basket, member and settlement hooks, `RetailMediaSurface`; `@bilt/pos-react/bridge` has `useBridge`, `InstallBridgePrompt` and `BridgeGate`.                                                                  |
| `browser-pos`        | `examples/browser-pos`: the reference register, a Vite + React page over the packages above; also holds the compiled samples of the [JavaScript SDK guide](../docs/javascript-sdk-integration.md).                                                        |
| `@bilt/pos-emulator` | `apps/emulator`: the browser register emulator, the counterpart of the Compose emulator in `../emulator`; every feature of the checkout contract, with a contract smoke test against the Session Host (`pnpm --filter @bilt/pos-emulator test:contract`). |

## Working in the workspace

Node 20 LTS (`.nvmrc`) and pnpm 10 (`packageManager` in `package.json`; `corepack enable` or
`npx pnpm`).

```sh
pnpm install
pnpm generate        # regenerate packages/protocol/src/generated from the spec; commit the result
pnpm lint            # eslint + prettier --check; `pnpm format` rewrites
pnpm lint:spec       # redocly lint of the Session Protocol spec
pnpm typecheck
pnpm build
pnpm test            # vitest, including type tests and the spec examples validated with Ajv
pnpm --filter @bilt/pos-sdk test:contract   # the SDK runtime against the real Session Host (needs a JDK and host/)
```

CI runs the same steps in `.github/workflows/js.yml` and fails when the generated code is out
of date with the spec.
