---
---

# Check loyalty member status

Read the status flags for a known loyalty member.

A member status check returns the operational flags that govern whether a member can transact — active, suspended, points locked, employee, and enrollment verified. Use it before redeeming or awarding when you need to surface a clear status message to the cashier (for example, "Member suspended — contact customer service"). The check operates on a known `LoyaltyID` from a prior [identification step](./loyalty-identify-member.md) and does not move points or rewards.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. The `LoyaltyID` from a prior [member identification](./loyalty-identify-member.md) or [enrollment](./loyalty-enroll-member.md).

---

## Make a status request

To check a member's status, send a Terminal API request with `MessageCategory` set to `Admin` and `ServiceIdentification` set to `LoyaltyMemberStatus`.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `Admin`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `AdminRequest` fields:

    - **`ServiceIdentification`** — `LoyaltyMemberStatus`.
    - **`LoyaltyAccount.LoyaltyAccountID.LoyaltyID`** — The member's loyalty account ID.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "Admin",
         "MessageType": "Request",
         "ServiceID": "SVC-01170",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "AdminRequest": {
         "ServiceIdentification": "LoyaltyMemberStatus",
         "LoyaltyAccount": {
           "LoyaltyAccountID": {
             "LoyaltyID": "98234"
           }
         }
       }
     }
   }
   ```

2. The request is routed to the terminal. The terminal queries the loyalty provider and returns the status flags.

---

## Status response

The result is returned in the API response in an `AdminResponse` body. The main result is in `AdminResponse.Response.Result`.

### Successful status check

When the status check succeeds, your integration receives:

- **`AdminResponse.Response.Result`** — `Success`.
- **`AdminResponse.LoyaltyAccount.LoyaltyAccountID.LoyaltyID`** — echo of the member's loyalty ID.
- **`AdminResponse.LoyaltyAccount.LoyaltyBrand`** — the loyalty program (e.g. `K-Club`).
- **`AdminResponse.LoyaltyAccount.MemberStatus.Active`** — `true` when the member is active.
- **`AdminResponse.LoyaltyAccount.MemberStatus.Suspended`** — `true` when the member is suspended; no transactions are permitted.
- **`AdminResponse.LoyaltyAccount.MemberStatus.PointsLocked`** — `true` when the member's points cannot be awarded or redeemed.
- **`AdminResponse.LoyaltyAccount.MemberStatus.Employee`** — `true` when the member is flagged as an employee. Useful for applying employee-specific pricing.
- **`AdminResponse.LoyaltyAccount.MemberStatus.EnrollmentVerified`** — `true` when the member has verified the email address on the account.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Admin",
        "MessageType": "Response",
        "ServiceID": "SVC-01170",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "AdminResponse": {
        "Response": {
          "Result": "Success"
        },
        "LoyaltyAccount": {
          "LoyaltyAccountID": {
            "LoyaltyID": "98234"
          },
          "LoyaltyBrand": "K-Club",
          "MemberStatus": {
            "Active": true,
            "Suspended": false,
            "PointsLocked": false,
            "Employee": false,
            "EnrollmentVerified": true
          }
        }
      }
    }
  }
  ```

### Failed status check

When the status check fails, the result includes:

- **`AdminResponse.Response.Result`** — `Failure`.
- **`AdminResponse.Response.ErrorCondition`** — the reason for failure. For example, `NotFound` if the loyalty ID is unknown.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Admin",
        "MessageType": "Response",
        "ServiceID": "SVC-01170",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "AdminResponse": {
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

## Next steps

- [Query a loyalty balance](./loyalty-balance-inquiry.md) — read the member's current point balance.
- [Award loyalty points](./loyalty-award-points.md) — credit points after a sale.
- [Apply loyalty rebates](./loyalty-apply-rebates.md) — apply rewards or coupons.
