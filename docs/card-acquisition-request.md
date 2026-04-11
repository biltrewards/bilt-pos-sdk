---
---

# Acquire card data

Read card and loyalty information from the terminal without processing a payment.

A card acquisition request prompts the shopper to present their card. The terminal reads the card and returns data such as the masked PAN, payment brand, loyalty accounts, and optionally a payment token. No funds are captured — this is a read-only operation.

Common use cases include loyalty lookups, tax-free eligibility checks based on issuer country, and shopper identification before starting a transaction.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Terminal API fundamentals](./terminal-api.md).

---

## Card acquisition request

To read card data, send a Terminal API request from your POS app. The terminal will prompt the shopper to swipe, insert, or tap their card.

1. Send a Terminal API card acquisition request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `CardAcquisition`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal. Required for `Service`-class requests.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `CardAcquisitionRequest` fields:

    - **`SaleData.SaleTransactionID.TransactionID`** — Your reference for this card acquisition. We recommend using a unique value. This is used to correlate the acquisition with a subsequent payment.
    - **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
    - **`SaleData.TokenRequestedType`** — *(optional)* Set to `Customer` to request a reusable payment token, or `Transaction` for a single-use token. Omit if you do not need a token.

   And the optional `CardAcquisitionTransaction` fields:

    - **`AllowedPaymentBrand`** — *(optional)* Array of card brands to accept. When omitted, all brands are accepted. See [Payment brands](./payment-brands.md) for possible values.
    - **`AllowedLoyaltyBrand`** — *(optional)* Array of loyalty program identifiers to accept. Ignored when `LoyaltyHandling` is `Forbidden`.
    - **`LoyaltyHandling`** — *(optional)* Controls whether loyalty data is collected. See [Loyalty handling values](#loyalty-handling-values) below.
    - **`ForceEntryMode`** — *(optional)* Array of card entry methods to allow. When omitted, all methods are enabled. See [Entry mode values](#entry-mode-values) below.
    - **`TotalAmount`** — *(optional)* Transaction amount for context. Not enforced — used by the terminal to provide the shopper with context about the upcoming transaction.

   Minimal example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "CardAcquisition",
         "MessageType": "Request",
         "ServiceID": "SVC-00901",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "CardAcquisitionRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "TXN-20260302-00410",
             "TimeStamp": "2026-03-02T14:35:00+00:00"
           }
         },
         "CardAcquisitionTransaction": {
           "LoyaltyHandling": "Forbidden"
         }
       }
     }
   }
   ```

   Full example request with all optional fields:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "CardAcquisition",
         "MessageType": "Request",
         "ServiceID": "SVC-00902",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "CardAcquisitionRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "TXN-20260302-00411",
             "TimeStamp": "2026-03-02T14:36:00+00:00"
           },
           "TokenRequestedType": "Customer"
         },
         "CardAcquisitionTransaction": {
           "AllowedPaymentBrand": ["Visa", "Mastercard"],
           "AllowedLoyaltyBrand": ["SuperBonus"],
           "LoyaltyHandling": "Allowed",
           "ForceEntryMode": ["ICC", "Contactless"],
           "TotalAmount": 25.00
         }
       }
     }
   }
   ```

2. The request is routed to the terminal. The shopper presents their card using one of the allowed entry methods.

3. The terminal reads the card and returns the result. The terminal does **not** process a payment — the card data is returned to your POS app for further action.

---

## Card acquisition response

The result is returned in the API response. The main result is in `CardAcquisitionResponse.Response.Result`.

### Successful acquisition

When the card is read successfully:

- The terminal reads the card and returns the data immediately. No payment is processed.
- Your integration receives a result containing:

    - **`Response.Result`** — `Success`.
    - **`POIData.POITransactionID.TransactionID`** — Terminal-assigned reference for this acquisition. Use this to link the acquisition to a subsequent payment.
    - **`POIData.POITransactionID.TimeStamp`** — Timestamp of the acquisition on the terminal.
    - **`PaymentBrand`** — Array containing the detected card brand (e.g. `["Visa"]`).
    - **`PaymentInstrumentData.CardData.EntryMode`** — How the card was read (e.g. `["ICC"]`).
    - **`PaymentInstrumentData.CardData.MaskedPAN`** — Masked card number (e.g. `"************5678"`).
    - **`PaymentInstrumentData.CardData.PaymentAccountRef`** — Payment account reference, if available.
    - **`PaymentInstrumentData.CardData.CardCountryCode`** — Three-digit issuer country code (e.g. `"840"` for US).
    - **`PaymentInstrumentData.CardData.PaymentToken`** — *(present only when `TokenRequestedType` was set in the request)* Contains `TokenValue`, `TokenRequestedType`, and optionally `ExpiryDateTime`.
    - **`LoyaltyAccount`** — *(present only when loyalty data was collected)* Array of loyalty accounts detected. See [Loyalty accounts in the response](#loyalty-accounts-in-the-response) below.
    - **`CustomerLanguage`** — Preferred language from the card (e.g. `"en"`), if available.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "CardAcquisition",
        "MessageType": "Response",
        "ServiceID": "SVC-00902",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "CardAcquisitionResponse": {
        "Response": {
          "Result": "Success"
        },
        "SaleData": {
          "SaleTransactionID": {
            "TransactionID": "TXN-20260302-00411",
            "TimeStamp": "2026-03-02T14:36:00+00:00"
          }
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "491",
            "TimeStamp": "2026-03-02T14:36:01.000+00:00"
          }
        },
        "PaymentBrand": ["Visa"],
        "PaymentInstrumentData": {
          "PaymentInstrumentType": "Card",
          "CardData": {
            "EntryMode": ["Contactless"],
            "MaskedPAN": "************5678",
            "PaymentAccountRef": "PAR123456789",
            "CardCountryCode": "840",
            "PaymentToken": {
              "TokenValue": "tok_abc123",
              "TokenRequestedType": "Customer",
              "ExpiryDateTime": "2027-12-31T23:59:59"
            }
          }
        },
        "LoyaltyAccount": [
          {
            "LoyaltyAccountID": {
              "EntryMode": ["MagStripe"],
              "IdentificationType": "PAN",
              "IdentificationSupport": "LoyaltyCard",
              "LoyaltyID": "8678252755678"
            },
            "LoyaltyBrand": "SuperBonus"
          }
        ],
        "CustomerLanguage": "en"
      }
    }
  }
  ```

### Failed acquisition

When a card acquisition fails, the result includes `Result: Failure` along with information on why it failed.

- Your integration receives a result containing:

    - **`Response.Result`** — `Failure`.
    - **`Response.ErrorCondition`** — The reason for failure.
    - **`Response.AdditionalResponse`** — Human-readable description of the failure.

  On failure, `PaymentBrand`, `PaymentInstrumentData`, `LoyaltyAccount`, and `CustomerLanguage` are **not** included in the response.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "CardAcquisition",
        "MessageType": "Response",
        "ServiceID": "SVC-00901",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "CardAcquisitionResponse": {
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "Cancel",
          "AdditionalResponse": "User Cancellation during Payment Card Selection"
        }
      }
    }
  }
  ```

### Error conditions

| ErrorCondition | Description |
|---|---|
| `Cancel` | The shopper cancelled the card acquisition on the terminal. |
| `DeviceOut` | The terminal is disconnected or unavailable. |
| `UnreachableHost` | The request timed out waiting for the card read (60-second timeout). |
| `NotAllowed` | Invalid configuration (e.g. incompatible entry mode or brand restrictions). |
| `MessageFormat` | The request could not be parsed — check that all required fields are present. |

---

## Entry mode values

The `ForceEntryMode` array controls which card presentation methods the terminal accepts. When omitted, all methods are enabled.

| Value | Description |
|---|---|
| `ICC` | Chip card inserted into the reader. |
| `MagStripe` | Card swiped through the magnetic stripe reader. |
| `Contactless` | Card or device tapped on the contactless reader. |
| `Tapped` | Alias for `Contactless`. |
| `Keyed` | Card number entered manually on the terminal keypad. |
| `Manual` | Alias for `Keyed`. |

In the response, `EntryMode` reflects how the card was actually read:

| Response value | Meaning |
|---|---|
| `ICC` | Card was inserted (chip). |
| `MagStripe` | Card was swiped. |
| `Contactless` | Card or device was tapped. |
| `Keyed` | Card number was entered manually. |

---

## Loyalty handling values

The `LoyaltyHandling` field controls whether and how loyalty data is collected alongside the card read.

| Value | Description |
|---|---|
| `Forbidden` | Do not collect loyalty data. If set, `AllowedLoyaltyBrand` is ignored and the response will not contain `LoyaltyAccount`. |
| `Allowed` | Collect loyalty data if the card has associated loyalty accounts. |
| `Processed` | Collect and process loyalty data during the acquisition. |
| `Proposed` | Present loyalty options to the shopper for selection. |
| `Required` | Loyalty data is mandatory — the acquisition fails if no loyalty account is found. |

When `LoyaltyHandling` is omitted, the terminal uses its default behavior.

---

## Loyalty accounts in the response

When loyalty data is collected, the response includes a `LoyaltyAccount` array. Each entry contains:

- **`LoyaltyAccountID.EntryMode`** — How the loyalty account was read (e.g. `["MagStripe"]`).
- **`LoyaltyAccountID.IdentificationType`** — The type of identifier. Possible values: `PAN`, `BarCode`, `AccountNumber`, `PhoneNumber`.
- **`LoyaltyAccountID.IdentificationSupport`** — *(optional)* The medium used to identify the account. Possible values: `LoyaltyCard`, `MobileApplication`.
- **`LoyaltyAccountID.LoyaltyID`** — The loyalty account identifier.
- **`LoyaltyBrand`** — *(optional)* The loyalty program name (e.g. `"SuperBonus"`).

---

## Payment tokens

To request a payment token, set `SaleData.TokenRequestedType` in the request:

| Value | Description |
|---|---|
| `Customer` | Returns a reusable token that identifies the shopper across transactions. Use this for loyalty programs and shopper recognition. |
| `Transaction` | Returns a single-use token valid only for the subsequent payment. |

When a token is returned, it appears in `PaymentInstrumentData.CardData.PaymentToken`:

- **`TokenValue`** — The token string.
- **`TokenRequestedType`** — Echoes the type that was requested (`Transaction` or `Customer`).
- **`ExpiryDateTime`** — *(optional)* Expiry timestamp for the token.

---

## Cancel a card acquisition

To cancel a card acquisition that is in progress (before the shopper presents their card), send an abort request:

```json
{
  "SaleToPOIRequest": {
    "MessageHeader": {
      "ProtocolVersion": "3.0",
      "MessageClass": "Service",
      "MessageCategory": "Abort",
      "MessageType": "Request",
      "ServiceID": "SVC-00903",
      "SaleID": "BiltPOS-Lane3",
      "POIID": "VictaLane-275839164"
    },
    "AbortRequest": {
      "AbortReason": "MerchantAbort",
      "MessageReference": {
        "MessageCategory": "CardAcquisition",
        "ServiceID": "SVC-00902",
        "SaleID": "BiltPOS-Lane3"
      }
    }
  }
}
```

The original card acquisition response will then return with `ErrorCondition: Cancel`. For details on abort handling, see [Cancel a payment](./cancel-payment.md).

---

## Using card acquisition before a payment

A common pattern is to acquire card data first, then use the terminal-assigned reference in a subsequent payment request. This allows your POS to identify the shopper and apply discounts or loyalty before initiating payment.

1. Send a `CardAcquisitionRequest` and receive the response.
2. Note the `POIData.POITransactionID.TransactionID` and `POIData.POITransactionID.TimeStamp` from the response.
3. Use these values in your subsequent `PaymentRequest` to link the payment to the already-read card, so the shopper does not need to present their card again.

---

## Next steps

- [Make a payment](./make-payment.md) — process a card payment.
- [Payment brands](./payment-brands.md) — full list of supported card brands.
- [Cancel a payment](./cancel-payment.md) — abort an in-progress request.
- [Handle responses](./error-scenarios.md) — general guidance on error handling.
