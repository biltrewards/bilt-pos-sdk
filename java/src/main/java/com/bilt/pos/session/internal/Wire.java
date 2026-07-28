/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Internal API — subject to change without notice.
 */
package com.bilt.pos.session.internal;

import com.bilt.pos.nexo.model.EntryModeType;
import com.bilt.pos.nexo.model.IdentificationTypeEnum;
import com.bilt.pos.nexo.model.LoyaltyAccountID;
import com.bilt.pos.nexo.model.POIData;
import com.bilt.pos.nexo.model.TransactionIdentificationType;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.SessionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Conversions between wire values and session types, shared by the internal
 * managers.
 */
public final class Wire {

    private Wire() {
    }

    /** Wire {@code double} amount → money at scale 2, {@code HALF_UP}. */
    public static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Lenient wire timestamp parser: accepts offset ({@code +00:00}) and
     * {@code Z} forms; {@code null} for {@code null} or unparsable input.
     */
    public static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    /** A terminal reply lacks a structure the operation needs. */
    public static SessionException missing(String what) {
        return new SessionException(new SessionError(SessionErrorCode.TERMINAL_ERROR,
                "terminal response is missing " + what));
    }

    /** The POI transaction reference of a response, or {@code null}. */
    public static TransactionIdentificationType poiRef(POIData poiData) {
        return poiData == null ? null : poiData.getPoiTransactionID();
    }

    /**
     * The loyalty account identification for a member id — the PAN/keyed
     * convention every loyalty request and reversal uses on this wire.
     */
    public static LoyaltyAccountID memberAccount(String memberId) {
        return LoyaltyAccountID.builder()
                .loyaltyID(memberId)
                .identificationType(IdentificationTypeEnum.PAN)
                .entryMode(new EntryModeType[] {EntryModeType.KEYED})
                .build();
    }
}
