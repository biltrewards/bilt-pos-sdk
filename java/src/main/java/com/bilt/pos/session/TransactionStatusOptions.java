/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import com.bilt.pos.nexo.model.DocumentQualifierEnum;
import com.bilt.pos.nexo.model.MessageCategoryType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Options for {@link CheckoutSession#getTransactionStatus}.
 *
 * <p>By default the referenced request is assumed to be a payment and no
 * receipt data is requested. Enable {@link Builder#receiptReprint} to have
 * the terminal include the original receipts in the repeated response — used
 * to reprint after a crash or connection loss.</p>
 */
public final class TransactionStatusOptions {

    private final MessageCategoryType originalCategory;
    private final boolean receiptReprint;
    private final List<DocumentQualifierEnum> documentQualifiers;

    private TransactionStatusOptions(Builder builder) {
        this.originalCategory = builder.originalCategory;
        this.receiptReprint = builder.receiptReprint;
        this.documentQualifiers = Collections.unmodifiableList(
                new ArrayList<>(builder.documentQualifiers));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Payment original, no receipt reprint. */
    public static TransactionStatusOptions defaults() {
        return builder().build();
    }

    /** Message category of the original request. Default {@code Payment}. */
    public MessageCategoryType getOriginalCategory() {
        return originalCategory;
    }

    /** Whether the terminal should include receipt data in the response. */
    public boolean isReceiptReprint() {
        return receiptReprint;
    }

    /**
     * Receipt kinds to include when {@link #isReceiptReprint()}; defaults to
     * customer and cashier receipts.
     */
    public List<DocumentQualifierEnum> getDocumentQualifiers() {
        return documentQualifiers;
    }

    /** Builder for {@link TransactionStatusOptions}. */
    public static final class Builder {

        private MessageCategoryType originalCategory = MessageCategoryType.PAYMENT;
        private boolean receiptReprint;
        private List<DocumentQualifierEnum> documentQualifiers = new ArrayList<>(List.of(
                DocumentQualifierEnum.CUSTOMER_RECEIPT, DocumentQualifierEnum.CASHIER_RECEIPT));

        private Builder() {
        }

        public Builder originalCategory(MessageCategoryType originalCategory) {
            this.originalCategory = originalCategory;
            return this;
        }

        public Builder receiptReprint(boolean receiptReprint) {
            this.receiptReprint = receiptReprint;
            return this;
        }

        public Builder documentQualifiers(List<DocumentQualifierEnum> documentQualifiers) {
            this.documentQualifiers = new ArrayList<>(documentQualifiers);
            return this;
        }

        public TransactionStatusOptions build() {
            return new TransactionStatusOptions(this);
        }
    }
}
