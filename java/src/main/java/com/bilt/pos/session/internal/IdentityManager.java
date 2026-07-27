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

import com.bilt.pos.nexo.model.BalanceInquiryRequest;
import com.bilt.pos.nexo.model.BalanceInquiryResponse;
import com.bilt.pos.nexo.model.CardAcquisitionRequest;
import com.bilt.pos.nexo.model.CardAcquisitionResponse;
import com.bilt.pos.nexo.model.CardAcquisitionTransaction;
import com.bilt.pos.nexo.model.CardData;
import com.bilt.pos.nexo.model.EntryModeType;
import com.bilt.pos.nexo.model.ErrorConditionType;
import com.bilt.pos.nexo.model.ForceEntryModeType;
import com.bilt.pos.nexo.model.IdentificationTypeEnum;
import com.bilt.pos.nexo.model.LoyaltyAccount;
import com.bilt.pos.nexo.model.LoyaltyAccountID;
import com.bilt.pos.nexo.model.LoyaltyAccountReq;
import com.bilt.pos.nexo.model.LoyaltyHandlingEnum;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.Response;
import com.bilt.pos.nexo.model.ResultType;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.session.SessionException;
import com.bilt.pos.session.identity.CardAcquisitionOptions;
import com.bilt.pos.session.identity.CardAcquisitionResult;
import com.bilt.pos.session.identity.EntryMode;
import com.bilt.pos.session.identity.ForceEntryMode;
import com.bilt.pos.session.identity.IdentifyOptions;
import com.bilt.pos.session.identity.IdentifyResult;
import com.bilt.pos.session.identity.IdentifyStatus;
import com.bilt.pos.session.identity.MemberIdentifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Member identification and card acquisition flows.
 */
public final class IdentityManager {

    private final NexoExchange exchange;

    public IdentityManager(NexoExchange exchange) {
        this.exchange = exchange;
    }

    /** Terminal-prompted identification (Nexo {@code CardAcquisition}). */
    public IdentifyResult identifyPrompted(IdentifyOptions options) {
        CardAcquisitionTransaction.Builder transaction = CardAcquisitionTransaction.builder()
                .loyaltyHandling(options.isRequireMember()
                        ? LoyaltyHandlingEnum.REQUIRED : LoyaltyHandlingEnum.PROPOSED);
        if (!options.getForceEntryModes().isEmpty()) {
            transaction.forceEntryMode(toWire(options.getForceEntryModes()));
        }
        if (!options.getAllowedLoyaltyBrands().isEmpty()) {
            transaction.allowedLoyaltyBrand(
                    options.getAllowedLoyaltyBrands().toArray(new String[0]));
        }
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.SERVICE, MessageCategoryType.CARD_ACQUISITION))
                .cardAcquisitionRequest(CardAcquisitionRequest.builder()
                        .saleData(exchange.factory().saleData())
                        .cardAcquisitionTransaction(transaction.build())
                        .build())
                .build();

        SaleToPOIResponse response = exchange.sendExpectingBody(
                MessageCategoryType.CARD_ACQUISITION, request, options.getTimeout());
        CardAcquisitionResponse body = response.getCardAcquisitionResponse();
        if (body == null || body.getResponse() == null) {
            throw Wire.missing("CardAcquisitionResponse");
        }
        IdentifyStatus lookupStatus = lookupStatus(body.getResponse());
        if (lookupStatus != IdentifyStatus.FOUND) {
            return IdentifyResult.withoutMember(lookupStatus);
        }
        LoyaltyAccount account = body.getLoyaltyAccount() != null
                && body.getLoyaltyAccount().length > 0 ? body.getLoyaltyAccount()[0] : null;
        if (account == null || account.getLoyaltyAccountID() == null) {
            throw Wire.missing("LoyaltyAccount");
        }
        return IdentifyResult.found(
                account.getLoyaltyAccountID().getLoyaltyID(),
                account.getLoyaltyBrand(),
                LoyaltyPayloadCodec.parseRewards(body.getResponse().getAdditionalResponse()),
                0);
    }

    /** POS-driven identification (Nexo {@code BalanceInquiry}). */
    public IdentifyResult identifyByIdentifier(MemberIdentifier identifier) {
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.SERVICE, MessageCategoryType.BALANCE_INQUIRY))
                .balanceInquiryRequest(BalanceInquiryRequest.builder()
                        .loyaltyAccountReq(LoyaltyAccountReq.builder()
                                .loyaltyAccountID(LoyaltyAccountID.builder()
                                        .loyaltyID(identifier.getValue())
                                        .identificationType(
                                                identifier.getType() == MemberIdentifier.Type.PHONE_NUMBER
                                                        ? IdentificationTypeEnum.PHONE_NUMBER
                                                        : IdentificationTypeEnum.ACCOUNT_NUMBER)
                                        .entryMode(new EntryModeType[] {
                                                identifier.isKeyedByCashier()
                                                        ? EntryModeType.KEYED : EntryModeType.FILE})
                                        .build())
                                .build())
                        .build())
                .build();

        SaleToPOIResponse response = exchange.sendExpectingBody(
                MessageCategoryType.BALANCE_INQUIRY, request);
        BalanceInquiryResponse body = response.getBalanceInquiryResponse();
        if (body == null || body.getResponse() == null) {
            throw Wire.missing("BalanceInquiryResponse");
        }
        IdentifyStatus lookupStatus = lookupStatus(body.getResponse());
        if (lookupStatus != IdentifyStatus.FOUND) {
            return IdentifyResult.withoutMember(lookupStatus);
        }
        LoyaltyAccount account = body.getLoyaltyAccountStatus() == null
                ? null : body.getLoyaltyAccountStatus().getLoyaltyAccount();
        if (account == null || account.getLoyaltyAccountID() == null) {
            throw Wire.missing("LoyaltyAccountStatus");
        }
        Double balance = body.getLoyaltyAccountStatus().getCurrentBalance();
        return IdentifyResult.found(
                account.getLoyaltyAccountID().getLoyaltyID(),
                account.getLoyaltyBrand(),
                LoyaltyPayloadCodec.parseRewards(body.getResponse().getAdditionalResponse()),
                balance == null ? 0 : (int) Math.round(balance));
    }

    /** Card read without payment (Nexo {@code CardAcquisition}, loyalty forbidden). */
    public CardAcquisitionResult acquireCard(CardAcquisitionOptions options) {
        CardAcquisitionTransaction.Builder transaction = CardAcquisitionTransaction.builder()
                .loyaltyHandling(LoyaltyHandlingEnum.FORBIDDEN);
        if (!options.getForceEntryModes().isEmpty()) {
            transaction.forceEntryMode(toWire(options.getForceEntryModes()));
        }
        if (options.getPaymentType() != null) {
            transaction.paymentType(options.getPaymentType());
        }
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.SERVICE, MessageCategoryType.CARD_ACQUISITION))
                .cardAcquisitionRequest(CardAcquisitionRequest.builder()
                        .saleData(exchange.factory().saleData())
                        .cardAcquisitionTransaction(transaction.build())
                        .build())
                .build();

        SaleToPOIResponse response = exchange.sendExpectingBody(
                MessageCategoryType.CARD_ACQUISITION, request, options.getTimeout());
        CardAcquisitionResponse body = response.getCardAcquisitionResponse();
        if (body == null) {
            throw Wire.missing("CardAcquisitionResponse");
        }
        exchange.requireSuccess(MessageCategoryType.CARD_ACQUISITION, body.getResponse());
        CardData card = body.getPaymentInstrumentData() == null
                ? null : body.getPaymentInstrumentData().getCardData();
        if (card == null) {
            throw Wire.missing("CardData");
        }
        return CardAcquisitionResult.builder()
                .maskedPan(card.getMaskedPAN())
                .truncatedPan(lastFourDigits(card.getMaskedPAN()))
                .rawPan(card.getSensitiveCardData() == null
                        ? null : card.getSensitiveCardData().getPan())
                .expiryDate(card.getSensitiveCardData() == null
                        ? null : card.getSensitiveCardData().getExpiryDate())
                .paymentBrand(card.getPaymentBrand())
                .entryMode(card.getEntryMode() != null && card.getEntryMode().length > 0
                        ? EntryMode.fromWire(card.getEntryMode()[0]) : null)
                .cardToken(card.getPaymentToken() == null
                        ? null : card.getPaymentToken().getTokenValue())
                .additionalData(additionalData(body.getResponse()))
                .build();
    }

    // ─── Internals ───

    /**
     * Distinguishes "no member attached" outcomes from real errors: not
     * found, suspended, and customer-cancelled become an
     * {@link IdentifyStatus}; anything else failing throws.
     */
    private IdentifyStatus lookupStatus(Response response) {
        if (response.getResult() != ResultType.FAILURE) {
            return IdentifyStatus.FOUND;
        }
        ErrorConditionType condition = response.getErrorCondition();
        if (condition == ErrorConditionType.NOT_FOUND) {
            return IdentifyStatus.NOT_FOUND;
        }
        if (condition == ErrorConditionType.NOT_ALLOWED) {
            return IdentifyStatus.SUSPENDED;
        }
        if (condition == ErrorConditionType.CANCEL || condition == ErrorConditionType.ABORTED) {
            return IdentifyStatus.CANCELLED;
        }
        throw new SessionException(NexoExchange.toError(
                MessageCategoryType.CARD_ACQUISITION, response));
    }

    private static ForceEntryModeType[] toWire(List<ForceEntryMode> modes) {
        ForceEntryModeType[] wire = new ForceEntryModeType[modes.size()];
        for (int i = 0; i < modes.size(); i++) {
            wire[i] = modes.get(i).toWire();
        }
        return wire;
    }

    private static String lastFourDigits(String maskedPan) {
        if (maskedPan == null) {
            return null;
        }
        String digits = maskedPan.replaceAll("\\D", "");
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
    }

    /**
     * Additional card data may be Base64 JSON or URL-encoded pairs; fall back
     * to the raw value under {@code "raw"}.
     */
    private static Map<String, String> additionalData(Response response) {
        String raw = response == null ? null : response.getAdditionalResponse();
        if (raw == null || raw.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Map<String, String> fields = LoyaltyPayloadCodec.parseFields(raw);
        if (!fields.isEmpty()) {
            return fields;
        }
        if (raw.contains("=")) {
            Map<String, String> pairs = new LinkedHashMap<>();
            for (String pair : raw.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    pairs.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
            if (!pairs.isEmpty()) {
                return pairs;
            }
        }
        Map<String, String> fallback = new LinkedHashMap<>();
        fallback.put("raw", raw);
        return fallback;
    }
}
