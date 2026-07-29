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
import com.bilt.pos.nexo.model.ErrorConditionType;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.NexoTerminalAPI;
import com.bilt.pos.nexo.model.Response;
import com.bilt.pos.nexo.model.ResultType;
import com.bilt.pos.nexo.model.SaleToPOIRequest;
import com.bilt.pos.nexo.model.SaleToPOIResponse;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.session.SessionException;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;

/**
 * Sends session requests through the {@link DisplayRouter} and translates the
 * two failure layers — transport exceptions and in-body Nexo result codes —
 * into {@link SessionError}s.
 *
 * <p>Also tracks the in-flight operation so {@code abort()} can reference it
 * from another thread.</p>
 */
public final class NexoExchange {

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
