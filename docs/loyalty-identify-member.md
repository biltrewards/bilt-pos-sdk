---
---

# Identify a loyalty member

Look up a loyalty member at the terminal and retrieve their active rewards.

Member identification is the first step in any loyalty flow. The terminal prompts the shopper for an identifier — typically an email address, phone number, or a scanned loyalty card — and queries the loyalty provider. On success, the response returns the member's loyalty account ID along with their currently available rewards and coupons so your POS app can offer redemption during checkout.

This operation is a [card acquisition](./card-acquisition-request.md) with `LoyaltyHandling` set to `Required` or `Proposed`. No card data is captured and no funds are moved — only loyalty data is returned.

> **POS-driven alternative:** When the cashier already has the shopper's phone number or account number on file and does not want to prompt at the terminal, send a [BalanceInquiry lookup](./loyalty-balance-inquiry.md#look-up-a-member-by-phone-number-or-account-number) instead. It resolves the identifier into a member and returns the same rewards data.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. Reviewed [Card acquisition](./card-acquisition-request.md) — loyalty identification reuses the same message category.

---

## Make an identification request

To identify a loyalty member, send a Terminal API card acquisition request with `LoyaltyHandling` set to a value that requires loyalty data. The terminal prompts the shopper for their identifier and queries the loyalty provider.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `CardAcquisition`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `CardAcquisitionRequest` fields:

    - **`SaleData.SaleTransactionID.TransactionID`** — Your reference for this acquisition. Use a unique value per transaction — follow-up loyalty requests are independent transactions with their own IDs. They correlate with this acquisition by carrying the returned `LoyaltyID` in `LoyaltyData[].LoyaltyAccountID`, or by referencing it via `LoyaltyData[].CardAcquisitionReference`.
    - **`SaleData.SaleTransactionID.TimeStamp`** — Date and time of the request in UTC format.
    - **`CardAcquisitionTransaction.LoyaltyHandling`** — `Required` to fail when no member is found, or `Proposed` to allow the flow to continue without a member.
    - **`CardAcquisitionTransaction.ForceEntryMode`** *(optional)* — Array restricting how the identifier is captured. Use `["Keyed"]` for email or phone entry, `["Scanned"]` for a loyalty card barcode. Omit to allow any method.
    - **`CardAcquisitionTransaction.AllowedLoyaltyBrand`** *(optional)* — Array of loyalty program identifiers to accept. Omit to accept any.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "CardAcquisition",
         "MessageType": "Request",
         "ServiceID": "SVC-01100",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "CardAcquisitionRequest": {
         "SaleData": {
           "SaleTransactionID": {
             "TransactionID": "TXN-20260430-00847",
             "TimeStamp": "2026-04-30T14:15:00+00:00"
           }
         },
         "CardAcquisitionTransaction": {
           "LoyaltyHandling": "Required",
           "ForceEntryMode": ["Keyed"]
         }
       }
     }
   }
   ```

2. The request is routed to the terminal. The terminal prompts the shopper for their identifier (typed email or phone, scanned loyalty card, etc.) and queries the loyalty provider.

3. The terminal returns the member's loyalty account ID and their active rewards.

---

## Identification response

The result is returned in the API response in a `CardAcquisitionResponse` body. The main result is in `CardAcquisitionResponse.Response.Result`.

### Successful identification

When the member is found, your integration receives:

- **`CardAcquisitionResponse.Response.Result`** — `Success`.
- **`POIData.POITransactionID.TransactionID`** — terminal reference for this acquisition.
- **`POIData.POITransactionID.TimeStamp`** — timestamp of the acquisition.
- **`LoyaltyAccount[]`** — array of loyalty accounts read. For loyalty identification there is typically a single entry. Each entry contains:
    - **`LoyaltyAccountID.LoyaltyID`** — the member's loyalty account identifier. Use this in subsequent loyalty requests.
    - **`LoyaltyAccountID.EntryMode`** — how the identifier was captured (`Keyed`, `Scanned`, etc.).
    - **`LoyaltyAccountID.IdentificationType`** — type of identifier (`PAN`, `BarCode`, `PhoneNumber`, `AccountNumber`).
    - **`LoyaltyAccountID.IdentificationSupport`** — medium that supplied the identifier (`LoyaltyCard`, `MobileApplication`, `NoCard`, `HybridCard`, `LinkedCard`).
    - **`LoyaltyBrand`** *(optional)* — the loyalty program name (e.g. `K-Club`).
- **`Response.AdditionalResponse`** — base64-encoded JSON containing the member's active rewards. Decode and parse to obtain a `rewards` array (each entry has `rewardRef`, `type`, `name`, `expirationDate`) and a `rewardCount`. Use these to show the member's available rewards at the register; rewards and coupons are applied to the sale via a [rebate](./loyalty-apply-rebates.md).

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "CardAcquisition",
        "MessageType": "Response",
        "ServiceID": "SVC-01100",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "CardAcquisitionResponse": {
        "Response": {
          "Result": "Success",
          "AdditionalResponse": "eyJyZXdhcmRzIjpbeyJyZXdhcmRSZWYiOiJyd2Q6UldELTQ0MDIxIiwidHlwZSI6InJld2FyZCIsIm5hbWUiOiIkMTAgT2ZmIFB1cmNoYXNlIiwiZXhwaXJhdGlvbkRhdGUiOiIyMDI2LTA1LTE1VDIzOjU5OjU5WiJ9LHsicmV3YXJkUmVmIjoiY3BuOkNQLTIwMTpDVC0xNU9GRiIsInR5cGUiOiJjb3Vwb24iLCJuYW1lIjoiMTUlIE9mZiBmb3IgR29sZCBNZW1iZXJzIiwiZXhwaXJhdGlvbkRhdGUiOiIyMDI2LTA1LTMxVDIzOjU5OjU5WiJ9XSwicmV3YXJkQ291bnQiOjJ9"
        },
        "SaleData": {
          "SaleTransactionID": {
            "TransactionID": "TXN-20260430-00847",
            "TimeStamp": "2026-04-30T14:15:00+00:00"
          }
        },
        "POIData": {
          "POITransactionID": {
            "TransactionID": "POI-LYL-701",
            "TimeStamp": "2026-04-30T14:15:01+00:00"
          }
        },
        "LoyaltyAccount": [
          {
            "LoyaltyAccountID": {
              "EntryMode": "Keyed",
              "IdentificationType": "PAN",
              "IdentificationSupport": "NoCard",
              "LoyaltyID": "98234"
            },
            "LoyaltyBrand": "K-Club"
          }
        ]
      }
    }
  }
  ```

  After base64-decoding `AdditionalResponse`, the payload looks like:

  ```json
  {
    "rewards": [
      {
        "rewardRef": "rwd:RWD-44021",
        "type": "reward",
        "name": "$10 Off Purchase",
        "expirationDate": "2026-05-15T23:59:59Z"
      },
      {
        "rewardRef": "cpn:CP-201:CT-15OFF",
        "type": "coupon",
        "name": "15% Off for Gold Members",
        "expirationDate": "2026-05-31T23:59:59Z"
      }
    ],
    "rewardCount": 2
  }
  ```

> **Note:** Cache the `LoyaltyID` and the decoded rewards in your POS session — subsequent loyalty messages reference them by ID.

### Failed identification

When identification fails, the result includes:

- **`CardAcquisitionResponse.Response.Result`** — `Failure`.
- **`CardAcquisitionResponse.Response.ErrorCondition`** — the reason for failure. `NotFound` when no member matches the captured identifier; `NotAllowed` when the member is suspended or inactive; `Cancel` when the shopper aborts the lookup.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "CardAcquisition",
        "MessageType": "Response",
        "ServiceID": "SVC-01100",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "CardAcquisitionResponse": {
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "NotFound",
          "AdditionalResponse": "No loyalty member found for the supplied identifier."
        }
      }
    }
  }
  ```

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Apply loyalty rebates](./loyalty-apply-rebates.md) — apply rewards or coupons to the sale.
- [Redeem loyalty points](./loyalty-redeem-points.md) — apply the member's point balance as a discount.
- [Award loyalty points](./loyalty-award-points.md) — credit points after payment completes.
- [Query a loyalty balance](./loyalty-balance-inquiry.md) — fetch the member's current point balance.
- [Enroll a new member](./loyalty-enroll-member.md) — sign up a shopper when no member is found.
