---
---

# Nexo Terminal API Java Client — Integration Guide

## Overview

The Nexo Terminal API Java client (`BiltNexoTerminalClient`) is a client library for communicating with a Bilt Terminal Application via the **Nexo Sale to POI protocol (version 3.0)**. The Nexo standard defines a message-based API between a Sale System (e.g. a POS application) and a POI terminal (the payment device). All messages are JSON-serialised and follow a common envelope structure: a `SaleToPOIRequest` or `SaleToPOIResponse` wrapping a `MessageHeader` plus one typed request/response body.

The terminal exposes a single HTTPS endpoint:

```
POST https://<device_local_ip_address>:8443/nexo
Content-Type: application/json
```

Each request sends a `NexoTerminalAPI` envelope containing a `SaleToPOIRequest` and receives a `NexoTerminalAPI` envelope containing a `SaleToPOIResponse`.

> The device uses a self-signed TLS certificate. Your HTTP client must validate this certificate (certificate verification can be disabled in a development/test context).

---

## Installation

The SDK is published to Maven Central under the `com.bilt` group. Two artifacts are available — use the one that matches your project language:

| Artifact | Language |
|---|---|
| `pos-lib-java` | Java |
| `pos-lib-kotlin` | Kotlin |

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    // Java library
    implementation("com.bilt:pos-lib-java:0.5.2")

    // Kotlin library
    implementation("com.bilt:pos-lib-kotlin:0.5.2")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    // Java library
    implementation 'com.bilt:pos-lib-java:0.5.2'

    // Kotlin library
    implementation 'com.bilt:pos-lib-kotlin:0.5.2'
}
```

### Maven

```xml
<!-- Java library -->
<dependency>
    <groupId>com.bilt</groupId>
    <artifactId>pos-lib-java</artifactId>
    <version>0.5.2</version>
</dependency>

<!-- Kotlin library -->
<dependency>
    <groupId>com.bilt</groupId>
    <artifactId>pos-lib-kotlin</artifactId>
    <version>0.5.2</version>
</dependency>
```

---

## Getting started

### Create a client

**Unencrypted (development only):**

> Unencrypted mode is only available on development terminals. Production devices require encryption and will reject unencrypted requests.

```java
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;

BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
    .endpoint("https://<device-ip>:8443/nexo")
    .trustAllCertificates()
    .build();
```

**Encrypted (required for production):**

```java
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.security.SecurityKey;

SecurityKey key = SecurityKey.builder()
    .passphrase("<your-passphrase>")
    .keyIdentifier("<your-key-identifier>")
    .keyVersion(0)
    .build();

BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
    .endpoint("https://<device-ip>:8443/nexo")
    .securityKey(key)
    .build();
```

When a `SecurityKey` is provided, all requests are AES-encrypted and all responses are decrypted transparently.

---

## Example — Make a payment

The following example initiates a $25.00 USD payment transaction.

### Java code

```java
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.*;

// Note, trusting all certificates is only allowed in development environments.
// Note, not providing a security key is only allowed in development environments.
String endpoint = "https://<device-ip>:8443/nexo";
BiltNexoTerminalClient.Builder clientBuilder = BiltNexoTerminalClient.builder()
    .endpoint(endpoint)
    .trustAllCertificates();

BiltNexoTerminalClient client = clientBuilder.build();

SaleToPOIRequest saleToPOIRequest = SaleToPOIRequest.builder()
    .messageHeader(MessageHeader.builder()
        .protocolVersion("3.0")
        .messageClass(MessageClassType.SERVICE)
        .messageCategory(MessageCategoryType.PAYMENT)
        .messageType(MessageTypeType.REQUEST)
        .serviceID("20240101-001")      // unique per transaction
        .saleID("POS-LANE-1")
        .poiid("TERMINAL-001")
        .build())
    .paymentRequest(PaymentRequest.builder()
        .saleData(SaleData.builder()
            .saleTransactionID(TransactionIdentificationType.builder()
                .transactionID("TXN-0042")
                .timeStamp("2024-01-01T12:00:00.000Z")
                .build())
            .build())
        .paymentTransaction(PaymentTransaction.builder()
            .amountsReq(AmountsReq.builder()
                .currency("USD")
                .requestedAmount(25.00)
                .build())
            .build())
        .build())
    .build();

NexoTerminalAPI request = NexoTerminalAPI.builder()
    .saleToPOIRequest(saleToPOIRequest)
    .build();

NexoTerminalAPI response = client.request(request);
```

### Expected JSON request

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Payment",
      "MessageType": "Request",
      "ServiceID": "20240101-001",
      "SaleID": "POS-LANE-1",
      "POIID": "TERMINAL-001"
    },
    "PaymentRequest": {
      "SaleData": {
        "SaleTransactionID": {
          "TransactionID": "TXN-0042",
          "TimeStamp": "2024-01-01T12:00:00.000Z"
        }
      },
      "PaymentTransaction": {
        "AmountsReq": {
          "Currency": "USD",
          "RequestedAmount": 25.0
        }
      }
    }
  }
}
```

### Expected JSON response (approved)

```json
{
  "SaleToPOIResponse": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Payment",
      "MessageType": "Response",
      "ServiceID": "20240101-001",
      "SaleID": "POS-LANE-1",
      "POIID": "TERMINAL-001"
    },
    "PaymentResponse": {
      "Response": {
        "Result": "Success"
      },
      "PaymentResult": {
        "PaymentType": "Normal",
        "AmountsResp": {
          "Currency": "USD",
          "AuthorizedAmount": 25.0
        },
        "PaymentAcquirerData": {
          "AcquirerID": "123456",
          "ApprovalCode": "ABC123",
          "TransactionID": {
            "TransactionID": "ACQ-789",
            "TimeStamp": "2024-01-01T12:00:05.000Z"
          }
        }
      },
      "POIData": {
        "POITransactionID": {
          "TransactionID": "POI-0099",
          "TimeStamp": "2024-01-01T12:00:05.000Z"
        }
      }
    }
  }
}
```

### Parsing the response

```java
SaleToPOIResponse poiResponse = response.getSaleToPOIResponse();
PaymentResponse paymentResponse = poiResponse.getPaymentResponse();
Response result = paymentResponse.getResponse();

if (result.getResult() == ResultType.SUCCESS) {
    double authorized = paymentResponse.getPaymentResult()
        .getAmountsResp()
        .getAuthorizedAmount();
    System.out.println("Approved: " + authorized);
} else {
    System.err.println("Declined: " + result.getErrorCondition()
        + " — " + result.getAdditionalResponse());
}
```

---

## Key types

All types have a static `builder()` factory method for fluent construction.

### Envelope and shared types

| Type | Purpose |
|---|---|
| `NexoTerminalAPI` | Wire envelope wrapping `SaleToPOIRequest` or `SaleToPOIResponse` |
| `SaleToPOIRequest` | Root object for all outgoing messages |
| `SaleToPOIResponse` | Root object for all incoming messages |
| `MessageHeader` | Routing and protocol metadata, required on every message |
| `Response` | Contains `Result` (`Success`/`Failure`/`Partial`) and error details |
| `AmountsReq` | Requested amounts (currency + `requestedAmount` required) |

### Message categories

Each message category has a dedicated request and response type set on the `SaleToPOIRequest` / `SaleToPOIResponse` envelope. Use the corresponding `MessageCategoryType` enum value in the `MessageHeader`.

| Request | Response | `MessageCategory` | Description |
|---|---|---|---|
| `AbortRequest` | — | `ABORT` | Aborts a previously sent request before the terminal processes it |
| `AdminRequest` | `AdminResponse` | `ADMIN` | Administrative operation (implementation-defined) |
| `BalanceInquiryRequest` | `BalanceInquiryResponse` | `BALANCE_INQUIRY` | Queries a card or loyalty account balance without charging |
| `BatchRequest` | `BatchResponse` | `BATCH` | Groups multiple transactions to be processed as a batch |
| `CardAcquisitionRequest` | `CardAcquisitionResponse` | `CARD_ACQUISITION` | Reads card data for later reuse without initiating a payment |
| `CardReaderAPDURequest` | `CardReaderAPDUResponse` | `CARD_READER_APDU` | Sends a low-level APDU command directly to the card reader |
| `CardReaderInitRequest` | `CardReaderInitResponse` | `CARD_READER_INIT` | Initializes the card reader hardware |
| `CardReaderPowerOffRequest` | `CardReaderPowerOffResponse` | `CARD_READER_POWER_OFF` | Powers off the card reader hardware |
| `DiagnosisRequest` | `DiagnosisResponse` | `DIAGNOSIS` | Terminal health check; use on startup to verify connectivity |
| `DisplayRequest` | `DisplayResponse` | `DISPLAY` | Shows text or content on the terminal display |
| `EnableServiceRequest` | `EnableServiceResponse` | `ENABLE_SERVICE` | Enables or disables specific services on the terminal |
| `EventNotification` | — | `EVENT` | Unsolicited notification sent from the terminal (no response expected) |
| `GetTotalsRequest` | `GetTotalsResponse` | `GET_TOTALS` | Retrieves cumulative transaction totals for the current shift or batch |
| `InputRequest` | `InputResponse` | `INPUT` | Requests customer input (e.g. confirmation, selection) on the terminal |
| `InputUpdate` | — | `INPUT_UPDATE` | Updates a previously sent `InputRequest` while it is still pending |
| `LoginRequest` | `LoginResponse` | `LOGIN` | Establishes a Sale to POI session; must be sent before transactions |
| `LogoutRequest` | `LogoutResponse` | `LOGOUT` | Terminates the current Sale to POI session |
| `LoyaltyRequest` | `LoyaltyResponse` | `LOYALTY` | Processes a standalone loyalty award or redemption |
| `PaymentRequest` | `PaymentResponse` | `PAYMENT` | Initiates a payment transaction |
| `PINRequest` | `PINResponse` | `PIN` | Requests PIN entry from the cardholder on the terminal |
| `PrintRequest` | `PrintResponse` | `PRINT` | Sends a receipt or document to the terminal printer |
| `ReconciliationRequest` | `ReconciliationResponse` | `RECONCILIATION` | Closes the current batch and reconciles totals with the acquirer |
| `ReversalRequest` | `ReversalResponse` | `REVERSAL` | Reverses a previous payment or loyalty transaction |
| `SoundRequest` | `SoundResponse` | `SOUND` | Plays a sound or tone on the terminal |
| `StoredValueRequest` | `StoredValueResponse` | `STORED_VALUE` | Activates, reloads, or redeems a stored value or gift card |
| `TransactionStatusRequest` | `TransactionStatusResponse` | `TRANSACTION_STATUS` | Queries the current or last known status of a specific transaction |
| `TransmitRequest` | `TransmitResponse` | `TRANSMIT` | Transmits raw data to the terminal for proprietary extensions |

---

## Next steps

- [Make a payment](./make-payment.md) — full payment request/response details.
- [Cancel, reverse, or refund a payment](./undo-payment.md) — overview of all options for undoing a payment.
- [Cancel a payment](./cancel-payment.md) — abort an in-progress transaction.
- [Reverse a payment](./reverse-payment.md) — void a completed payment before the batch settles.
- [Referenced refund](./refund-referenced.md) — post-clearing refund linked to the original payment.
- [Unreferenced refund](./refund-unreferenced.md) — refund to any card.
- [Show an image on the terminal](./display-image.md) — display an image on the terminal screen.
- [Show a QR code or barcode on the terminal](./display-qr.md) — display a QR code or barcode on the terminal screen.
- [Show a virtual receipt on the terminal](./display-receipt.md) — display a virtual receipt on the terminal screen.
- [Show the standby screen](./display-standby.md) — return the terminal to its standby display.
