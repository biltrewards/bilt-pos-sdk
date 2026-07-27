/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.identity;

import java.util.Objects;

/**
 * A member identifier the POS already has on file, for identification
 * without a terminal prompt (resolved via a Nexo {@code BalanceInquiry}).
 *
 * <pre>{@code
 * session.identifyMember(MemberIdentifier.phoneNumber("555-867-5309"));
 * session.identifyMember(MemberIdentifier.accountNumber("98234").keyedByCashier());
 * }</pre>
 */
public final class MemberIdentifier {

    /** Kind of identifier being resolved. */
    public enum Type { PHONE_NUMBER, ACCOUNT_NUMBER }

    private final Type type;
    private final String value;
    private final boolean keyedByCashier;

    private MemberIdentifier(Type type, String value, boolean keyedByCashier) {
        this.type = type;
        this.value = value;
        this.keyedByCashier = keyedByCashier;
    }

    /** A phone number loaded from a POS profile. */
    public static MemberIdentifier phoneNumber(String phoneNumber) {
        Objects.requireNonNull(phoneNumber, "phoneNumber");
        return new MemberIdentifier(Type.PHONE_NUMBER, phoneNumber, false);
    }

    /** A loyalty account number loaded from a POS profile. */
    public static MemberIdentifier accountNumber(String accountNumber) {
        Objects.requireNonNull(accountNumber, "accountNumber");
        return new MemberIdentifier(Type.ACCOUNT_NUMBER, accountNumber, false);
    }

    /** Marks the identifier as typed in by the cashier rather than on file. */
    public MemberIdentifier keyedByCashier() {
        return new MemberIdentifier(type, value, true);
    }

    public Type getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    /** {@code true} when the cashier typed the identifier ({@code EntryMode=Keyed}). */
    public boolean isKeyedByCashier() {
        return keyedByCashier;
    }
}
