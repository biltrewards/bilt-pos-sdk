/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.storedvalue;

import com.bilt.pos.nexo.model.EntryModeType;
import com.bilt.pos.nexo.model.IdentificationTypeEnum;
import com.bilt.pos.nexo.model.StoredValueAccountTypeEnum;

import java.util.Objects;

/**
 * Identifies a stored value (gift) card for stored value operations and
 * split-tender payment.
 *
 * <pre>{@code
 * StoredValueCard.number("6006491260550218157")            // keyed card number
 * StoredValueCard.scanned("6006491260550218157")           // scanned barcode
 * StoredValueCard.swiped()                                 // terminal reads the mag stripe
 *     .withProvider("givex")
 *     .withExpiryDate("1228")
 * }</pre>
 */
public final class StoredValueCard {

    private final String storedValueId;
    private final IdentificationTypeEnum identificationType;
    private final EntryModeType entryMode;
    private final StoredValueAccountTypeEnum accountType;
    private final String provider;
    private final String expiryDate;

    private StoredValueCard(String storedValueId, IdentificationTypeEnum identificationType,
                            EntryModeType entryMode, StoredValueAccountTypeEnum accountType,
                            String provider, String expiryDate) {
        this.storedValueId = storedValueId;
        this.identificationType = identificationType;
        this.entryMode = entryMode;
        this.accountType = accountType;
        this.provider = provider;
        this.expiryDate = expiryDate;
    }

    /** A card number typed or on file ({@code PAN} / {@code Keyed}). */
    public static StoredValueCard number(String cardNumber) {
        Objects.requireNonNull(cardNumber, "cardNumber");
        return new StoredValueCard(cardNumber, IdentificationTypeEnum.PAN,
                EntryModeType.KEYED, StoredValueAccountTypeEnum.GIFT_CARD, null, null);
    }

    /** A scanned barcode ({@code BarCode} / {@code Scanned}). */
    public static StoredValueCard scanned(String barcode) {
        Objects.requireNonNull(barcode, "barcode");
        return new StoredValueCard(barcode, IdentificationTypeEnum.BAR_CODE,
                EntryModeType.SCANNED, StoredValueAccountTypeEnum.GIFT_CARD, null, null);
    }

    /**
     * The terminal prompts the customer to swipe the card
     * ({@code MagStripe}; no card number is sent).
     */
    public static StoredValueCard swiped() {
        return new StoredValueCard(null, IdentificationTypeEnum.PAN,
                EntryModeType.MAG_STRIPE, StoredValueAccountTypeEnum.GIFT_CARD, null, null);
    }

    /** Returns a copy with the stored value provider (e.g. {@code "givex"}). */
    public StoredValueCard withProvider(String provider) {
        return new StoredValueCard(storedValueId, identificationType, entryMode,
                accountType, provider, expiryDate);
    }

    /** Returns a copy with a different account type (default {@code GiftCard}). */
    public StoredValueCard withAccountType(StoredValueAccountTypeEnum accountType) {
        Objects.requireNonNull(accountType, "accountType");
        return new StoredValueCard(storedValueId, identificationType, entryMode,
                accountType, provider, expiryDate);
    }

    /** Returns a copy with the card expiry date ({@code "MMYY"}). */
    public StoredValueCard withExpiryDate(String expiryDate) {
        return new StoredValueCard(storedValueId, identificationType, entryMode,
                accountType, provider, expiryDate);
    }

    /** Card number or barcode; {@code null} for swiped cards. */
    public String getStoredValueId() {
        return storedValueId;
    }

    public IdentificationTypeEnum getIdentificationType() {
        return identificationType;
    }

    public EntryModeType getEntryMode() {
        return entryMode;
    }

    public StoredValueAccountTypeEnum getAccountType() {
        return accountType;
    }

    /** Stored value provider, or {@code null} for the terminal default. */
    public String getProvider() {
        return provider;
    }

    /** {@code "MMYY"}, or {@code null}. */
    public String getExpiryDate() {
        return expiryDate;
    }
}
