---
---

# Make a gift card payment

Process an in-person payment using a gift card on a Bilt-connected terminal.

A gift card payment works like a standard card payment, but uses a stored value card as the payment instrument. When the card's balance is less than the requested amount, a partial authorization is returned — your POS app can then collect the remainder using a second gift card or another payment method.

> **Note:** These docs use "gift card" terminology, but all operations apply to any stored value card type — including phone cards and other prepaid instruments. Set `StoredValueAccountType` to `GiftCard`, `PhoneCard`, or `Other` as appropriate.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. [Activated the gift card](./gift-card-activate.md) if it has not already been activated.

---

## Make a gift card payment request

To process a gift card payment, send a Terminal API payment request with `PaymentInstrumentType` set to `StoredValue`. This tells the terminal to charge the gift card rather than prompting for a standard card payment.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `Payment`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `PaymentRequest` fields:

    - **`SaleData.SaleTransactionID.TransactionID`** — Your reference for this payment. We recommend using a unique value per transaction. This appears as the merchant reference in reports.
    - **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
    - **`PaymentTransaction.AmountsReq.Currency`** — The transaction currency code (e.g. `USD`).
    - **`PaymentTransaction.AmountsReq.RequestedAmount`** — The payment amount.
    - **`PaymentData.PaymentInstrumentData.PaymentInstrumentType`** — `StoredValue`
    - **`PaymentData.PaymentInstrumentData.StoredValueAccountID.StoredValueAccountType`** — The type of stored value card: `GiftCard`, `PhoneCard`, or `Other`.
    - **`PaymentData.PaymentInstrumentData.StoredValueAccountID.StoredValueProvider`** — The stored value provider (e.g. `givex`, `svs`, `valuelink`).
    - **`PaymentData.PaymentInstrumentData.StoredValueAccountID.IdentificationType`** — How the card is identified: `PAN` (card number), `BarCode` (barcode), `PhoneNumber` (for phone cards), or `AccountNumber` (account-based).
    - **`PaymentData.PaymentInstrumentData.StoredValueAccountID.EntryMode`** — How the card is identified: `Scanned`, `MagStripe`, `Keyed`, or `Contactless`.
    - **`PaymentData.PaymentInstrumentData.StoredValueAccountID.StoredValueID`** *(conditional)* — The card number or barcode. Required when `EntryMode` is `Scanned` or `Keyed`.
    - **`PaymentData.PaymentInstrumentData.StoredValueAccountID.ExpiryDate`** *(optional)* — Card expiration date, if available.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "Payment",
         "MessageType": "Request",
         "ServiceID": "SVC-01010",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "PaymentRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "SVPAY-20260410-00012",
             "TimeStamp": "2026-04-10T11:20:00+00:00"
           }
         },
         "PaymentTransaction": {
           "AmountsReq": {
             "Currency": "USD",
             "RequestedAmount": 35.00
           }
         },
         "PaymentData": {
           "PaymentInstrumentData": {
             "PaymentInstrumentType": "StoredValue",
             "StoredValueAccountID": {
               "StoredValueAccountType": "GiftCard",
               "StoredValueProvider": "givex",
               "IdentificationType": "PAN",
               "EntryMode": "Scanned",
               "StoredValueID": "6006491260550218157",
               "ExpiryDate": "1228"
             }
           }
         }
       }
     }
   }
   ```

2. The request is routed to the terminal. If `EntryMode` is `MagStripe`, the terminal prompts the shopper to swipe the card.

3. The terminal sends the payment to the stored value provider and your integration receives the result.

---

## Payment response

The result is returned in the API response in a `PaymentResponse` body. The main result is in `PaymentResponse.Response.Result`.

### Successful payment

When the full amount is authorized, your integration receives:

- **`PaymentResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — the transaction identifier.
- **`PaymentResult.AmountsResp.AuthorizedAmount`** — the amount authorized, equal to the requested amount.
- **`PaymentResult.AmountsResp.Currency`** — the currency code.
- **`PaymentResponse.Response.AdditionalResponse`** *(optional)* — URL-encoded string containing `currentBalance`, the remaining balance on the gift card after the transaction (e.g. `currentBalance=65.00`). Only included when the stored value provider returns balance information. May also include `accountType` if returned by the payment processor — see [AdditionalResponse fields](./make-payment.md#additionalresponse-fields) for details.
- **`PaymentReceipt`** — receipt data in XML format. For gift card transactions, the `receiptData` section may include `availableBalance` with the remaining card balance. See [Receipt format](./receipt-format.md) for details.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Payment",
        "MessageType": "Response",
        "ServiceID": "SVC-01010",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "PaymentResponse": {
        "Response": {
          "Result": "Success",
          "AdditionalResponse": "currentBalance=65.00"
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "c4e19a22-7bdd-4f13-b901-3a8d6e5f1c74",
            "TimeStamp": "2026-04-10T11:20:06+00:00"
          }
        },
        "PaymentResult": {
          "PaymentType": "Normal",
          "AmountsResp": {
            "Currency": "USD",
            "AuthorizedAmount": 35.00
          }
        }
      }
    }
  }
  ```

> **Note:** The `currentBalance` in `AdditionalResponse` shows the gift card's remaining balance after the payment. Use this to display the balance to the customer or for reconciliation purposes.

### Partial payment

When the gift card balance is less than the requested amount, the provider may authorize a partial payment. Your integration receives:

- **`PaymentResponse.Response.Result`** — `Partial`.
- **`PaymentResult.AmountsResp.AuthorizedAmount`** — the amount actually authorized (the card's available balance).
- **`PaymentResponse.Response.AdditionalResponse`** *(optional)* — URL-encoded string containing `currentBalance` (the remaining balance, e.g. `currentBalance=0.00` when fully depleted) and `accountType`. See successful payment above for details.

The remaining balance must be collected through another payment method — either a second gift card or a standard card payment.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Payment",
        "MessageType": "Response",
        "ServiceID": "SVC-01010",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "PaymentResponse": {
        "Response": {
          "Result": "Partial",
          "AdditionalResponse": "currentBalance=0.00"
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "a91d3f44-2c8e-4b6a-9e01-7d2a4c6f8b12",
            "TimeStamp": "2026-04-10T11:20:06+00:00"
          }
        },
        "PaymentResult": {
          "PaymentType": "Normal",
          "AmountsResp": {
            "Currency": "USD",
            "AuthorizedAmount": 22.50
          }
        }
      }
    }
  }
  ```

> **Tip:** To avoid partial payments, [query the balance](./gift-card-balance-inquiry.md) first. If the balance is insufficient, charge only the available amount to the gift card and collect the remainder separately.

### Failed payment

When a payment fails, the result includes:

- **`PaymentResponse.Response.Result`** — `Failure`.
- **`PaymentResponse.Response.ErrorCondition`** — the reason for failure. For example, `Refusal` if the card has no balance, or `NotAllowed` if the card is not activated.

For a full list of failure reasons and what they mean, see [Refusal reasons](./refusal-reasons.md). For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Query a gift card balance](./gift-card-balance-inquiry.md) — check the remaining balance before or after a payment.
- [Cancel, reverse, or refund a payment](./undo-payment.md) — options for undoing a payment.
