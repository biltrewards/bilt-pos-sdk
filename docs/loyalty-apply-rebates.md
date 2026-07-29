---
---

# Apply loyalty rebates

Apply coupons and offers as a discount on the current sale.

A rebate transaction commits the coupons and offers that apply to the basket and returns the discount to subtract — as an amount off the whole purchase (`TotalRebate`), per-item amounts, or extra free units. The selection of which coupons apply can be driven from either side:

- **Terminal-driven** — the terminal owns the coupon UI. The customer browses and stages coupons on the terminal during scanning or at the totals screen; staged selections are invisible to the register until the rebate request commits them. The terminal holds the rebate request open while the customer finalizes their selection and confirms.
- **Register-driven** — the register initiates the rebate without customer interaction, and the loyalty provider evaluates the basket and applies the applicable offers automatically.

The request is the same in both cases; whether the terminal prompts the customer for a selection or applies offers automatically is terminal configuration.

A rebate is an independent loyalty transaction: it has its own `SaleTransactionID` and returns its own `POITransactionID`, separate from any [point redemption](./loyalty-redeem-points.md) or [award](./loyalty-award-points.md) in the same sale. Rebates are committed when the response returns — if the sale is later abandoned, the rebate must be [reversed](./loyalty-reverse-rebate.md).

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. The customer identified on the terminal, or a `LoyaltyID` from a prior [identification](./loyalty-identify-member.md).

---

## Make a rebate request

To apply rebates, send a Terminal API request with `MessageCategory` set to `Loyalty` and `LoyaltyTransactionType` set to `Rebate`. Include the basket so the provider can grant item-level rebates.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `Loyalty`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `LoyaltyRequest` fields:

    - **`SaleData.SaleTransactionID.TransactionID`** — Your reference for this rebate. Each loyalty request is an independent transaction — use a unique value, distinct from any redemption or award in the same sale.
    - **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
    - **`LoyaltyTransaction.LoyaltyTransactionType`** — `Rebate`.
    - **`LoyaltyTransaction.TotalAmount`** — The purchase total the rebate is based on. Must equal the sum of `SaleItem[].ItemAmount`.
    - **`LoyaltyTransaction.Currency`** — The transaction currency code (e.g. `USD`).
    - **`LoyaltyTransaction.SaleItem[]`** — Array of line items, keyed by `ItemID`. Required for item-level rebates. Each item includes:
        - **`ItemID`** — Sequential identifier for the line, starting at `1`.
        - **`ProductCode`** — Merchant SKU.
        - **`ProductLabel`** — Human-readable product description.
        - **`Quantity`** — Number of units.
        - **`UnitPrice`** — Price per unit.
        - **`ItemAmount`** — Line total (`Quantity` × `UnitPrice`).
    - **`LoyaltyData[].LoyaltyAccountID`** *(optional)* — The member's loyalty account. If omitted, the terminal identifies the account itself, falling back through `CardAcquisitionReference`, then `OriginalPOITransaction` with `ReuseCardDataFlag`, and finally the account the customer identified with on the terminal. When supplying it, include:
        - **`LoyaltyID`** — The member's loyalty account ID.
        - **`EntryMode`** — How the member was identified, echoed from the prior step (`Keyed`, `Scanned`, etc.).
        - **`IdentificationType`** — `PAN`, `BarCode`, `PhoneNumber`, or `AccountNumber`.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "Loyalty",
         "MessageType": "Request",
         "ServiceID": "SVC-01140",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "LoyaltyRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "TXN-20260430-00845",
             "TimeStamp": "2026-04-30T14:20:00+00:00"
           }
         },
         "LoyaltyTransaction": {
           "LoyaltyTransactionType": "Rebate",
           "TotalAmount": 64.97,
           "Currency": "USD",
           "SaleItem": [
             {
               "ItemID": 1,
               "ProductCode": "KRK-CNDL-LRG-VAN",
               "ProductLabel": "Large Vanilla Candle",
               "Quantity": 2,
               "UnitPrice": 24.99,
               "ItemAmount": 49.98
             },
             {
               "ItemID": 2,
               "ProductCode": "KRK-FRAME-5X7-BLK",
               "ProductLabel": "5x7 Black Frame",
               "Quantity": 1,
               "UnitPrice": 14.99,
               "ItemAmount": 14.99
             }
           ]
         },
         "LoyaltyData": [
           {
             "LoyaltyAccountID": {
               "EntryMode": "Keyed",
               "IdentificationType": "PAN",
               "LoyaltyID": "98234"
             }
           }
         ]
       }
     }
   }
   ```

2. The request is routed to the terminal. In the terminal-driven flow, the terminal presents the applicable coupons and holds the request open until the customer confirms their selection.

3. The terminal submits the committed selection to the loyalty provider, which computes the rebates and returns them.

---

## Rebate response

The result is returned in the API response in a `LoyaltyResponse` body. The main result is in `LoyaltyResponse.Response.Result`.

### Successful rebate

When the rebate succeeds, your integration receives:

- **`LoyaltyResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — terminal-assigned reference. Keep this — it is required to [reverse the rebate](./loyalty-reverse-rebate.md).
- **`POIData.POITransactionID.TimeStamp`** — timestamp of the transaction.
- **`LoyaltyResult[0].LoyaltyAccount.LoyaltyAccountID.LoyaltyID`** — echo of the member's loyalty ID.
- **`LoyaltyResult[0].LoyaltyAccount.LoyaltyBrand`** — the loyalty program (e.g. `K-Club`).
- **`LoyaltyResult[0].Rebates.TotalRebate`** — discount on the overall purchase, not attached to a specific item.
- **`LoyaltyResult[0].Rebates.RebateLabel`** — short text to print on the receipt for the total rebate.
- **`LoyaltyResult[0].Rebates.SaleItemRebate[]`** — per-item rebates, each keyed to a basket line by `ItemID`:
    - **`ItemAmount`** — money off that line, and/or
    - **`Quantity`** — extra free units of that item.
    - **`RebateLabel`** — short text to print on the receipt in front of the rebate.

  Subtract `TotalRebate` and the per-item amounts from the basket, and print the labels on the receipt.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Loyalty",
        "MessageType": "Response",
        "ServiceID": "SVC-01140",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "LoyaltyResponse": {
        "Response": {
          "Result": "Success"
        },
        "SaleData": {
          "SaleTransactionID": {
            "TransactionID": "TXN-20260430-00845",
            "TimeStamp": "2026-04-30T14:20:00+00:00"
          }
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "POI-LYL-701",
            "TimeStamp": "2026-04-30T14:20:09+00:00"
          }
        },
        "LoyaltyResult": [
          {
            "LoyaltyAccount": {
              "LoyaltyAccountID": {
                "EntryMode": "Keyed",
                "IdentificationType": "PAN",
                "IdentificationSupport": "NoCard",
                "LoyaltyID": "98234"
              },
              "LoyaltyBrand": "K-Club"
            },
            "Rebates": {
              "TotalRebate": 5.00,
              "RebateLabel": "K-Club member offer",
              "SaleItemRebate": [
                {
                  "ItemID": 1,
                  "ProductCode": "KRK-CNDL-LRG-VAN",
                  "ItemAmount": 3.00,
                  "RebateLabel": "15% off candles"
                }
              ]
            }
          }
        ]
      }
    }
  }
  ```

### Failed rebate

When the rebate fails, the result includes:

- **`LoyaltyResponse.Response.Result`** — `Failure`.
- **`LoyaltyResponse.Response.ErrorCondition`** — the reason for failure. For example, `NotFound` if the member is unknown, `NotAllowed` if the member is suspended, or `Aborted` if the customer cancelled the selection on the terminal.

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Complete the sale

Rebates (and any [point redemption](./loyalty-redeem-points.md)) are committed **before** payment. Compute the final amount as:

```
Final amount = TotalAmount − TotalRebate − per-item rebates − redeemed point value
```

and send the [payment](./make-payment.md) with `PaymentData.TransactionConditions.LoyaltyHandling` set to `Processed`, indicating that loyalty has already been handled in standalone loyalty transactions.

If the customer rejects the final amount, reverse each committed loyalty transaction using its `POITransactionID` — see [Reverse a rebate](./loyalty-reverse-rebate.md) and [Reverse a redemption](./loyalty-reverse-redemption.md).

---

## Next steps

- [Redeem loyalty points](./loyalty-redeem-points.md) — debit points from the member's balance as an additional discount.
- [Reverse a rebate](./loyalty-reverse-rebate.md) — undo the rebate if the sale is abandoned.
- [Award loyalty points](./loyalty-award-points.md) — credit points for the purchase after payment.
