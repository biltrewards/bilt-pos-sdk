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
| `--type <payment\|refund\|diagnosis\|display-standby\|display-receipt\|confirmation\|signature\|reversal\|transaction-status\|abort>` | Request type | `payment` |
| `--no-encryption` | Disable message encryption | encryption enabled |
| `--passphrase <value>` | Encryption passphrase | — |
| `--key-id <value>` | Encryption key identifier | — |
| `--key-version <number>` | Encryption key version | `0` |
| `--amount <number>` | Payment amount | `2.50` |
| `--currency <code>` | Currency code | `USD` |
| `--prompt <text>` | Prompt text for confirmation/signature | see below |
| `--original-service-id <value>` | POI transaction ID of the original payment to reverse | — |
| `--original-timestamp <value>` | Timestamp of the original POI transaction (ISO 8601) | — |
| `--reversal-reason <value>` | Reversal reason: `CustCancel`, `MerchantCancel`, `Malfunction`, `Unable2Compl` | `MerchantCancel` |
| `--status-service-id <value>` | ServiceID of the transaction to query status for | — |
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

**Referenced refund (using original transaction ID):**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --type refund --original-service-id TXN-12345 --original-timestamp 2026-03-06T11:00:00-05:00"
```

**Unreferenced refund (card-present, specify amount):**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --type refund --amount 10.00"
```

**Reverse a completed payment:**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --type reversal --original-service-id TXN-12345 --original-timestamp 2026-03-06T11:00:00-05:00"
```

**Reverse with a specific reason:**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --type reversal --original-service-id TXN-12345 --original-timestamp 2026-03-06T11:00:00-05:00 --reversal-reason CustCancel"
```

**Query the status of a previous transaction:**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --type transaction-status --status-service-id SVC-01002"
```

**Abort an in-progress request:**

```bash
./gradlew :cli:run --args="192.168.1.100 --no-encryption --type abort --abort-service-id SVC-01002"
```

The CLI connects to `https://<ip>:8443/nexo` and prints the terminal's JSON response to stdout.
