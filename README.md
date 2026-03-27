# bilt-pos-sdk

Java and Kotlin libraries for the nexo POS protocol.

## Modules

| Module    | Description                                      |
|-----------|--------------------------------------------------|
| `:java`   | Java library with Jackson serialization          |
| `:kotlin` | Kotlin library with kotlinx-serialization        |

## Usage

```java
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.*;

// Create a client (unencrypted, for development)
BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
        .endpoint("https://192.168.1.100:8443/nexo")
        .trustAllCertificates()
        .build();

// Build a payment request
SaleToPOIRequest saleToPOIRequest = SaleToPOIRequest.builder()
        .messageHeader(MessageHeader.builder()
                .protocolVersion("3.0")
                .messageClass(MessageClassType.SERVICE)
                .messageCategory(MessageCategoryType.PAYMENT)
                .messageType(MessageTypeType.REQUEST)
                .serviceID("txn-001")
                .saleID("POS-1")
                .poiid("TERM-1")
                .build())
        .paymentRequest(PaymentRequest.builder()
                .saleData(SaleData.builder()
                        .saleTransactionID(TransactionIdentificationType.builder()
                                .transactionID("sale-123")
                                .timeStamp("2026-03-03T12:00:00-05:00")
                                .build())
                        .build())
                .paymentTransaction(PaymentTransaction.builder()
                        .amountsReq(AmountsReq.builder()
                                .currency("USD")
                                .requestedAmount(2.50)
                                .build())
                        .build())
                .build())
        .build();

// Wrap in NexoTerminalAPI envelope and send
NexoTerminalAPI request = NexoTerminalAPI.builder()
        .saleToPOIRequest(saleToPOIRequest)
        .build();

NexoTerminalAPI response = client.request(request);

// Unwrap the response
SaleToPOIResponse poiResponse = response.getSaleToPOIResponse();
ResultType result = poiResponse.getPaymentResponse().getResponse().getResult();
```

For encrypted (production) usage, provide a `SecurityKey`:

```java
SecurityKey key = SecurityKey.builder()
        .passphrase("sharedSecret")
        .keyIdentifier("myTerminal")
        .keyVersion(0)
        .build();

BiltNexoTerminalClient client = BiltNexoTerminalClient.builder()
        .endpoint("https://192.168.1.100:8443/nexo")
        .securityKey(key)
        .build();
```

## Build

```bash
./gradlew build
```

## Generate API Reference

Regenerate the schema reference docs (requires Node.js):

```bash
cd schema && npm install && npm run generate:api-reference
```

This converts the nexo JSON Schema to an OpenAPI spec and renders it as a self-contained HTML page at `docs/api-reference.html`.

## Project Structure

Source code is generated from JSON Schema (nexo protocol) and committed at the source level for readability. Generation tooling lives externally — this repo contains libraries only.

Convention plugins in `build-logic/` provide shared build configuration.
