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

import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.MessageHeader;
import com.bilt.pos.nexo.model.MessageTypeType;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.SaleToPOIRequest;

import java.security.SecureRandom;

/**
 * Builds Nexo message headers and envelopes for a checkout session,
 * encapsulating the {@code ProtocolVersion}/{@code ServiceID}/{@code SaleID}/
 * {@code POIID} boilerplate.
 *
 * <p>{@code ServiceID}s are random 10-character alphanumeric strings —
 * unique within the terminal's 48-hour correlation window without requiring
 * session-restart-safe counters.</p>
 */
public final class NexoMessageFactory {

    private static final String PROTOCOL_VERSION = "3.0";
    private static final char[] SERVICE_ID_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int SERVICE_ID_LENGTH = 10;

    private final String saleId;
    private final String poiId;
    private final SecureRandom random = new SecureRandom();

    public NexoMessageFactory(String saleId, String poiId) {
        this.saleId = saleId;
        this.poiId = poiId;
    }

    public String getSaleId() {
        return saleId;
    }

    public String getPoiId() {
        return poiId;
    }

    /** Generates a fresh 10-character alphanumeric {@code ServiceID}. */
    public String nextServiceId() {
        char[] chars = new char[SERVICE_ID_LENGTH];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = SERVICE_ID_ALPHABET[random.nextInt(SERVICE_ID_ALPHABET.length)];
        }
        return new String(chars);
    }

    /**
     * Builds a request header for the given class/category with a fresh
     * {@code ServiceID}.
     */
    public MessageHeader header(MessageClassType messageClass, MessageCategoryType category) {
        return header(messageClass, category, nextServiceId());
    }

    /** Builds a request header with an explicit {@code ServiceID}. */
    public MessageHeader header(MessageClassType messageClass, MessageCategoryType category,
                                String serviceId) {
        return MessageHeader.builder()
                .protocolVersion(PROTOCOL_VERSION)
                .messageClass(messageClass)
                .messageCategory(category)
                .messageType(MessageTypeType.REQUEST)
                .serviceID(serviceId)
                .saleID(saleId)
                .poiid(poiId)
                .build();
    }

    /** Wraps a {@link SaleToPOIRequest} into the top-level API envelope. */
    public NexoTerminalAPI envelope(SaleToPOIRequest request) {
        return NexoTerminalAPI.builder()
                .saleToPOIRequest(request)
                .build();
    }
}
