# bilt-pos-sdk

Java and Kotlin libraries for the nexo POS protocol.

## Modules

| Module    | Description                                      |
|-----------|--------------------------------------------------|
| `:java`   | Java library with Jackson serialization          |
| `:kotlin` | Kotlin library with kotlinx-serialization        |

## Build

```bash
./gradlew build
```

## Project Structure

Source code is generated from JSON Schema (nexo protocol) and committed at the source level for readability. Generation tooling lives externally — this repo contains libraries only.

Convention plugins in `build-logic/` provide shared build configuration.
