# Bilt POS SDK for JavaScript

The pnpm workspace for the JavaScript side of the SDK. The browser cannot talk to a Bilt
terminal itself, so these packages speak the [Session Protocol](../schema/session-protocol)
to a host that embeds the Java SDK — the Terminal Bridge on the register machine today, the
Cloud Session Service later.

| Package              | Contents                                                                                                                                                               |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `@bilt/pos-protocol` | Types and a typed `fetch` client generated from `schema/session-protocol/openapi.yaml`; the TypeScript domain model.                                                   |
| `@bilt/pos-sdk`      | The public SDK contract: `BiltPos`, `ShopperSession`, `TerminalShopperSession`, settlement handlers, `Operation`, errors; `@bilt/pos-sdk/internal` is the engine seam. |

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
```

CI runs the same steps in `.github/workflows/js.yml` and fails when the generated code is out
of date with the spec.
