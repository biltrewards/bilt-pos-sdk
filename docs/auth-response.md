# Authorization Response

When the terminal processes a payment, the response includes an *authorization result* and an optional *authorization response* string. These values must be mapped to `Response.Result` and `Response.ErrorCondition` fields as well as included in the `Response.AdditionalResponse` field.

## Authorization Result Values

| Value | Description |
|---|---|
| `AUTHORIZED` | Payment approved |
| `DECLINED` | Payment rejected |
| `USER_CANCELLED` | User cancelled |
| `CANCELLED_EXTERNALLY` | Cancelled externally |
| `AUTHORIZED_EXTERNALLY` | Approved via manual/external method (e.g. phone) |
| `CASH_VERIFIED` | Paid by cash |
| `VOIDED` | Payment was voided |
| `VOID_DECLINED` | Void failed |
| `REFUNDED` | Payment was refunded |
| `REFUND_DECLINED` | Refund failed |
| `IN_PROGRESS` | Final status to be obtained later |
| `MERCHANT_ACTION_REQUIRED` | Rejected, merchant action needed |
| `DEVICE_CANCELLED` | Terminal error |
| `HOST_RESPONSE_TIMEOUT` | Host not responding in time |
| `REVERSED` | Authorization incomplete due to timeout, reversal sent to host |

## Authorization Response Text

- Optional string from the payment processor
- Primarily contains failure reason information

## Mapping to Response

The Authorization Result determines the `Result` and `ErrorCondition` on the `Response` object:

| AuthorizationResult | Result | ErrorCondition |
|---|---|---|
| `AUTHORIZED` | `Success` | — |
| `AUTHORIZED_EXTERNALLY` | `Success` | — |
| `CASH_VERIFIED` | `Success` | — |
| `REVERSED` | `Success` | — |
| `VOIDED` | `Success` | — |
| `REFUNDED` | `Success` | — |
| `DECLINED` | `Failure` | `Refusal` |
| `VOID_DECLINED` | `Failure` | `Refusal` |
| `REFUND_DECLINED` | `Failure` | `Refusal` |
| `MERCHANT_ACTION_REQUIRED` | `Failure` | `Refusal` |
| `USER_CANCELLED` | `Failure` | `Cancel` |
| `CANCELLED_EXTERNALLY` | `Failure` | `Cancel` |
| `DEVICE_CANCELLED` | `Failure` | `Cancel` |
| `HOST_RESPONSE_TIMEOUT` | `Failure` | `UnreachableHost` |
| `IN_PROGRESS` | `Failure` | `InProgress` |

