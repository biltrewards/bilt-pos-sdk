# bilt-pos-sdk

Java and Kotlin libraries for the nexo POS protocol.

## Modules

| Module    | Description                                      |
|-----------|--------------------------------------------------|
| `:java`   | Java library with Jackson serialization          |
| `:kotlin` | Kotlin library with kotlinx-serialization        |

## Usage

For most integrations, start with the high-level [`CheckoutSession` API](docs/checkout-session-integration.md) — it manages the basket, terminal display, loyalty, and the full payment sequence over the raw client shown below:

```java
try (CheckoutSession session = CheckoutSession.builder()
        .client(client)
        .saleId("POS-1").poiId("TERM-1").currency("USD")
        .start()
        .get()) {

    session.basket().addItem(BasketItem.sale("SKU-1", "Large Vanilla Candle", 2, new BigDecimal("24.99")));
    session.settle()
            .onSuccess(result -> printReceipt(result.getMerchantReceipt()))
            .onError(error -> SettlementRecovery.abort())
            .execute();
}
```

The raw nexo client remains fully available:

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

`trustAllCertificates()` is for development only. In production, validate the terminal's TLS certificate against the Bilt CA — see [Configuring Certificate Validation](docs/certificate-validation-setup.md).

## Build

```bash
./gradlew build
```

## Formatting

Java is formatted with [google-java-format](https://github.com/google/google-java-format) and Kotlin (including the Gradle scripts) with [ktfmt](https://github.com/facebook/ktfmt), driven by [Spotless](https://github.com/diffplug/spotless). Both formatters are non-configurable, so never format by hand. Running them needs Gradle on JDK 21 or newer (google-java-format is compiled for 21), which is what CI uses:

```bash
./gradlew spotlessApply   # rewrite everything
./gradlew spotlessCheck   # verify only
```

Generated sources (the `auto-generated` header) are excluded. For the IDE, install the google-java-format and ktfmt IntelliJ plugins so on-save output matches the build.

The initial reformat commit is listed in `.git-blame-ignore-revs`, so blame keeps pointing at the real author. GitHub and IntelliJ honor the file automatically; for the command line, run once per clone:

```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
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
