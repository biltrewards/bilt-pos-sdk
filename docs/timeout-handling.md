# Timeout handling

How to handle the different timeout scenarios that can occur during payment processing.

During a payment flow, three types of timeouts can occur: a processing timeout while waiting for the acquirer, a user action timeout while waiting for the shopper, and a request timeout on the POS side. Each produces a different response and requires a different recovery strategy.

For the full list of error conditions, see [Refusal reasons](./refusal-reasons.md).

---

## Overview

| Timeout | Duration | Configurable | ErrorCondition | Who times out |
|---|---|---|---|---|
| [Processing timeout](#processing-timeout) | 30 seconds | No | `UnreachableHost` | Terminal waiting for acquirer |
| [User action timeout](#user-action-timeout) | 30 seconds | No | `Cancel` | Terminal waiting for shopper |
| [Request timeout](#request-timeout) | 120 seconds | Yes | None (no response) | POS waiting for terminal |

---

## Processing timeout

The terminal sent the authorization request to the acquirer but did not receive a response within the allowed time.

### When it happens

After the shopper presents their card and the terminal sends the transaction to the acquirer for authorization, the acquirer (or the network between the terminal and acquirer) does not respond within the timeout period.

**Timeout duration:** 30 seconds (not configurable).

### Response

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "UnreachableHost",
    "AdditionalResponse": "Command timed out"
  }
}
```

### Recommended action

1. Check network connectivity between the terminal and the acquirer.
2. Retry the payment after a short delay.
3. If the problem persists, the acquirer service may be experiencing issues.

> **Important:** When a processing timeout occurs, the payment may or may not have been processed by the acquirer. The authorization request was sent but no response was received. Always verify the transaction status before retrying to avoid duplicate charges.

---

## User action timeout

The terminal is waiting for the shopper to perform an action (present card, enter PIN, provide signature), but the shopper does not act within the allowed time.

### When it happens

The terminal displays a prompt to the shopper and waits for interaction. If the shopper does not respond within 30 seconds, the terminal cancels the transaction automatically.

**Timeout duration:** 30 seconds (not configurable).

Common scenarios:

| Scenario | Terminal prompt | AdditionalResponse |
|---|---|---|
| Card not presented | "Present card" / "Insert, tap, or swipe" | `Transaction timeout.` |
| PIN not entered | "Enter PIN" | `Transaction timeout.` |
| Signature not provided | "Sign here" | `Transaction cancelled.` |
| Card removed early | "Do not remove card" | `Card removed.\nTransaction cancelled.` |
| Shopper pressed cancel | "Enter PIN" / any prompt | `Transaction cancelled.` |
| Invalid card presented | "Present card" (timer restarts) | `Transaction timeout.` |

> **Note:** When an invalid card is presented (e.g., expired card), the terminal rejects the card and restarts the 30-second timer, giving the shopper another chance to present a valid card. If the timer expires without a valid card, the transaction times out.

### Response

All user action timeouts produce the same error condition:

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "Cancel",
    "AdditionalResponse": "Transaction timeout."
  }
}
```

Shopper-initiated cancellations (pressing cancel button, removing card) are also reported as `Cancel`:

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "Cancel",
    "AdditionalResponse": "Transaction cancelled."
  }
}
```

### Recommended action

The transaction was not processed — no funds were captured. The POS can safely retry the payment without risk of duplicate charges. Ask the shopper if they want to try again.

---

## Request timeout

The POS application sends a payment request to the terminal but does not receive a response within its own timeout period.

### When it happens

This is not a terminal-level timeout — it occurs on the POS side. The POS sent the payment request and is waiting for a response, but the response does not arrive within the configured timeout period.

**Default timeout:** 120 seconds (configurable).

This can happen due to:

- A network interruption between the POS and terminal
- The POS application crashing or restarting
- The terminal taking longer than expected to process

Unlike the other timeouts, the POS does **not** receive a `PaymentResponse` in this scenario.

### Response

No response is received. The POS must assume the transaction status is unknown.

### Recommended action

1. Do **not** assume the payment failed — it may have been approved by the acquirer.
2. Do **not** retry the payment immediately — this could result in a duplicate charge.
3. Use transaction status verification to determine the outcome of the original payment.
4. Only retry or refund based on the verified status.

> **Important:** This is the most dangerous timeout scenario because the payment may have completed successfully on the terminal side. Never retry without first verifying the status of the original transaction.

---

## Timeout summary

```
POS                    Terminal                  Acquirer
 |                       |                        |
 |--- PaymentRequest --->|                        |
 |                       |--- Authorization ------>|
 |                       |                        |
 |  Request timeout      |  Processing timeout    |
 |  (no response)        |  (no acquirer response)|
 |                       |                        |
 |                       |  User action timeout   |
 |                       |  (shopper inaction)    |
```

| Timeout | Duration | Payment processed? | Safe to retry? | ErrorCondition |
|---|---|---|---|---|
| Processing timeout | 30s | Unknown | No — verify first | `UnreachableHost` |
| User action timeout | 30s | No | Yes | `Cancel` |
| Request timeout | 120s (configurable) | Unknown | No — verify first | None (no response) |

---

## Next steps

- [Error scenarios](./error-scenarios.md) — full guide to handling payment errors.
- [Refusal reasons](./refusal-reasons.md) — reference for all error conditions.
- [Cancel a payment](./cancel-payment.md) — abort an in-progress transaction.
