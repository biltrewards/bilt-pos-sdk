---
---

# Payment brands

The `PaymentBrand` field in `CardData` identifies the card network used for a transaction. It appears in the payment response at `PaymentResult.PaymentInstrumentData.CardData.PaymentBrand`.

You can also use the `AllowedPaymentBrand` array in `TransactionConditions` to restrict which brands are accepted for a given transaction.

---

## Possible values

| Value | Description |
|---|---|
| `UNSET_PAYMENT_BRAND` | Default value when no brand is set (instead of null). |
| `UNKNOWN_PAYMENT_BRAND` | Brand is present but not recognized. |
| `AMEX` | American Express |
| `DISCOVER` | Discover |
| `MASTER_CARD` | MasterCard |
| `VISA` | Visa |
| `JCB` | JCB |
| `UPI` | UnionPay International |
| `INTERAC` | Interac |
| `DINERS_CLUB` | Diners Club |
