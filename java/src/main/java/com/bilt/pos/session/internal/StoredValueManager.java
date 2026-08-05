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
import com.bilt.pos.nexo.model.EntryModeType;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.PaymentAccountReq;
import com.bilt.pos.nexo.model.PaymentInstrumentData;
import com.bilt.pos.nexo.model.PaymentInstrumentTypeEnum;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.nexo.model.StoredValueAccountID;
import com.bilt.pos.nexo.model.StoredValueData;
import com.bilt.pos.nexo.model.StoredValueRequest;
import com.bilt.pos.nexo.model.StoredValueResponse;
import com.bilt.pos.nexo.model.StoredValueResult;
import com.bilt.pos.nexo.model.StoredValueTransactionTypeEnum;
import com.bilt.pos.nexo.model.TransactionIdentificationType;
import com.bilt.pos.session.storedvalue.StoredValueBalance;
import com.bilt.pos.session.storedvalue.StoredValueCard;
import com.bilt.pos.session.storedvalue.StoredValueOperationResult;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Stored value (gift card) lifecycle operations: activate, load, unload,
 * reserve, reverse, duplicate ({@code StoredValueRequest}) and balance
 * inquiry ({@code BalanceInquiry} with a {@code PaymentAccountReq}).
 */
public final class StoredValueManager {

    private final NexoExchange exchange;
    private final String currency;

    public StoredValueManager(NexoExchange exchange, String currency) {
        this.exchange = exchange;
        this.currency = currency;
    }

    /**
     * Runs a stored value operation.
     *
     * @param card         the card; {@code null} only for reversals
     * @param amount       amount to move; activate accepts {@code 0}
     * @param originalPoiTxnId original transaction, for {@code Reverse}
     */
    public StoredValueOperationResult operation(StoredValueTransactionTypeEnum type,
                                                StoredValueCard card, BigDecimal amount,
                                                String originalPoiTxnId,
                                                Instant originalPoiTimestamp) {
        StoredValueData.Builder data = StoredValueData.builder()
                .storedValueTransactionType(type)
                .currency(currency)
                .itemAmount(amount == null ? 0.0 : amount.doubleValue());
        if (card != null) {
            data.storedValueAccountID(accountId(card));
            if (card.getProvider() != null) {
                data.storedValueProvider(card.getProvider());
            }
        }
        if (originalPoiTxnId != null) {
            data.originalPOITransaction(Wire.originalTransaction(
                    originalPoiTxnId, originalPoiTimestamp));
        }
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.SERVICE, MessageCategoryType.STORED_VALUE))
                .storedValueRequest(StoredValueRequest.builder()
                        .saleData(exchange.factory().saleData())
                        .storedValueData(new StoredValueData[] {data.build()})
                        .build())
                .build();

        SaleToPOIResponse response = exchange.sendExpectingBody(
                MessageCategoryType.STORED_VALUE, request);
        StoredValueResponse body = response.getStoredValueResponse();
        if (body == null) {
            throw Wire.missing("StoredValueResponse");
        }
        exchange.requireSuccess(MessageCategoryType.STORED_VALUE, body.getResponse());

        StoredValueResult result = body.getStoredValueResult() != null
                && body.getStoredValueResult().length > 0
                ? body.getStoredValueResult()[0] : null;
        TransactionIdentificationType poiTxn = body.getPoiData() == null
                ? null : body.getPoiData().getPoiTransactionID();
        StoredValueOperationResult.Builder out = StoredValueOperationResult.builder()
                .transactionType(type)
                .amount(amount)
                .currency(currency)
                .poiTransactionId(poiTxn == null ? null : poiTxn.getTransactionID())
                .poiTransactionTimestamp(poiTxn == null
                        ? null : Wire.instant(poiTxn.getTimeStamp()));
        if (result != null) {
            out.amount(Wire.money(result.getItemAmount()));
            if (result.getCurrency() != null) {
                out.currency(result.getCurrency());
            }
            if (result.getStoredValueAccountStatus() != null
                    && result.getStoredValueAccountStatus().getCurrentBalance() != null) {
                out.currentBalance(Wire.money(
                        result.getStoredValueAccountStatus().getCurrentBalance()));
            }
            if (result.getHostTransactionID() != null) {
                out.hostTransactionId(result.getHostTransactionID().getTransactionID());
            }
        }
        return out.build();
    }

    /** Balance inquiry for a stored value card. */
    public StoredValueBalance balance(StoredValueCard card) {
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(exchange.factory().header(
                        MessageClassType.SERVICE, MessageCategoryType.BALANCE_INQUIRY))
                .balanceInquiryRequest(BalanceInquiryRequest.builder()
                        .paymentAccountReq(PaymentAccountReq.builder()
                                .paymentInstrumentData(PaymentInstrumentData.builder()
                                        .paymentInstrumentType(PaymentInstrumentTypeEnum.STORED_VALUE)
                                        .storedValueAccountID(accountId(card))
                                        .build())
                                .build())
                        .build())
                .build();

        SaleToPOIResponse response = exchange.sendExpectingBody(
                MessageCategoryType.BALANCE_INQUIRY, request);
        BalanceInquiryResponse body = response.getBalanceInquiryResponse();
        if (body == null) {
            throw Wire.missing("BalanceInquiryResponse");
        }
        exchange.requireSuccess(MessageCategoryType.BALANCE_INQUIRY, body.getResponse());
        if (body.getPaymentAccountStatus() == null
                || body.getPaymentAccountStatus().getCurrentBalance() == null) {
            throw Wire.missing("PaymentAccountStatus.CurrentBalance");
        }
        return new StoredValueBalance(
                Wire.money(body.getPaymentAccountStatus().getCurrentBalance()),
                body.getPaymentAccountStatus().getCurrency() != null
                        ? body.getPaymentAccountStatus().getCurrency() : currency);
    }

    /** Maps a session card onto the wire account identification. */
    public static StoredValueAccountID accountId(StoredValueCard card) {
        StoredValueAccountID.Builder account = StoredValueAccountID.builder()
                .storedValueAccountType(card.getAccountType())
                .identificationType(card.getIdentificationType())
                .entryMode(new EntryModeType[] {card.getEntryMode()});
        if (card.getStoredValueId() != null) {
            account.storedValueID(card.getStoredValueId());
        }
        if (card.getProvider() != null) {
            account.storedValueProvider(card.getProvider());
        }
        if (card.getExpiryDate() != null) {
            account.expiryDate(card.getExpiryDate());
        }
        return account.build();
    }
}
