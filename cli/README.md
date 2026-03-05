# Bilt POS CLI

Command-line tool for sending Nexo Sale to POI requests to a Bilt payment terminal.

## Build

```bash
./gradlew :cli:build
```

## Usage

```bash
./gradlew :cli:run --args="<ip> [options]"
```

### Options

| Option | Description | Default |
|---|---|---|
| `--type <payment\|diagnosis\|display-standby\|display-receipt\|confirmation\|signature\|abort>` | Request type | `payment` |
| `--no-encryption` | Disable message encryption | encryption enabled |
| `--passphrase <value>` | Encryption passphrase | — |
| `--key-id <value>` | Encryption key identifier | — |
| `--key-version <number>` | Encryption key version | `0` |
| `--amount <number>` | Payment amount | `2.50` |
| `--currency <code>` | Currency code | `USD` |
| `--prompt <text>` | Prompt text for confirmation/signature | see below |
| `--abort-service-id <value>` | ServiceID of the request to abort | — |
| `-h, --help` | Show help | — |

### Examples

**Unencrypted payment (development):**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption"
```

**Unencrypted payment with custom amount:**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --amount 10.00"
```

**Encrypted payment:**

```bash
./gradlew :cli:run --args="192.168.1.100 --passphrase mySecret --key-id myTerminal"
```

**Diagnosis request:**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --type diagnosis"
```

**Display standby screen:**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --type display-standby"
```

**Display sample receipt:**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --type display-receipt"
```

**Confirmation prompt (default: "Would you like a receipt?"):**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --type confirmation"
```

**Confirmation with custom prompt:**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --type confirmation --prompt 'Accept terms and conditions?'"
```

**Signature capture (default: "Signature required"):**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --type signature"
```

**Signature with custom prompt:**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --type signature --prompt 'Sign to authorize \$94.50'"
```

**Abort an in-progress request:**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --type abort --abort-service-id SVC-01002"
```

The CLI connects to `https://<ip>:8443/nexo` and prints the terminal's JSON response to stdout.
