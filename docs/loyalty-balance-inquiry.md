---
---

# Query a loyalty balance or look up a member

Read a loyalty member's point balance — or resolve a phone number or account number into a member and their active rewards.

A loyalty balance inquiry runs in one of two modes depending on `IdentificationType` in the request. With `PAN`, the terminal returns the available point balance for a member that has already been identified. With `PhoneNumber` or `AccountNumber`, the terminal performs a member lookup using an identifier the POS already has on file — useful when the cashier doesn't want to ask the shopper to re-identify at the terminal — and returns the resolved member, their balance, and their active rewards. In either mode no points are moved.

---

## Lookup modes

| `IdentificationType` | Behavior | Notes |
|---|---|---|
| `PAN` | Balance check for a known member. | `LoyaltyID` must be the resolved member ID from a prior [identification](./loyalty-identify-member.md) or enrollment. |
| `PhoneNumber` | Member lookup. Returns balance plus rewards. | `LoyaltyID` carries the phone number. |
| `AccountNumber` | Member lookup. Returns balance plus rewards. | `LoyaltyID` carries the account number (typically an email or external account ID). |

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. For a balance check: the `LoyaltyID` (member ID) from a prior [member identification](./loyalty-identify-member.md) or [enrollment](./loyalty-enroll-member.md).
4. For a member lookup: the shopper's phone number or account number on file in your POS.

---

## Check a balance for a known member

Use this mode when you already have the resolved `LoyaltyID` and just need the current balance.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `BalanceInquiry`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `BalanceInquiryRequest` fields:

    - **`LoyaltyAccountReq.LoyaltyAccountID.LoyaltyID`** — The member's loyalty account ID.
    - **`LoyaltyAccountReq.LoyaltyAccountID.IdentificationType`** — `PAN`.
    - **`LoyaltyAccountReq.LoyaltyAccountID.EntryMode`** — Echoed from the prior identification (`Keyed`, `Scanned`, etc.).

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "BalanceInquiry",
         "MessageType": "Request",
         "ServiceID": "SVC-01150",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "BalanceInquiryRequest": {
         "LoyaltyAccountReq": {
           "LoyaltyAccountID": {
             "EntryMode": "Keyed",
             "IdentificationType": "PAN",
             "LoyaltyID": "98234"
           }
         }
       }
     }
   }
   ```

2. The request is routed to the terminal. The terminal queries the loyalty provider and returns the result.

### Successful balance check

When the inquiry succeeds, your integration receives:

- **`BalanceInquiryResponse.Response.Result`** — `Success`.
- **`LoyaltyAccountStatus.LoyaltyAccount.LoyaltyAccountID.LoyaltyID`** — echo of the member's loyalty ID.
- **`LoyaltyAccountStatus.LoyaltyAccount.LoyaltyAccountID.IdentificationSupport`** — medium that supplied the identifier (`LoyaltyCard`, `MobileApplication`, `NoCard`, `HybridCard`, `LinkedCard`).
- **`LoyaltyAccountStatus.LoyaltyAccount.LoyaltyBrand`** — the loyalty program (e.g. `K-Club`).
- **`LoyaltyAccountStatus.LoyaltyAccount.CurrentBalance`** — the member's available point balance.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "BalanceInquiry",
        "MessageType": "Response",
        "ServiceID": "SVC-01150",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "BalanceInquiryResponse": {
        "Response": {
          "Result": "Success"
        },
        "LoyaltyAccountStatus": {
          "LoyaltyAccount": {
            "LoyaltyAccountID": {
              "EntryMode": "Keyed",
              "IdentificationType": "PAN",
              "IdentificationSupport": "NoCard",
              "LoyaltyID": "98234"
            },
            "LoyaltyBrand": "K-Club",
            "CurrentBalance": 1240
          }
        }
      }
    }
  }
  ```

> **Note:** Lifetime and pending balances are not part of the standard nexo response. When the loyalty provider returns them, they are carried in `Response.AdditionalResponse` as URL-encoded fields (e.g. `lifetimeBalance=8750&pendingBalance=0`).

### Failed balance check

When an inquiry fails, the result includes:

- **`BalanceInquiryResponse.Response.Result`** — `Failure`.
- **`BalanceInquiryResponse.Response.ErrorCondition`** — the reason for failure. For example, `NotFound` if the loyalty ID is unknown, or `NotAllowed` if the member is suspended.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "BalanceInquiry",
        "MessageType": "Response",
        "ServiceID": "SVC-01150",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "BalanceInquiryResponse": {
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "NotFound",
          "AdditionalResponse": "No loyalty member with ID 98234."
        }
      }
    }
  }
  ```

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Look up a member by phone number or account number

Use this mode when the POS already has the shopper's identifier on file and you want to resolve it into a `LoyaltyID` without prompting the shopper at the terminal. The response carries the resolved member, their balance, and their active rewards — the same data returned by a [CardAcquisition member identification](./loyalty-identify-member.md).

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `BalanceInquiry`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `BalanceInquiryRequest` fields:

    - **`LoyaltyAccountReq.LoyaltyAccountID.LoyaltyID`** — The phone number or account number to resolve.
    - **`LoyaltyAccountReq.LoyaltyAccountID.IdentificationType`** — `PhoneNumber` or `AccountNumber`.
    - **`LoyaltyAccountReq.LoyaltyAccountID.EntryMode`** — `File` when the identifier is loaded from a POS profile, or `Keyed` when the cashier typed it.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "BalanceInquiry",
         "MessageType": "Request",
         "ServiceID": "SVC-01151",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "BalanceInquiryRequest": {
         "LoyaltyAccountReq": {
           "LoyaltyAccountID": {
             "EntryMode": "File",
             "IdentificationType": "PhoneNumber",
             "LoyaltyID": "555-867-5309"
           }
         }
       }
     }
   }
   ```

2. The request is routed to the terminal. The terminal resolves the identifier with the loyalty provider and returns the matched member.

### Successful lookup

When the lookup succeeds, your integration receives:

- **`BalanceInquiryResponse.Response.Result`** — `Success`.
- **`LoyaltyAccountStatus.LoyaltyAccount.LoyaltyAccountID.LoyaltyID`** — the **resolved member ID** (not the original phone number or account number). Cache this and use it for subsequent loyalty requests.
- **`LoyaltyAccountStatus.LoyaltyAccount.LoyaltyAccountID.IdentificationType`** — flips to `PAN` to reflect the resolved member ID.
- **`LoyaltyAccountStatus.LoyaltyAccount.LoyaltyBrand`** — the loyalty program (e.g. `K-Club`).
- **`LoyaltyAccountStatus.LoyaltyAccount.CurrentBalance`** — the member's available point balance.
- **`Response.AdditionalResponse`** — base64-encoded JSON containing the member's active rewards. Decode and parse to obtain a `rewards` array (each entry has `rewardRef`, `type`, `name`, `expirationDate`) and a `rewardCount`. The `rewardRef` values are required for [reward redemption](./loyalty-redeem-rewards.md).

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "BalanceInquiry",
        "MessageType": "Response",
        "ServiceID": "SVC-01151",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "BalanceInquiryResponse": {
        "Response": {
          "Result": "Success",
          "AdditionalResponse": "eyJyZXdhcmRzIjpbeyJyZXdhcmRSZWYiOiJyd2Q6UldELTQ0MDIxIiwidHlwZSI6InJld2FyZCIsIm5hbWUiOiIkMTAgT2ZmIFB1cmNoYXNlIiwiZXhwaXJhdGlvbkRhdGUiOiIyMDI2LTA1LTE1VDIzOjU5OjU5WiJ9LHsicmV3YXJkUmVmIjoiY3BuOkNQLTIwMTpDVC0xNU9GRiIsInR5cGUiOiJjb3Vwb24iLCJuYW1lIjoiMTUlIE9mZiBmb3IgR29sZCBNZW1iZXJzIiwiZXhwaXJhdGlvbkRhdGUiOiIyMDI2LTA1LTMxVDIzOjU5OjU5WiJ9XSwicmV3YXJkQ291bnQiOjJ9"
        },
        "LoyaltyAccountStatus": {
          "LoyaltyAccount": {
            "LoyaltyAccountID": {
              "EntryMode": "File",
              "IdentificationType": "PAN",
              "IdentificationSupport": "NoCard",
              "LoyaltyID": "98234"
            },
            "LoyaltyBrand": "K-Club",
            "CurrentBalance": 1240
          }
        }
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

> **Note:** Treat the response's `LoyaltyID` (now `PAN`-typed) as the canonical member ID. The original phone or account number is not echoed back — cache it on your side if you need to remember which identifier was used.

### Failed lookup

When the lookup fails, the result includes:

- **`BalanceInquiryResponse.Response.Result`** — `Failure`.
- **`BalanceInquiryResponse.Response.ErrorCondition`** — the reason for failure. For example, `NotFound` when no member matches the supplied identifier, or `NotAllowed` when the matched member is suspended.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "BalanceInquiry",
        "MessageType": "Response",
        "ServiceID": "SVC-01151",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "BalanceInquiryResponse": {
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "NotFound",
          "AdditionalResponse": "No loyalty member found for the supplied phone number."
        }
      }
    }
  }
  ```

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Identify a loyalty member](./loyalty-identify-member.md) — capture a `LoyaltyID` by prompting the shopper at the terminal.
- [Redeem loyalty rewards](./loyalty-redeem-rewards.md) — apply rewards returned by a lookup.
- [Check member status](./loyalty-member-status.md) — verify the member is active before transacting.
- [Award loyalty points](./loyalty-award-points.md) — credit points for a new purchase.
