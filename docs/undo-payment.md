# Cancel, reverse, or refund a payment

Choose the right method for undoing a payment based on its current state.

The Terminal API provides four mechanisms for cancelling or returning funds from a payment. Which one to use depends on whether the payment is still in progress, whether the batch has settled, and whether you have the original transaction reference.

---

## Decision flow

1. **Is the payment still in progress on the terminal?**
   - Yes — use [**Abort**](./cancel-payment.md) to cancel it before it completes.

2. **Has the payment completed but the batch has not yet settled?**
   - Yes — use a [**Reversal**](./reverse-payment.md) to void it before clearing.

3. **Has the batch already settled?**
   - Do you have the original transaction ID?
     - Yes — use a [**Referenced refund**](./refund-referenced.md) to refund against the original payment.
     - No — use an [**Unreferenced refund**](./refund-unreferenced.md) to refund to any presented card.

---

## Comparison

| | Abort | Reversal | Referenced refund | Unreferenced refund |
|---|---|---|---|---|
| **When to use** | Payment in progress | After completion, before batch settles | After batch settles, have original txn ID | After batch settles, no original txn ID |
| **Nexo message** | `AbortRequest` | `ReversalRequest` | `PaymentRequest` with `PaymentType=Refund` | `PaymentRequest` with `PaymentType=Refund` |
| **Links to original?** | Yes (via `ServiceID`) | Yes (via `POITransactionID`) | Yes (via `OriginalPOITransaction`) | No — reads card |
| **Partial amount?** | No | Yes | Yes | N/A (any amount) |
| **Card required?** | No | No | No | Yes |
| **Typical fees** | None | Lower | Standard refund | Standard refund |

---

## Next steps

- [Cancel a payment](./cancel-payment.md) — abort an in-progress payment.
- [Reverse a payment](./reverse-payment.md) — void a completed payment before the batch settles.
- [Referenced refund](./refund-referenced.md) — post-clearing refund linked to the original payment.
- [Unreferenced refund](./refund-unreferenced.md) — refund to any card.
- [Verify payment status](./verify-transaction-status.md) — check the status of a transaction when you don't receive a result.
