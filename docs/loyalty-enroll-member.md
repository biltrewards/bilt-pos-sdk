---
---

# Enroll a loyalty member

Sign up a new loyalty member from the terminal.

Enrollment creates a member in the loyalty provider using the shopper's profile details — at minimum a name and email, and optionally a phone number. On success, the response returns a freshly assigned `LoyaltyID` that you can use immediately for [redemption](./loyalty-redeem-rewards.md) or [awarding points](./loyalty-award-points.md) within the same sale. Enrollment is typically offered as a fallback when a [member lookup](./loyalty-identify-member.md) returns no match.

---

## Before you begin

Make sure you have:

1. Ordered a terminal and boarded it to a store.
2. Read and understood the [Integration Guide](./integration.md).
3. Collected the shopper's first name, last name, and email. Phone number is optional.

---

## Make an enrollment request

To enroll a member, send a Terminal API request with `MessageCategory` set to `Admin` and `ServiceIdentification` set to `LoyaltyEnrollment`.

1. Send a Terminal API request with the following `MessageHeader` fields:

    - **`ProtocolVersion`** — `3.0`
    - **`MessageClass`** — `Service`
    - **`MessageCategory`** — `Admin`
    - **`MessageType`** — `Request`
    - **`ServiceID`** — Unique ID, 1–10 alphanumeric characters, unique within 48 hours per terminal.
    - **`SaleID`** — Your POS system identifier.
    - **`POIID`** — Target terminal ID, format: `[model]-[serial]`.

   And the following `AdminRequest` fields:

    - **`ServiceIdentification`** — `LoyaltyEnrollment`.
    - **`LoyaltyAccount.MemberProfile.FirstName`** — Shopper's first name.
    - **`LoyaltyAccount.MemberProfile.LastName`** — Shopper's last name.
    - **`LoyaltyAccount.MemberProfile.Email`** — Shopper's email address. Must be unique within the loyalty program.
    - **`LoyaltyAccount.MemberProfile.Phone`** *(optional)* — Shopper's phone number.

   Example request:

   ```json
   {
     "SaleToPOIRequest": {
       "MessageHeader": {
         "ProtocolVersion": "3.0",
         "MessageClass": "Service",
         "MessageCategory": "Admin",
         "MessageType": "Request",
         "ServiceID": "SVC-01160",
         "SaleID": "BiltPOS-Lane3",
         "POIID": "VictaLane-275839164"
       },
       "AdminRequest": {
         "ServiceIdentification": "LoyaltyEnrollment",
         "LoyaltyAccount": {
           "MemberProfile": {
             "FirstName": "Jane",
             "LastName": "Doe",
             "Email": "jane.doe@example.com",
             "Phone": "555-867-5309"
           }
         }
       }
     }
   }
   ```

2. The request is routed to the terminal. The terminal submits the profile to the loyalty provider.

3. The loyalty provider creates the member and returns the new `LoyaltyID`.

---

## Enrollment response

The result is returned in the API response in an `AdminResponse` body. The main result is in `AdminResponse.Response.Result`.

### Successful enrollment

When enrollment succeeds, your integration receives:

- **`AdminResponse.Response.Result`** — `Success`.
- **`AdminResponse.LoyaltyAccount.LoyaltyAccountID.LoyaltyID`** — the newly assigned loyalty account ID. Cache this in your POS session.
- **`AdminResponse.LoyaltyAccount.LoyaltyBrand`** — the loyalty program (e.g. `K-Club`).
- **`AdminResponse.LoyaltyAccount.MemberProfile`** — the stored profile fields echoed back from the provider.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Admin",
        "MessageType": "Response",
        "ServiceID": "SVC-01160",
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
          "MemberProfile": {
            "FirstName": "Jane",
            "LastName": "Doe",
            "Email": "jane.doe@example.com",
            "Phone": "555-867-5309"
          }
        }
      }
    }
  }
  ```

### Failed enrollment

When enrollment fails, the result includes:

- **`AdminResponse.Response.Result`** — `Failure`.
- **`AdminResponse.Response.ErrorCondition`** — the reason for failure. The most common is `Refusal` when a member with the same email already exists; `MessageFormat` when a required profile field is missing.
- **`AdminResponse.Response.AdditionalResponse`** *(optional)* — when the failure is a duplicate email, the existing member's ID is returned as a URL-encoded field (e.g. `reason=DUPLICATE_EMAIL&existingMemberId=98234`). Use this to recover by switching the flow to [identify](./loyalty-identify-member.md) the existing member instead of enrolling.

  Example response:

  ```json
  {
    "SaleToPOIResponse": {
      "MessageHeader": {
        "ProtocolVersion": "3.0",
        "MessageClass": "Service",
        "MessageCategory": "Admin",
        "MessageType": "Response",
        "ServiceID": "SVC-01160",
        "SaleID": "BiltPOS-Lane3",
        "POIID": "VictaLane-275839164"
      },
      "AdminResponse": {
        "Response": {
          "Result": "Failure",
          "ErrorCondition": "Refusal",
          "AdditionalResponse": "reason=DUPLICATE_EMAIL&existingMemberId=98234"
        }
      }
    }
  }
  ```

For general guidance on handling failed requests, see [Handle responses](./error-scenarios.md).

---

## Next steps

- [Award loyalty points](./loyalty-award-points.md) — credit points to the new member for the current sale.
- [Redeem loyalty rewards](./loyalty-redeem-rewards.md) — apply any welcome rewards the program issues at enrollment.
- [Check member status](./loyalty-member-status.md) — verify the new member is active before transacting.
