---
---

# Payment brands

The `PaymentBrand` field in `CardData` identifies the card network used for a transaction. It appears in the payment response at `PaymentResult.PaymentInstrumentData.CardData.PaymentBrand`.

You can also use the `AllowedPaymentBrand` array in `TransactionConditions` to restrict which brands are accepted for a given transaction.

---

## Possible values

| Value | Description |
|---|---|
| `""` (empty string) | Default value when no brand is set (instead of null). |
| `Unknown` | Brand is present but not recognized. |
| `American Express` | American Express |
| `Discover` | Discover |
| `MASTERCARD` | MasterCard |
| `VISA` | Visa |
| `JCB` | JCB |
| `Union Pay International` | UnionPay International |
| `Interac` | Interac |
| `Diners Club` | Diners Club |
| `Dankort` | Dankort |
| `BankAxept` | BankAxept |
