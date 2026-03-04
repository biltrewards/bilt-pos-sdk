# Payment Error Conditions

When a payment fails, the `PaymentResponse.Response` includes an `ErrorCondition` that classifies the failure and an `AdditionalResponse` with a human-readable message. This document describes each error condition, its causes, and recommended actions.

## Error Condition Reference

### Refusal

Transaction refused by the acquirer, card issuer, or card rules.

| Source | Detail | AdditionalResponse | Retryable |
|--------|--------|-------------------|-----------|
| Acquirer declined | Issuer refused the transaction (insufficient funds, limit exceeded, etc.) | `Transaction declined.` or specific reason | No |
| Card rules | Card restrictions prevent this transaction type | Varies | No |
| Unknown non-approved result | Authorization completed but result was not AUTHORIZED | Varies | No |

**Verifone StatusCode:** `GENERAL_ERROR` (catch-all for unclassified failures)
**Auth results:** `DECLINED`, or any non-`AUTHORIZED`/non-`CANCELLED` result

**Recommended action:** Do not retry. Ask the shopper to use a different payment method or contact their bank.

---

### Cancel

The transaction was cancelled, either by the customer on the terminal, by the payment application, or by a user action timeout. See [Timeout handling](./timeout-handling.md) for timeout details.

| Source | Detail | AdditionalResponse | Retryable |
|--------|--------|-------------------|-----------|
| Customer cancelled | Shopper pressed cancel on the PIN pad or terminal | `Transaction cancelled.` | Yes |
| Signature timeout | Shopper did not sign within 30 seconds | `Transaction cancelled.` | Yes |
| Card not presented | Shopper did not present card within 30 seconds | `Transaction timeout.` | Yes |
| Invalid card timeout | Invalid card presented (e.g., expired), timer restarted and expired | `Transaction timeout.` | Yes |
| Card removed | Card removed before transaction completed | `Card removed.\nTransaction cancelled.` | Yes |
| Application cancelled | Payment application cancelled the transaction | — | Yes |

**Verifone StatusCode:** `CANCELLED`
**Auth result:** `CANCELLED`
**User action timeout:** 30 seconds (not configurable)

**Recommended action:** No funds were captured. The transaction can be safely retried. Ask the shopper if they want to try again.

---

### Abort

The Sale System (register) sent an explicit abort request to cancel the in-progress transaction.

| Source | Detail | Retryable |
|--------|--------|-----------|
| Register abort | POS application requested transaction cancellation | Yes |

**Verifone StatusCode:** `ABORTED`

**Recommended action:** Retry if the abort was unintentional. This is typically initiated by the register application.

---

### DeviceOut

The payment terminal is unavailable due to a connection issue or hardware error. This may be temporary (connection lost) or permanent (device failure).

| Source | Detail | Retryable |
|--------|--------|-----------|
| Device not found | Terminal not discovered on the network | Yes |
| Device not ready | Terminal is powered on but not ready | Yes |
| Connection failed | Could not establish connection to terminal | Yes |
| Connection lost | Connection dropped during transaction | Yes |
| Device error | Terminal reported a hardware/software error | Yes |
| Pairing rejected | Terminal rejected the pairing request | No |

**Verifone StatusCodes:** `DEVICE_NOT_FOUND`, `DEVICE_NOT_READY`, `DEVICE_CONNECTION_FAILED`, `DEVICE_CONNECTION_LOST`, `DEVICE_ERROR`, `DEVICE_REJECTED_PAIRING`

**Recommended action:** Check the terminal connection. For transient errors (connection lost, not ready), retry after reconnecting. For persistent errors, the terminal may need to be restarted or replaced.

> **Note:** `DEVICE_CONNECTION_LOST` triggers an automatic teardown and state reset to `Disconnected`. The terminal must be reconnected before retrying.

---

### Busy

The terminal or payment system is currently processing another request.

| Source | Detail | Retryable |
|--------|--------|-----------|
| Device busy | Terminal is handling another transaction | Yes |
| Command in progress | A previous command has not yet completed | Yes |

**Verifone StatusCodes:** `DEVICE_BUSY`, `COMMAND_IN_PROGRESS`

**Recommended action:** Wait for the current operation to complete, then retry.

---

### NotAllowed

The requested operation is not permitted in the current state or configuration.

| Source | Detail | Retryable |
|--------|--------|-----------|
| Not allowed | Operation rejected by the SDK | No |
| Invalid state | Terminal is not in the correct state for this operation | No |
| Invalid configuration | Terminal configuration is incorrect | No |
| Configuration required | Terminal setup is incomplete | No |
| Unsupported command | The requested operation is not supported | No |
| Missing listener | Internal SDK setup issue | No |
| Receipt support required | Receipt handler not configured | No |
| Configuration mismatch | Cached configuration does not match | No |

**Verifone StatusCodes:** `NOT_ALLOWED`, `INVALID_STATE`, `INVALID_CONFIGURATION`, `CONFIGURATION_REQUIRED`, `UNSUPPORTED_COMMAND`, `MISSING_LISTENER`, `RECEIPT_SUPPORT_REQUIRED`, `CACHED_CONFIGURATION_MISMATCH`

**Recommended action:** Do not retry the same request. This typically indicates a setup or integration issue that must be resolved before the operation can succeed.

---

### UnreachableHost

The acquirer or payment processor could not be reached, or the authorization request timed out. See [Timeout handling](./timeout-handling.md) for timeout details.

| Source | Detail | Retryable |
|--------|--------|-----------|
| Contact acquirer | Acquirer communication failure | Yes |
| Processing timeout | Authorization request sent but no response within timeout period | Yes |

**Verifone StatusCodes:** `CONTACT_ACQUIRER`, `COMMAND_TIMED_OUT`
**Processing timeout:** 30 seconds (not configurable)

**Recommended action:** Check network connectivity. Retry after a short delay. If the problem persists, the network or acquirer service may be down.

> **Important:** When a processing timeout occurs, the authorization request was sent to the acquirer but no response was received. The payment may or may not have been processed. Always verify the transaction status before retrying to avoid duplicate charges.

---

### LoggedOut

The terminal session has expired or the terminal is not logged in.

| Source | Detail | Retryable |
|--------|--------|-----------|
| Reauthentication required | Session expired, terminal needs to log in again | Yes |

**Verifone StatusCode:** `REAUTHENTICATION_REQUIRED`

**Recommended action:** The application should automatically re-authenticate (login) to the terminal before retrying the transaction.

---

### MessageFormat

The request contained invalid or malformed parameters.

| Source | Detail | Retryable |
|--------|--------|-----------|
| Invalid arguments | Request parameters are malformed or missing | No |

**Verifone StatusCode:** `INVALID_ARGUMENTS`

**Recommended action:** Do not retry with the same parameters. Fix the request and try again.

---

### InvalidCard

The card could not be read or is not accepted. This error condition originates from the authorization result within a successful SDK status — the terminal completed processing but the card itself was the problem.

| Source | Detail | Retryable |
|--------|--------|-----------|
| Card not readable | Card could not be read by the terminal | Yes |
| Card not accepted | Card type is not supported | No |
| Card expired | Card has passed its expiration date | No |

**Verifone StatusCode:** N/A (comes from authorization result, not StatusCode)

**Recommended action:** Ask the shopper to re-insert/re-tap the card (if unreadable) or use a different card.

---

### WrongPIN

PIN verification failed.

| Source | Detail | Retryable |
|--------|--------|-----------|
| Incorrect PIN | Shopper entered the wrong PIN | Yes (limited) |

**Verifone StatusCode:** N/A (comes from authorization result, not StatusCode)

**Recommended action:** Ask the shopper to try again. After multiple failed attempts, the card may be blocked by the issuer.

---

### PaymentRestriction

The card is restricted from purchasing certain products or services.

| Source | Detail | Retryable |
|--------|--------|-----------|
| Product restriction | Card cannot be used for this product category | No |

**Verifone StatusCode:** N/A (comes from authorization result, not StatusCode)

**Recommended action:** Do not retry with the same card. Ask the shopper to use a different payment method.
