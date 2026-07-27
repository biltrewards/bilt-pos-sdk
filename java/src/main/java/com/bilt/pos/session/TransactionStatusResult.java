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

import com.bilt.pos.nexo.model.LoyaltyResponse;
import com.bilt.pos.nexo.model.PaymentResponse;
import com.bilt.pos.nexo.model.ReversalResponse;
import com.bilt.pos.nexo.model.StoredValueResponse;

/**
 * Outcome of {@link CheckoutSession#getTransactionStatus(String)}.
 *
 * <p>When the original transaction was found ({@link #isFound()}), the
 * terminal repeats the original response; exactly one of the typed response
 * accessors is non-null, indicated by {@link #getMessageCategory()}.</p>
 */
public final class TransactionStatusResult {

    private final boolean found;
    private final String messageCategory;
    private final PaymentResponse paymentResponse;
    private final LoyaltyResponse loyaltyResponse;
    private final StoredValueResponse storedValueResponse;
    private final ReversalResponse reversalResponse;

    private TransactionStatusResult(boolean found, String messageCategory,
                                    PaymentResponse paymentResponse,
                                    LoyaltyResponse loyaltyResponse,
                                    StoredValueResponse storedValueResponse,
                                    ReversalResponse reversalResponse) {
        this.found = found;
        this.messageCategory = messageCategory;
        this.paymentResponse = paymentResponse;
        this.loyaltyResponse = loyaltyResponse;
        this.storedValueResponse = storedValueResponse;
        this.reversalResponse = reversalResponse;
    }

    /** The original transaction was not found on the terminal. */
    public static TransactionStatusResult notFound() {
        return new TransactionStatusResult(false, null, null, null, null, null);
    }

    /** The original transaction was found; its response is repeated. */
    public static TransactionStatusResult found(String messageCategory,
                                                PaymentResponse paymentResponse,
                                                LoyaltyResponse loyaltyResponse,
                                                StoredValueResponse storedValueResponse,
                                                ReversalResponse reversalResponse) {
        return new TransactionStatusResult(true, messageCategory,
                paymentResponse, loyaltyResponse, storedValueResponse, reversalResponse);
    }

    /** Whether the terminal found the referenced transaction. */
    public boolean isFound() {
        return found;
    }

    /**
     * Message category of the original transaction — {@code "Payment"},
     * {@code "Loyalty"}, {@code "StoredValue"}, or {@code "Reversal"} — or
     * {@code null} when not found.
     */
    public String getMessageCategory() {
        return messageCategory;
    }

    /** The repeated payment response, or {@code null}. */
    public PaymentResponse getPaymentResponse() {
        return paymentResponse;
    }

    /** The repeated loyalty response, or {@code null}. */
    public LoyaltyResponse getLoyaltyResponse() {
        return loyaltyResponse;
    }

    /** The repeated stored value response, or {@code null}. */
    public StoredValueResponse getStoredValueResponse() {
        return storedValueResponse;
    }

    /** The repeated reversal response, or {@code null}. */
    public ReversalResponse getReversalResponse() {
        return reversalResponse;
    }
}
