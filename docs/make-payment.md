---
---

# Make a payment

Make an in-person card payment on a Bilt-connected terminal.

When your POS app initiates a payment request, it is routed to the terminal, which prompts the shopper to present their card and verify the payment. The terminal then sends the payment to Adyen for processing and your integration receives the result.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Terminal API fundamentals](./terminal-api.md).

---

## Make a payment request

To initiate a card payment, send a Terminal API request from your POS app. The terminal will then prompt the shopper to swipe, insert, or tap their card and, if required, enter their PIN or signature.

1. Send a Terminal API payment request with the following `MessageHeader` fields:

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
    - **`PaymentTransaction.AmountsReq.RequestedAmount`** — The final transaction amount.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "Payment",
         "MessageType": "Request",
         "ServiceID": "SVC-00842",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "PaymentRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "TXN-20260302-00391",
             "TimeStamp": "2026-03-02T14:35:00+00:00"
           }
         },
         "PaymentTransaction": {
           "AmountsReq": {
             "Currency": "USD",
             "RequestedAmount": 94.50
           }
         }
       }
     }
   }
   ```

2. The request is routed to the terminal. The shopper presents their card and verifies the payment.

3. Once the shopper interaction is complete, the payment is sent to Adyen for processing and your integration receives the result.

---

## Payment response

The result is returned in the API response. The main payment result is in `PaymentResponse.Response.Result`.

### Successful payment

When a payment succeeds:

- The terminal display shows that the transaction is approved.
- Your integration receives a result containing:

    - **`POIData.POITransactionID.TransactionID`** — the transaction identifier.
    - **`PaymentResponse.Response.Result`** — `Success`. Other possible values are `Partial` for a successful partial payment, or `Failure` for a declined payment.
    - **`PaymentResponse.Response.AdditionalResponse`** — additional transaction data, such as shopper recognition or shopper input details.
    - **`PaymentReceipt`** — receipt data in XML format containing HTML, plain text, and structured fields. See [Receipt format](./receipt-format.md) for details.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Payment",
        "MessageType": "Response",
        "ServiceID": "SVC-00842",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "PaymentResponse": {
        "Response": {
          "Result": "Success",
          "AdditionalResponse": "tidUsed=VictaLane-275839164&..."
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "4rKV001726384910000.AJ7F2M9KR43TPQB8",
            "TimeStamp": "2026-03-02T14:35:12+00:00"
          }
        },
        "PaymentResult": {
          "PaymentType": "Normal",
          "AmountsResp": {
            "Currency": "USD",
            "AuthorizedAmount": 94.50
          }
        }
      }
    }
  }
  ```

You can also view the payment details in the Bilt dashboard under **Transactions > Payments**.

### Failed payment

When a payment fails, the result includes `Result: Failure` along with information on why it failed.

- The terminal display shows **Declined**.
- Your integration receives a result containing:

    - **`POIData.POITransactionID.TransactionID`** — the transaction identifier for the failed attempt.
    - **`PaymentResponse.Response.Result`** — `Failure`.
    - **`PaymentResponse.Response.ErrorCondition`** — the reason for failure. For example, `Refused` means the card issuer declined the transaction; `Cancel` means the transaction was cancelled on the terminal.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Device",
        "MessageCategory": "Payment",
        "MessageType": "Response",
        "ServiceID": "SVC-00842",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "PaymentResponse": {
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "Refusal",
          "AdditionalResponse": "Transaction declined."
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "0f17480f-e013-4714-9508-f9681fd5ba8d",
            "TimeStamp": "2026-03-03T19:12:53-05:00"
          }
        },
        "SaleData": {
          "SaleTransactionID": {
            "TransactionID": "c6b793da-d0d6-4119-965e-c8bf3f692501",
            "TimeStamp": "2026-03-03T19:12:52.533469-05:00"
          }
        },
        "PaymentResult": {},
        "PaymentReceipt": [
          {
            "DocumentQualifier": "SaleReceipt",
            "OutputContent": {
              "OutputFormat": "XHTML",
              "OutputXHTML": "<?xml version=\"1.0\" encoding=\"UTF-8\"?><r:receipt xmlns:r=\"urn:bilt:receipt:v1\" type=\"MERCHANT\" version=\"1.0\"><r:htmlReceipt>...</r:htmlReceipt><r:plainTextReceipt>...</r:plainTextReceipt><r:receiptData>...</r:receiptData></r:receipt>"
            }
          },
          {
            "DocumentQualifier": "CustomerReceipt",
            "OutputContent": {
              "OutputFormat": "XHTML",
              "OutputXHTML": "<?xml version=\"1.0\" encoding=\"UTF-8\"?><r:receipt xmlns:r=\"urn:bilt:receipt:v1\" type=\"CUSTOMER\" version=\"1.0\"><r:htmlReceipt>...</r:htmlReceipt><r:plainTextReceipt>...</r:plainTextReceipt><r:receiptData>...</r:receiptData></r:receipt>"
            }
          }
        ]
      }
    }
  }
  ```

For a full list of failure reasons and what they mean, see [Refusal reasons](./refusal-reasons.md). For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).
> **Testing tip:** while building a test integration you can simulate a declined payment by setting the last three digits of `RequestedAmount` to `123` — for example `41.23` or `101.23`.

---

## Next steps

- [Cancel, reverse, or refund a payment](./undo-payment.md) — cancel, void, or refund a payment.
- [Verify payment status](./verify-transaction-status.md) — verify the status of a transaction when you don't receive a result, for example due to a connection issue. We strongly recommend implementing this to avoid unnecessary refunds or duplicate payments.
- [Cancel a payment](./cancel-payment.md) — abort a payment while it is in progress.
