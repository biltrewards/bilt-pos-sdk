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

import com.bilt.pos.nexo.client.BiltNexoClientException;
import com.bilt.pos.nexo.client.BiltNexoTerminalClient;
import com.bilt.pos.nexo.model.AbortRequest;
import com.bilt.pos.nexo.model.AdminRequest;
import com.bilt.pos.nexo.model.AdminResponse;
import com.bilt.pos.nexo.model.ErrorConditionType;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.MessageClassType;
import com.bilt.pos.nexo.model.MessageReference;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.Response;
import com.bilt.pos.nexo.model.RepeatedResponseMessageBody;
import com.bilt.pos.nexo.model.ResultType;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.nexo.model.TransactionStatusRequest;
import com.bilt.pos.nexo.model.TransactionStatusResponse;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.SessionException;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends session requests through the {@link DisplayRouter} and translates the
 * two failure layers — transport exceptions and in-body Nexo result codes —
 * into {@link SessionError}s.
 *
 * <p>Also tracks the in-flight operation so {@code abort()} can reference it
 * from another thread.</p>
 */
public final class NexoExchange {

    private static final Logger LOGGER = Logger.getLogger(NexoExchange.class.getName());
    private static final int TRANSACTION_STATUS_MAX_ATTEMPTS = 5;
    private static final long TRANSACTION_STATUS_POLL_INTERVAL_MILLIS = 1_000L;
    private static final long TRANSACTION_STATUS_POLL_JITTER_MILLIS = 200L;

    /** The operation currently awaiting a terminal response, if any. */
    public static final class InFlight {
        private final MessageCategoryType category;
        private final String serviceId;
        private final BiltNexoTerminalClient client;

        InFlight(MessageCategoryType category, String serviceId, BiltNexoTerminalClient client) {
            this.category = category;
            this.serviceId = serviceId;
            this.client = client;
        }

        public MessageCategoryType getCategory() {
            return category;
        }

        public String getServiceId() {
            return serviceId;
        }

        public BiltNexoTerminalClient getClient() {
            return client;
        }
    }

    private final DisplayRouter router;
    private final NexoMessageFactory factory;
    private volatile InFlight inFlight;

    public NexoExchange(DisplayRouter router, NexoMessageFactory factory) {
        this.router = router;
        this.factory = factory;
    }

    public NexoMessageFactory factory() {
        return factory;
    }

    public DisplayRouter router() {
        return router;
    }

    /** The operation currently awaiting a response, or {@code null}. */
    public InFlight currentInFlight() {
        return inFlight;
    }

    /**
     * Sends a Bilt session lifecycle signal as a Nexo {@code AdminRequest}
     * (see {@link SessionSignalCodec}), requiring success.
     */
    public void sendSessionSignal(String serviceIdentification) {
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(factory.header(MessageClassType.SERVICE,
                        MessageCategoryType.ADMIN))
                .adminRequest(AdminRequest.builder()
                        .serviceIdentification(serviceIdentification)
                        .build())
                .build();
        SaleToPOIResponse response = sendExpectingBody(MessageCategoryType.ADMIN, request);
        AdminResponse body = response.getAdminResponse();
        if (body == null) {
            throw Wire.missing("AdminResponse");
        }
        requireSuccess(MessageCategoryType.ADMIN, body.getResponse());
    }

    /**
     * Best-effort Nexo {@code AbortRequest} for the operation currently
     * awaiting a response, sent to the device processing it. No-op with
     * nothing in flight. The session lifecycle signals (the ADMIN category
     * carries only start/end) are never the abort's target: cancelling an
     * end exchange would buy nothing and could strand the terminal's
     * session-scoped data.
     */
    public void abortInFlight() {
        InFlight current = inFlight;
        if (current == null || current.getCategory() == MessageCategoryType.ADMIN) {
            return;
        }
        SaleToPOIRequest request = SaleToPOIRequest.builder()
                .messageHeader(factory.header(MessageClassType.SERVICE,
                        MessageCategoryType.ABORT))
                .abortRequest(AbortRequest.builder()
                        .abortReason("MerchantAbort")
                        .messageReference(MessageReference.builder()
                                .messageCategory(current.getCategory())
                                .serviceID(current.getServiceId())
                                .saleID(factory.getSaleId())
                                .build())
                        .build())
                .build();
        try {
            current.getClient().request(factory.envelope(request));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "abort request failed", e);
        }
    }

    /**
     * Sends {@code request} to the client owning {@code category} and returns
     * the response body, which may be {@code null} when the terminal replies
     * with an empty body (e.g. for aborts).
     *
     * @throws SessionException with {@link SessionErrorCode#NETWORK} or
     *         {@link SessionErrorCode#TIMEOUT} on transport failure
     */
    public SaleToPOIResponse send(MessageCategoryType category, SaleToPOIRequest request) {
        return send(category, request, null);
    }

    /** Like {@link #send} with an optional per-request read timeout override. */
    public SaleToPOIResponse send(MessageCategoryType category, SaleToPOIRequest request,
                                  java.time.Duration timeout) {
        BiltNexoTerminalClient client = router.route(category);
        String serviceId = request.getMessageHeader() != null
                ? request.getMessageHeader().getServiceID() : null;
        inFlight = new InFlight(category, serviceId, client);
        try {
            NexoTerminalAPI envelope = factory.envelope(request);
            NexoTerminalAPI response = timeout == null
                    ? client.request(envelope)
                    : client.request(envelope, timeout);
            return response == null ? null : response.getSaleToPOIResponse();
        } catch (BiltNexoClientException e) {
            throw new SessionException(toTransportError(category, e));
        } finally {
            inFlight = null;
        }
    }

    /**
     * Like {@link #send} but treats an empty response body as a
     * {@link SessionErrorCode#TERMINAL_ERROR}.
     */
    public SaleToPOIResponse sendExpectingBody(MessageCategoryType category,
                                               SaleToPOIRequest request) {
        return sendExpectingBody(category, request, null);
    }

    /** Like {@link #sendExpectingBody} with a per-request read timeout override. */
    public SaleToPOIResponse sendExpectingBody(MessageCategoryType category,
                                               SaleToPOIRequest request,
                                               java.time.Duration timeout) {
        SaleToPOIResponse response = send(category, request, timeout);
        if (response == null) {
            throw new SessionException(new SessionError(SessionErrorCode.TERMINAL_ERROR,
                    "terminal returned an empty response to " + category.toValue() + " request"));
        }
        return response;
    }

    /**
     * Resolves an earlier request by ServiceID. Returns the repeated original
     * response, or {@code null} when the terminal authoritatively reports that
     * it was not found. An in-progress response is polled for a bounded period;
     * any other status exchange failure remains a SessionException.
     */
    public SaleToPOIResponse recoverByTransactionStatus(String serviceId,
                                                        MessageCategoryType originalCategory) {
        for (int attempt = 1; attempt <= TRANSACTION_STATUS_MAX_ATTEMPTS; attempt++) {
            SaleToPOIResponse response = sendExpectingBody(
                    MessageCategoryType.TRANSACTION_STATUS,
                    transactionStatusRequest(serviceId, originalCategory));
            TransactionStatusResponse body = response.getTransactionStatusResponse();
            if (body == null) {
                throw Wire.missing("TransactionStatusResponse");
            }
            Response status = body.getResponse();
            if (isStatusFailure(status, ErrorConditionType.NOT_FOUND)) {
                return null;
            }
            if (isStatusFailure(status, ErrorConditionType.IN_PROGRESS)) {
                if (attempt == TRANSACTION_STATUS_MAX_ATTEMPTS) {
                    throw new SessionException(new SessionError(
                            SessionErrorCode.TIMEOUT,
                            "terminal is still processing " + originalCategory.toValue()
                                    + " request " + serviceId + " after " + attempt
                                    + " TransactionStatus attempts",
                            ErrorConditionType.IN_PROGRESS.toValue(), null));
                }
                waitBeforeStatusRetry();
                continue;
            }
            requireSuccess(MessageCategoryType.TRANSACTION_STATUS, status);
            RepeatedResponseMessageBody repeated = body.getRepeatedMessageResponse() == null
                    ? null : body.getRepeatedMessageResponse().getRepeatedResponseMessageBody();
            if (repeated == null) {
                throw Wire.missing(
                        "TransactionStatusResponse.RepeatedMessageResponse");
            }
            boolean expectedBodyPresent = (originalCategory == MessageCategoryType.PAYMENT
                    && repeated.getPaymentResponse() != null)
                    || (originalCategory == MessageCategoryType.LOYALTY
                    && repeated.getLoyaltyResponse() != null)
                    || (originalCategory == MessageCategoryType.STORED_VALUE
                    && repeated.getStoredValueResponse() != null)
                    || (originalCategory == MessageCategoryType.REVERSAL
                    && repeated.getReversalResponse() != null);
            if (!expectedBodyPresent) {
                throw Wire.missing("TransactionStatusResponse repeated "
                        + originalCategory.toValue() + " response");
            }
            return SaleToPOIResponse.builder()
                    .loyaltyResponse(repeated.getLoyaltyResponse())
                    .paymentResponse(repeated.getPaymentResponse())
                    .storedValueResponse(repeated.getStoredValueResponse())
                    .reversalResponse(repeated.getReversalResponse())
                    .build();
        }
        throw new AssertionError("unreachable TransactionStatus recovery state");
    }

    private SaleToPOIRequest transactionStatusRequest(String serviceId,
                                                      MessageCategoryType originalCategory) {
        return SaleToPOIRequest.builder()
                .messageHeader(factory.header(MessageClassType.SERVICE,
                        MessageCategoryType.TRANSACTION_STATUS))
                .transactionStatusRequest(TransactionStatusRequest.builder()
                        .messageReference(MessageReference.builder()
                                .messageCategory(originalCategory)
                                .serviceID(serviceId)
                                .saleID(factory.getSaleId())
                                .build())
                        .build())
                .build();
    }

    private static boolean isStatusFailure(Response response, ErrorConditionType condition) {
        return response != null
                && response.getResult() == ResultType.FAILURE
                && response.getErrorCondition() == condition;
    }

    private static void waitBeforeStatusRetry() {
        long delayMillis = ThreadLocalRandom.current().nextLong(
                TRANSACTION_STATUS_POLL_INTERVAL_MILLIS
                        - TRANSACTION_STATUS_POLL_JITTER_MILLIS,
                TRANSACTION_STATUS_POLL_INTERVAL_MILLIS
                        + TRANSACTION_STATUS_POLL_JITTER_MILLIS + 1L);
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SessionException(new SessionError(SessionErrorCode.TERMINAL_ERROR,
                    "TransactionStatus recovery was interrupted", null, e));
        }
    }

    /**
     * Throws a {@link SessionException} when the in-body result reports
     * failure. A missing {@code Response} object is treated as a terminal
     * error; {@code Partial} results pass.
     */
    public void requireSuccess(MessageCategoryType category, Response response) {
        if (response == null) {
            throw new SessionException(new SessionError(SessionErrorCode.TERMINAL_ERROR,
                    category.toValue() + " response is missing its Result"));
        }
        if (response.getResult() == ResultType.FAILURE) {
            throw new SessionException(toError(category, response));
        }
    }

    /** Maps a failed in-body {@code Response} to a {@link SessionError}. */
    public static SessionError toError(MessageCategoryType category, Response response) {
        ErrorConditionType condition = response.getErrorCondition();
        SessionErrorCode code = toErrorCode(category, condition);
        String conditionValue = condition == null ? null : condition.toValue();
        StringBuilder message = new StringBuilder(category.toValue()).append(" request failed");
        if (conditionValue != null) {
            message.append(": ").append(conditionValue);
        }
        if (response.getAdditionalResponse() != null) {
            message.append(" (").append(response.getAdditionalResponse()).append(')');
        }
        return new SessionError(code, message.toString(), conditionValue, null);
    }

    private static SessionErrorCode toErrorCode(MessageCategoryType category,
                                                ErrorConditionType condition) {
        if (condition == null) {
            return SessionErrorCode.UNKNOWN;
        }
        switch (condition) {
            case ABORTED:
                return SessionErrorCode.ABORTED;
            case CANCEL:
                return SessionErrorCode.CANCELLED;
            case REFUSAL:
            case PAYMENT_RESTRICTION:
            case WRONG_PIN:
                return SessionErrorCode.DECLINED;
            case UNAVAILABLE_SERVICE:
                return category == MessageCategoryType.LOYALTY
                        ? SessionErrorCode.LOYALTY_UNAVAILABLE
                        : SessionErrorCode.TERMINAL_ERROR;
            case UNREACHABLE_HOST:
                return SessionErrorCode.NETWORK;
            case NOT_FOUND:
                return SessionErrorCode.UNKNOWN;
            case BUSY:
            case IN_PROGRESS:
            case DEVICE_OUT:
            case INSERTED_CARD:
            case INVALID_CARD:
            case LOGGED_OUT:
            case MESSAGE_FORMAT:
            case NOT_ALLOWED:
            case UNAVAILABLE_DEVICE:
                return SessionErrorCode.TERMINAL_ERROR;
            default:
                return SessionErrorCode.UNKNOWN;
        }
    }

    private static SessionError toTransportError(MessageCategoryType category,
                                                 BiltNexoClientException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof InterruptedIOException) {
                return new SessionError(SessionErrorCode.TIMEOUT,
                        category.toValue() + " request timed out: " + e.getMessage(), null, e);
            }
        }
        return new SessionError(SessionErrorCode.NETWORK,
                category.toValue() + " request failed: " + e.getMessage(), null, e);
    }
}
