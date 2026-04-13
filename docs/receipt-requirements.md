---
---

# Receipt Requirements

This section defines the minimum requirements for EMV transaction receipts when integrating with the Bilt POS platform.

---

## Overview

All EMV transaction receipts must comply with Fiserv's mandatory receipt requirements. Additionally, Bilt Platform-specific fields may be added for loyalty and split-pay features.

---

## Source Documents

| Document | Description |
|----------|-------------|
| [Fiserv EMV Receipt Guidelines (Chapters 7 & 12)](assets/receipts/Fiserv_EMV_Receipt_Chapters_7_and_12.pdf) | Mandatory EMV receipt fields, offline decline requirements, and sample receipt layouts |
| [Bilt Platform Receipt Fields Addendum](assets/receipts/TBHC0300_Receipt_Addendum_Bilt_Platform_Fields.docx) | Optional Bilt-specific fields for loyalty and split-pay features |

---

## Fiserv Mandatory Fields

The following fields are **mandatory** on all EMV transaction receipts per Fiserv requirements:

### General Fields

| Field | Description |
|-------|-------------|
| Merchant Name and Address | Name and address of the merchant or marketplace |
| Transaction Date | Date stamp of the transaction |
| Transaction Time | Time stamp of the transaction |
| Transaction Number | Unique reference number of the transaction |
| Receipt/Invoice Number | Unique reference number of the receipt |
| Card Type | Name of the card issuer (Visa, Mastercard, Amex, etc.) |
| Account Number | Truncated PAN (last 4 digits only) |
| Card Entry Method | `Chip Read`, `Contactless`, `FSwipe` (fallback), or `Magstripe` |
| Application Preferred Name | Or Application Label (Tag 50), or Terminal Default Label |
| Application Identifier (AID) | Full AID value from the chip |
| Approval Code | Authorization code received for the transaction |
| Authorization Mode | `Issuer` (online) or `Card` (offline) |
| Transaction Type | Sale, Refund, Void, etc. |
| Transaction Amount | Total billed amount with currency symbol |
| PIN Verify Statement | Status of PIN verification (Verified/Not Verified) |
| Return and Refund Policies | Merchant's terms and conditions |

### Conditional Fields

| Field | Condition |
|-------|-----------|
| Currency Symbol | Required if not local currency |
| Signature Line | Required for Signature CVM transactions |
| Payment Network | Required when CAID is sent for dual credit/debit processing |

### Declined Transaction Fields

| Field | Description |
|-------|-------------|
| Decline Code | System-generated error code |
| Decline Message | System-generated decline message |
| Mandatory EMV Tags | AID, TVR, IAD, and ARC for offline declines |

---

## Offline Decline Requirements

For transactions declined offline (by the chip without going online), the receipt **must** include:

| Tag | Label | Requirement |
|-----|-------|-------------|
| 9F12 / 50 | Application Preferred Name / Application Label | Mandatory |
| 84 | Application Identifier (AID) | Mandatory |
| 95 | Terminal Verification Results (TVR) | Mandatory |
| 9F10 | Issuer Authentication Data (IAD) | Mandatory |
| 8A | Authorization Response Code (ARC) | Mandatory |

Additional recommended tags for offline declines: `5F2A`, `5F34`, `82`, `9A`, `9C`, `9F02`, `9F03`, `9F07`, `9F0D`, `9F0E`, `9F0F`, `9F1A`, `9F26`, `9F27`, `9F34`, `9F36`, `9F37`, TAC Default, TAC Denial, TAC Online.

---

## Bilt Platform Fields (Optional)

The following fields are **optional** and only printed when the corresponding Bilt Platform feature is active:

| Field | Description | Phase |
|-------|-------------|-------|
| Bilt Points Earned | Points earned on this transaction | Phase 2 |
| Bilt Points Redeemed | Dollar value and point count applied as tender | Phase 2 |
| Bilt Member Tier | Member's loyalty tier (Blue, Silver, Gold, Platinum) | Phase 2 |
| Bilt Points Balance | Remaining points balance after transaction | Phase 2 |
| Split-Pay Tender Breakdown | Itemized breakdown of all tenders used | Phase 1* |
| Digital Receipt Link | QR code or URL to digital receipt | Future |
| Bilt Member ID (Masked) | Last 4 characters of member ID | Future |

\* Phase 1 split-pay supports gift card + credit split. Full multi-tender display is Phase 2.

---

## Receipt Layout Order

When Bilt Platform fields are active, they should appear in this order:

1. **Fiserv-mandated EMV fields** (merchant info, transaction details, card data, CVM, approval)
2. **Tender breakdown** (split-pay details, if applicable)
3. **Bilt Loyalty section** (points earned, redeemed, balance, tier)
4. **Digital receipt link** (QR code or URL, if enabled)
5. **Standard footer** (return policy, declaration, merchant/customer copy indicator)

---

## Key Requirements Summary

| Requirement ID | Description |
|----------------|-------------|
| RQ 5800 | Transaction amount must be printed with currency symbol |
| RQ 5900 | Application Name (Tag 9F12 or Tag 50) must be printed |
| RQ 6000 | Masked Application PAN must be printed |
| RQ 6100 | Application ID (AID) must be printed |
| RQ 6200 | Card Entry Method must identify EMV and Fallback transactions |
| RQ 6300 | CVM selected must be printed (`Verified by PIN` or signature line) |
| RQ 6400 | Authorization mode (`Issuer` or `Card`) must be printed |
| RQ 6500 | Offline decline receipts must contain AID, TVR, IAD, and ARC |
| RQ 6501 | Refund policy must be printed (Mastercard requirement) |

---

## Best Practices

- Merchant and customer receipt copies should contain the same data (except copy indicator)
- Keep offline decline receipt data for dispute resolution
- Bilt-specific fields should not modify or interfere with mandatory EMV receipt data
