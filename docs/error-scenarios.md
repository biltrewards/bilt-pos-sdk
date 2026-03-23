---
---

# Error scenarios

How to handle errors and edge cases that can occur during payment processing.

When integrating with the Terminal API, your POS application should handle several categories of errors: malformed requests, declined payments, terminal issues, and communication failures. This guide covers each scenario and the recommended action.

For a full list of error conditions and their meanings, see [Refusal reasons](./refusal-reasons.md).

---

## Response structure

Every Terminal API response includes a `Response` object with:

- **`Result`** — `Success`, `Failure`, or `Partial`.
- **`ErrorCondition`** — The failure category. Present when `Result` is `Failure`.
- **`AdditionalResponse`** — Human-readable detail or acquirer data. Mandatory when `Result` is `Failure`.

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "Refusal",
    "AdditionalResponse": "Insufficient funds"
  }
}
```

---

## Declined payments

A declined payment has `Result: Failure` with an `ErrorCondition` indicating why.

### Refusal (issuer declined)

The acquirer or card issuer refused the transaction.

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "Refusal",
    "AdditionalResponse": "Insufficient funds"
  }
}
```

**Action:** Do not retry with the same card. Ask the shopper to use a different payment method.

### Refusal (issuer declined) — additional examples

A declined transaction with the generic acquirer message:

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "Refusal",
    "AdditionalResponse": "Transaction declined."
  }
}
```

### Cancel (shopper cancelled or timed out)

The shopper pressed cancel, removed the card early, or did not act within the 30-second user action timeout. See [Timeout handling](./timeout-handling.md) for full details on timeout scenarios.

Shopper pressed cancel or signature timed out:

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "Cancel",
    "AdditionalResponse": "Transaction cancelled."
  }
}
```

User action timed out (e.g., card not presented within 30 seconds, or invalid card presented and timer expired):

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "Cancel",
    "AdditionalResponse": "Transaction timeout."
  }
}
```

Card removed before the transaction completed:

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "Cancel",
    "AdditionalResponse": "Card removed.\nTransaction cancelled."
  }
}
```

**Action:** No funds were captured. The payment can be safely retried. Ask the shopper if they want to try again.

### Abort (POS cancelled)

Your POS application sent an abort request to cancel the in-progress payment. See [Cancel a payment](./cancel-payment.md).

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "Abort",
    "AdditionalResponse": "Transaction cancelled"
  }
}
```

**Action:** Retry if the cancellation was unintentional. Note that if the payment completed before the abort was processed, you will receive `Result: Success` instead.

---

## Terminal errors

These errors indicate a problem with the terminal itself rather than the transaction.

### DeviceOut (terminal unavailable)

The terminal is unreachable, disconnected, or experienced a hardware error.

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "DeviceOut",
    "AdditionalResponse": "Device connection lost"
  }
}
```

**Action:** Check the terminal connection. If the connection was lost mid-transaction, the terminal automatically resets to a disconnected state and must be reconnected. Once reconnected, retry the payment.

### Busy (terminal occupied)

The terminal is currently processing another transaction or command.

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "Busy"
  }
}
```

**Action:** Wait for the current operation to complete, then retry.

### LoggedOut (session expired)

The terminal session has expired and the terminal needs to re-authenticate.

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "LoggedOut"
  }
}
```

**Action:** The application should re-login to the terminal automatically, then retry the payment.

---

## Communication errors

### UnreachableHost (acquirer unreachable or processing timeout)

The terminal could not reach the acquirer or payment processor, or the authorization request timed out. The processing timeout is 30 seconds (not configurable). See [Timeout handling](./timeout-handling.md) for details.

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "UnreachableHost",
    "AdditionalResponse": "Command timed out"
  }
}
```

**Action:** Check network connectivity. Retry after a short delay. If the problem persists, the network or acquirer may be down.

> **Important:** When a processing timeout occurs, the authorization request was sent but no response was received — the payment may or may not have been processed by the acquirer. Always verify the transaction status before retrying to avoid duplicate charges.

---

## Request errors

These errors indicate a problem with the request itself.

### MessageFormat (invalid request)

The request contained malformed or missing parameters.

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "MessageFormat",
    "AdditionalResponse": "Invalid arguments"
  }
}
```

**Action:** Do not retry with the same parameters. Fix the request and try again.

### NotAllowed (operation not permitted)

The requested operation is not allowed in the current terminal state or configuration. This covers cases like sending a payment before login, unsupported commands, or configuration issues.

```json
{
  "Response": {
    "Result": "Failure",
    "ErrorCondition": "NotAllowed",
    "AdditionalResponse": "No active session"
  }
}
```

**Action:** Do not retry the same request. This usually indicates a setup or integration issue. Check the terminal state, ensure login is complete, and verify configuration.

---

## No response received (request timeout)

If your POS application does not receive a payment response (for example, due to a network interruption or application crash), the transaction may or may not have been processed. This is the "request timeout" scenario — see [Timeout handling](./timeout-handling.md) for full details.

**Action:**

1. Do **not** assume the payment failed — it may have been approved.
2. Do **not** retry the payment immediately — this could result in a duplicate charge.
3. Use [Verify payment status](./verify-transaction-status.md) to determine the outcome.
4. Only retry or refund based on the verified status.

---

## Error handling summary

| ErrorCondition | Category | Retryable | Recommended action |
|---|---|---|---|
| Refusal | Declined | No | Use a different payment method |
| Cancel | Declined | Yes | Ask shopper to retry |
| Abort | Declined | Yes | Retry if unintentional |
| DeviceOut | Terminal | Yes | Reconnect terminal, then retry |
| Busy | Terminal | Yes | Wait, then retry |
| LoggedOut | Terminal | Yes | Re-login, then retry |
| UnreachableHost | Communication | Yes | Check network, retry after delay |
| MessageFormat | Request | No | Fix the request parameters |
| NotAllowed | Request | No | Fix integration/configuration |
| InvalidCard | Declined | Depends | Re-present card or use different card |
| WrongPIN | Declined | Yes (limited) | Ask shopper to re-enter PIN |
| PaymentRestriction | Declined | No | Use a different payment method |

---

## Next steps

- [Timeout handling](./timeout-handling.md) — detailed guide to the three timeout scenarios.
- [Refusal reasons](./refusal-reasons.md) — full reference for all error conditions and their causes.
- [Cancel a payment](./cancel-payment.md) — abort an in-progress transaction.
- [Cancel, reverse, or refund a payment](./undo-payment.md) — return funds for a completed payment.
- [Verify payment status](./verify-transaction-status.md) — check the outcome when no response is received.
