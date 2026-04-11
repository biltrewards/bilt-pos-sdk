---
---

# Query a gift card balance

Check the remaining funds on an activated gift card.

A balance inquiry lets your POS app verify a card's available balance before or after a transaction. This is useful for displaying the balance to the cashier, deciding whether a single gift card can cover a purchase, or confirming that a load or activation was applied correctly. No funds are moved during a balance inquiry.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Terminal API fundamentals](./terminal-api.md).

---

## Make a balance inquiry request

To query a gift card balance, send a Terminal API request with `MessageCategory` set to `BalanceInquiry` and `PaymentInstrumentType` set to `StoredValue`.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `BalanceInquiry`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `BalanceInquiryRequest` fields:

    - **`PaymentAccountReq.PaymentInstrumentData.PaymentInstrumentType`** — `StoredValue`
    - **`PaymentAccountReq.PaymentInstrumentData.StoredValueAccountID.StoredValueAccountType`** — `GiftCard`
    - **`PaymentAccountReq.PaymentInstrumentData.StoredValueAccountID.StoredValueProvider`** — The stored value provider (e.g. `givex`, `svs`, `valuelink`).
    - **`PaymentAccountReq.PaymentInstrumentData.StoredValueAccountID.IdentificationType`** — `PAN` for a card number, or `BarCode` for a barcode identifier.
    - **`PaymentAccountReq.PaymentInstrumentData.StoredValueAccountID.EntryMode`** — How the card is identified: `Scanned`, `MagStripe`, `Keyed`, or `Contactless`.
    - **`PaymentAccountReq.PaymentInstrumentData.StoredValueAccountID.StoredValueID`** *(conditional)* — The card number or barcode. Required when `EntryMode` is `Scanned` or `Keyed`.
    - **`PaymentAccountReq.PaymentInstrumentData.StoredValueAccountID.ExpiryDate`** *(optional)* — Card expiration date, if available.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "BalanceInquiry",
         "MessageType": "Request",
         "ServiceID": "SVC-01020",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "BalanceInquiryRequest": {
         "PaymentAccountReq": {
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

2. The request is routed to the terminal. If `EntryMode` is `MagStripe`, the terminal prompts the customer to swipe the card.

3. The terminal queries the stored value provider and your integration receives the result.

---

## Balance inquiry response

The result is returned in the API response in a `BalanceInquiryResponse` body. The main result is in `BalanceInquiryResponse.Response.Result`.

### Successful inquiry

When the balance inquiry succeeds, your integration receives:

- **`BalanceInquiryResponse.Response.Result`** — `Success`.
- **`PaymentAccountStatus.CurrentBalance`** — the available balance on the gift card.
- **`PaymentAccountStatus.Currency`** — the currency code.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "BalanceInquiry",
        "MessageType": "Response",
        "ServiceID": "SVC-01020",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "BalanceInquiryResponse": {
        "Response": {
          "Result": "Success"
        },
        "PaymentAccountStatus": {
          "CurrentBalance": 42.75,
          "Currency": "USD"
        }
      }
    }
  }
  ```

### Failed inquiry

When a balance inquiry fails, the result includes:

- **`BalanceInquiryResponse.Response.Result`** — `Failure`.
- **`BalanceInquiryResponse.Response.ErrorCondition`** — the reason for failure. For example, `NotAllowed` if the card is not activated, or `DeviceOut` if the terminal could not be reached.

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Make a gift card payment](./gift-card-payment.md) — process a payment using a gift card.
- [Load a gift card balance](./gift-card-load.md) — add funds to a gift card.
- [Activate a gift card](./gift-card-activate.md) — enable a new gift card.
