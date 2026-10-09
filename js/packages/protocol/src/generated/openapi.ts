/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   This file is generated from schema/session-protocol/openapi.yaml by openapi-typescript.
 *   Do not edit by hand; run `pnpm generate` in js/ instead.
 */
export interface paths {
    "/health": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Host health and capabilities
         * @description Unauthenticated. The install prompt in the JavaScript SDK polls this to detect a bridge,
         *     and every client reads it once to check that the host speaks the protocol version it was
         *     generated from. `terminal` is the one terminal the host was configured with, absent when
         *     it has none; `reachable` is a cached view, not a probe — use `POST /v1/terminal/diagnose`
         *     for a live check.
         */
        get: operations["getHealth"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/terminal": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * The terminal the host drives
         * @description A host is a bridge between one register and one terminal, configured when it starts. The
         *     terminal's address, certificate and payload passphrase are the host's concern and never
         *     cross this protocol. Answers 404 when the host has no terminal configured.
         */
        get: operations["getTerminal"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/terminal/diagnose": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Query terminal health and host reachability
         * @description Mirrors `Terminal.diagnose()`. Terminal operations are session-less and synchronous: the
         *     request blocks until the terminal answers. They run on the terminal's own exchange, so a
         *     connectivity check mid-payment does not queue behind the payment.
         */
        post: operations["diagnoseTerminal"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/terminal/totals": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Running totals since the last reconciliation
         * @description Mirrors `Terminal.getTotals()` (Nexo `GetTotals`): lighter than reconciliation, which
         *     closes the period. Filtered by `saleId` and, when given, `storeLocation` as the
         *     `TotalsGroupID`.
         */
        post: operations["getTerminalTotals"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/terminal/reconcile": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Run a sale reconciliation
         * @description Mirrors `Terminal.reconcile()` — end-of-period totals; closes the period.
         */
        post: operations["reconcileTerminal"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/terminal/print": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Print a document on the terminal printer
         * @description Mirrors `Terminal.print(PrintPayload)`.
         */
        post: operations["printOnTerminal"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/terminal/sound": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Play or stop a sound on the terminal
         * @description Mirrors `Terminal.playSound(..)` and `Terminal.stopSound()`.
         */
        post: operations["terminalSound"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Start a shopper session
         * @description Mirrors `ShopperSession.builder()...start()` for `kind: local` and
         *     `TerminalShopperSession.builder()...start().get()` for `kind: terminal`. A terminal
         *     session is announced to the terminal (Nexo `Admin` session start) and only exists once the
         *     terminal acknowledged, so this request blocks for that round trip; a refused start creates
         *     no session. Each attempt is a new session with a new id.
         *
         *     A host drives exactly one terminal, the one it was configured with, so a terminal session
         *     always runs on it; `poiId` does not select a terminal. It is passed through as the Nexo
         *     `POIID` of every message the session sends, and the host substitutes its own default when
         *     the request leaves it out. A host with no terminal configured refuses `kind: terminal`
         *     with 409 (`UNSUPPORTED`).
         *
         *     A `retail-media` widget on a host without an ad decision service refuses the whole request
         *     with 409 (`UNSUPPORTED`) and creates no session. Widgets are otherwise attached once the
         *     session exists; one that cannot run (missing store location) is reported through a
         *     `background.error` event and the session continues without it, exactly as in Java. A
         *     pending initial member is resolved in the background and announced through
         *     `member.changed`.
         */
        post: operations["createSession"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}": {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        /** Read a session */
        get: operations["getSession"];
        put?: never;
        post?: never;
        /**
         * End the session
         * @description Mirrors `end()`. The end is itself an operation on the session's lane: it queues behind
         *     anything in flight, tells a terminal to discard its session-scoped data (Nexo `Admin`
         *     session end), and completes through `operation.completed` followed by `session.ended`.
         *     Once ended, no session operation is allowed and the basket is frozen; start a new session
         *     for the next shopper.
         *
         *     Refused with 409 under the same guards as Java: while money is in flight, while a failed
         *     payment's rollback is incomplete, while a same-session or prior-sale void is partially
         *     complete, or while refund allocations from a failed settlement have committed. Finish the
         *     unwind with `voidTransaction` or retry `settle` first, or use `force-end`. If the end
         *     signal fails the operation fails and the session remains open so the call can be retried.
         *     An abort never cancels an in-flight end.
         */
        delete: operations["endSession"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/force-end": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Irrevocably abandon the session
         * @description Mirrors `forceEnd(reason)`. Bypasses the unresolved-recovery guards that make `DELETE`
         *     refuse, for when an incomplete settlement rollback, refund allocation or void cannot be
         *     recovered and the register has recorded the incident for reconciliation. Still refused
         *     (409) while a settlement, void or recovery drain is actively moving money. The terminal
         *     end signal is best-effort: if it fails the operation reports the failure, but the session
         *     is sealed either way and cannot be retried; its duplicate-movement protection is discarded.
         */
        post: operations["forceEndSession"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/abort": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Abort whatever operation is in flight
         * @description Mirrors `TerminalShopperSession.abort()`: targets the operation currently holding the
         *     lane, whichever it is, and is a no-op when nothing is in flight. Unordered — it overtakes
         *     the lane rather than queueing on it. See `POST .../operations/{operationId}/abort` for the
         *     per-operation form and the full abort semantics.
         */
        post: operations["abortSession"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/basket": {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        /**
         * The current basket snapshot
         * @description Mirrors `SessionBasket.snapshot()`.
         */
        get: operations["getBasket"];
        /**
         * Replace the whole basket
         * @description Mirrors `SessionBasket.replace(Basket)` and `replace(List<BasketItem>)`. The session
         *     diffs the new content against the current basket and reports one `basket.changed` event
         *     with source `REPLACE`; lines are paired by `reference` when present, otherwise by SKU and
         *     type, and a paired line keeps its item id. A snapshot equal to the current basket changes
         *     no line and emits no event, but the response is still a fresh snapshot of the session, so
         *     its `updatedAt` is the time of the request, not of the last change. On a terminal session whose basket has been consumed by a
         *     successful settlement, replace starts a fresh cart (as `clear` would, under its guards)
         *     and then installs the content.
         */
        put: operations["replaceBasket"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/basket/items": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Add an item
         * @description Mirrors `SessionBasket.addItem(item)` and `addItem(item, itemId)`. When the SKU (and
         *     type, for an unreferenced item) is already in the basket its quantity is incremented.
         */
        post: operations["addBasketItem"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/basket/items/{itemId}": {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
                /** @description The session-assigned line id (`BasketLineItem.itemId`). */
                itemId: string;
            };
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Remove a line
         * @description Mirrors `SessionBasket.removeItem(itemId)`.
         */
        delete: operations["removeBasketItem"];
        options?: never;
        head?: never;
        /**
         * Change a line's quantity, discounts or tax
         * @description Mirrors `updateItemQuantity`, `setDiscounts`, `setTaxRate` and `setTaxAmount` for one
         *     line, applied atomically as one change. A quantity of `0` removes the line.
         */
        patch: operations["updateBasketItem"];
        trace?: never;
    };
    "/v1/sessions/{sessionId}/basket/mutations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Apply a batch of mutations atomically
         * @description Mirrors `SessionBasket.mutate(..)`: the mutations are applied in order as one atomic
         *     change (source `BATCH`), with one event and one customer display refresh. If any
         *     mutation fails the basket is left untouched. SKU-addressed mutations prefer a unique sale
         *     line when a SKU exists with several types and reject ambiguous SKUs.
         */
        post: operations["mutateBasket"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/basket/tax-total": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Override the basket's total tax
         * @description Mirrors `SessionBasket.setTaxTotal(amount)`; `null` restores item-level computation. The
         *     override is a magnitude; an all-refund basket carries it with a negative sign.
         */
        post: operations["setBasketTaxTotal"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/basket/clear": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Start a fresh basket
         * @description Mirrors `SessionBasket.clear()`: clears items and tax, mints a new cart id and sale
         *     transaction id, and drops the stored-value card selected for split tender. This is the
         *     transaction boundary between settlements in one session. Refused (409) while money
         *     movement, settlement recovery or a partially completed void is in progress.
         */
        post: operations["clearBasket"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/member": {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        /**
         * The member attached to the visit
         * @description Mirrors `ShopperSession.member()`: the member as the POS attached it or as identification
         *     resolved it, resolved or still pending. Phone numbers, email addresses and custom
         *     identifiers are masked in every response and event.
         */
        get: operations["getMember"];
        /**
         * Attach the member for this visit
         * @description Mirrors `ShopperSession.member(Member)`. A member with an `id` attaches immediately. A
         *     member with a `resolver` attaches as pending and is resolved in the background on the
         *     session's operation lane: on a terminal session by a Nexo `BalanceInquiry` (account id
         *     and phone only — email and custom identifiers cannot be resolved there, are reported
         *     through `background.error` and stay pending), on a local session not at all yet. Until it
         *     resolves the visit is a guest's. A lookup that finds nobody clears the member; one that
         *     fails leaves it pending. Whatever member is attached last wins. Every change is announced
         *     through `member.changed`.
         *
         *     Allowed at any time on both session kinds, but refused (409) once the session has ended
         *     and on a terminal session while a settlement or void is moving money.
         */
        put: operations["setMember"];
        post?: never;
        /**
         * Sign the member out
         * @description Mirrors `ShopperSession.member(null)`. Same guards as attaching.
         */
        delete: operations["clearMember"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/context": {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        /**
         * The session context
         * @description Mirrors `SessionContext.snapshot()`.
         */
        get: operations["getContext"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Move the phase or change attributes
         * @description Mirrors `SessionContext.phase(..)`, `attribute(..)` and `removeAttribute(..)`. Pure local
         *     compute; nothing reaches a terminal. A terminal session moves the phase itself around
         *     settlement (`TENDERING` when a settlement starts executing, `COMPLETE` when it succeeds,
         *     back on failure or abort, `SCANNING` when the basket is cleared) and may later override a
         *     phase set here. Refused (409) once the session is ending or has ended.
         */
        patch: operations["updateContext"];
        trace?: never;
    };
    "/v1/sessions/{sessionId}/operations": {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        /**
         * The session's operations, newest first
         * @description For a client that reconnects after a page reload and needs to find the operation still
         *     in flight before it has caught up on events.
         */
        get: operations["listOperations"];
        put?: never;
        /**
         * Start a session operation
         * @description The wire form of every lazy operation on `TerminalShopperSession`: the body's `type`
         *     names the Java method and carries its arguments. The response is the operation resource,
         *     normally still `queued` or `running`; the outcome arrives as `operation.completed` on the
         *     event stream and can be polled with `GET .../operations/{operationId}`.
         *
         *     One ordered lane: operations run one at a time in submission order, as on the Java
         *     session's operation thread. `updateInputDisplay` is the exception and never queues — it
         *     targets the input prompt currently holding the lane. `setStoredValueCard` is a setter in
         *     Java and completes immediately here without queueing.
         *
         *     Operations that consult the register mid-flight (`settle`, `refund`, `refundUnlinked`,
         *     `voidTransaction`) declare in `handledSteps` which step kinds the client will answer; a
         *     step kind not declared is resolved with its default at once, which is what the Java SDK
         *     does when no handler is registered.
         *
         *     A local session (`kind: local`) accepts no operation but `end`; everything here needs a
         *     terminal and is refused with 409 `UNSUPPORTED`. See `OperationRequest` for each type's
         *     preconditions.
         */
        post: operations["startOperation"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/operations/{operationId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Read an operation
         * @description Mirrors `SessionResult.get()` as a poll; the result or error is on the resource once it completed.
         */
        get: operations["getOperation"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/operations/{operationId}/reply": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Answer a pending step
         * @description The register's side of a settlement or reversal handler. The body names the `stepId` from
         *     the `operation.step` event and carries the one field that step kind takes: `total` for
         *     `TOTAL_REQUIRED`, `saleTransactionId` for `BEFORE_STEP`, `recovery` for
         *     `RECOVERY_REQUIRED`, `decision` for `REVERSAL_DECISION_REQUIRED`. A reply after the
         *     step's deadline, for a step already answered, or for a step the operation is not awaiting
         *     is refused with 422; the default has already applied or will apply.
         */
        post: operations["replyToStep"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/operations/{operationId}/abort": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Abort an operation
         * @description Mirrors `TerminalShopperSession.abort()` aimed at one operation. The session continues:
         *     an abort is a normal register manoeuvre, not an abandonment. If the terminal is awaiting
         *     a response a Nexo `AbortRequest` is sent best-effort. An aborted payment stops at its next
         *     step boundary, reverses the committed steps and leaves the basket intact so `settle` may
         *     retry; the operation completes with `ABORTED`. Aborted prompts deliver their aborted or
         *     cancelled outcome. Money-moving operations (refunds, stored value) always deliver their
         *     real outcome even when the abort raced them. Voids, `end` and `forceEnd` are never the
         *     abort's target (409). An operation that already completed is left alone (200).
         *
         *     Unordered: the abort overtakes the lane instead of queueing behind the operation it
         *     cancels.
         */
        post: operations["abortOperation"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/widgets": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * The session's widgets
         * @description Mirrors `ShopperSession.widgets()`, in registration order; empty for a session without widgets.
         */
        get: operations["listWidgets"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/widgets/{widgetType}/pause": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Pause a widget
         * @description Mirrors `session.widget(RetailMedia.class).pause()`: the widget's placements are cleared
         *     (`widget.clear` events) and stay clear, and it makes no requests on the shopper's behalf
         *     until resumed. The session's state keeps flowing to it so it picks up where the session is
         *     when resumed. Idempotent. For a companion display shared with PIN entry or signature
         *     capture.
         */
        post: operations["pauseWidget"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/widgets/{widgetType}/resume": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Resume a paused widget
         * @description Mirrors `resume()`. Idempotent.
         */
        post: operations["resumeWidget"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/widgets/retail-media/actions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Report what the shopper did with a rendering
         * @description The client renders; the host owns validation and reporting. This is the wire form of the
         *     `ActionSink` the Java widget hands a surface with each rendering: `perform` for a tapped
         *     call to action (the CTA's `token` must be one the rendering carried and the rendering must
         *     still be the one on display), `viewed` once the rendering counted as seen, `dismissed`
         *     when the shopper closed it from the surface, `completed` when its video played to the
         *     end. The request is accepted without waiting for validation; what follows arrives as
         *     events — `widget.offer` for an accepted `APPLY_OFFER`, `widget.interaction` for every
         *     measured event, `widget.clear` after a dismiss, and `background.error` for a rejected
         *     token (foreign, stale or tampered). A rejection is never an HTTP error, so a renderer
         *     cannot break the checkout by sending the wrong thing.
         */
        post: operations["reportRetailMediaAction"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/v1/sessions/{sessionId}/events": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Subscribe to the session's event stream
         * @description Every state change of a session is an `Event` on this stream: an envelope with a
         *     monotonically increasing `seq`, the time it happened and a typed `payload`. The stream is
         *     the source of truth for state changes; the resource endpoints are for reading state on
         *     demand and after a reconnect.
         *
         *     **Transports.** The same path serves two transports. With `Accept: text/event-stream` the
         *     response is Server-Sent Events: each event is framed as `id: <seq>`, `event: <type>`,
         *     `data: <Event JSON>`, and the host sends a comment line (`: ping`) at least every 15
         *     seconds. With an `Upgrade: websocket` handshake the connection becomes a WebSocket on
         *     which each text frame is one `Event` JSON document; the host pings per RFC 6455. React
         *     Native clients use WebSocket only; browsers may use either.
         *
         *     **Replay.** `since` is the last `seq` the client processed. The host replays every
         *     buffered event with a greater `seq` before live events, so a client that reconnects before
         *     a step's deadline still sees the pending `operation.step`. Without `since` the stream
         *     starts from the oldest buffered event, which for a fresh session is `session.started`. A
         *     `since` older than the buffer is answered 410: refetch the session, basket, member,
         *     context and operations, then subscribe again without `since`. Hosts buffer at least the
         *     whole session's events while the session is open and for a grace period after it ended.
         *
         *     **Ordering and conflation.** Events are delivered in the order the changes happened. A
         *     host never conflates events on the wire; what Java conflates (basket changes to a slow
         *     observer) is a concern of the host's own observers, not of this stream.
         *
         *     **Lifecycle.** `session.started` is always the first event and `session.ended` the last;
         *     the host closes the stream after it.
         */
        get: operations["streamEvents"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /**
         * @description Describes why something failed: the body of every error response, the `error` of a failed
         *     operation, and the payload of `background.error`. Mirrors the Java `SessionError`.
         */
        SessionError: {
            code: components["schemas"]["SessionErrorCode"];
            /** @description Human-readable description of the failure. */
            message: string;
            /** @description The raw Nexo `ErrorCondition` from the terminal reply, when the failure originated there. */
            nexoErrorCondition?: string;
            /**
             * @description Movements that completed before a whole-sale void stopped. Present on a failed void so
             *     the register can persist the progress and omit those references from a later
             *     `OriginalSaleRecord`.
             */
            reversedMovements?: components["schemas"]["ReversedMovement"][];
            /** @description Protocol-level detail, e.g. which field failed validation or the oldest buffered `seq`. */
            details?: {
                [key: string]: unknown;
            };
        };
        /**
         * @description Why a session operation failed. The first twelve values are the Java `SessionErrorCode`
         *     constants; `VALIDATION`, `NOT_FOUND` and `UNAUTHORIZED` exist only on the wire.
         * @enum {string}
         */
        SessionErrorCode: "NETWORK" | "TIMEOUT" | "DECLINED" | "CANCELLED" | "ABORTED" | "ABANDONED" | "LOYALTY_UNAVAILABLE" | "STORED_VALUE_INSUFFICIENT" | "INVALID_STATE" | "UNSUPPORTED" | "TERMINAL_ERROR" | "UNKNOWN" | "VALIDATION" | "NOT_FOUND" | "UNAUTHORIZED";
        /**
         * @description A decimal amount as a string, in the session currency; never a float.
         * @example 12.34
         * @example -4.00
         * @example 0
         */
        Money: string;
        /**
         * Format: duration
         * @description An ISO 8601 duration, e.g. `PT30S`.
         * @example PT30S
         */
        Duration: string;
        Health: {
            /**
             * @description What kind of host answered.
             * @enum {string}
             */
            host: "bridge" | "cloud";
            /** @description The host application's version. */
            hostVersion: string;
            /** @description The embedded Java SDK's version. */
            sdkVersion: string;
            /** @description Protocol major versions the host implements; this document is `"1"`. */
            protocolVersions: string[];
            /** @description The terminal the host drives; absent when none is configured, in which case only `local` sessions work. */
            terminal?: components["schemas"]["TerminalInfo"];
        };
        /**
         * @description What a client is told about the host's one terminal. Deliberately not its address,
         *     certificate or passphrase, and no `POIID`: that is whatever the session or request names.
         */
        TerminalInfo: {
            /** @description A human-readable name for the terminal, when the host's configuration gives one. */
            label?: string;
            /** @description The terminal model, when the host's configuration names it. */
            model?: string;
            /** @description The host's last known reachability; a cached view, not a live probe. */
            reachable?: boolean;
        };
        /** @description Outcome of `Terminal.diagnose()`. */
        DiagnosisResult: {
            /** @description Terminal device status (card reader, printer, security) as the Nexo `POIStatus`. */
            poiStatus?: components["schemas"]["NexoObject"];
            /** @description Reachability of the acquirer and loyalty hosts as Nexo `HostStatus` entries; empty if not reported. */
            hostStatuses: components["schemas"]["NexoObject"][];
        };
        /** @description Outcome of `Terminal.reconcile()` and `Terminal.getTotals()`. */
        ReconciliationResult: {
            /** @description Terminal-assigned identifier of the reconciliation period. */
            poiReconciliationId?: string;
            /** @description Totals per payment instrument or card brand as Nexo `TransactionTotals`; empty if not reported. */
            transactionTotals: components["schemas"]["NexoObject"][];
        };
        /** @description Content for `Terminal.print(..)`. */
        PrintPayload: {
            /**
             * @description `TEXT` is plain text; `XHTML` is a Base64-encoded XHTML document.
             * @enum {string}
             */
            format: "TEXT" | "XHTML";
            content: string;
            /** @default CUSTOMER_RECEIPT */
            documentQualifier?: components["schemas"]["DocumentQualifier"];
        };
        /** @description `PLAY` mirrors `playSound(soundReferenceId, volumePercent)`; `STOP` mirrors `stopSound()`. */
        SoundRequest: {
            /** @enum {string} */
            action: "PLAY" | "STOP";
            /** @description Reference of a pre-provisioned sound; required for `PLAY`. */
            soundReferenceId?: string;
            /** @description Volume for `PLAY`; the terminal default when absent. */
            volumePercent?: number;
        };
        /**
         * @description A Nexo Sale to POI 3.0 structure carried through unchanged, serialised as the SDK's JSON
         *     model (the schema is `schema/nexo_sale_to_poi_v3_0_schema.json`). The protocol otherwise
         *     speaks no Nexo; these appear only where the Java API itself returns a Nexo type.
         */
        NexoObject: {
            [key: string]: unknown;
        };
        /** @description A rendered receipt from a payment, refund or void, decoded from the Nexo `PaymentReceipt` content. */
        Receipt: {
            /** @description The rendered HTML receipt. */
            html?: string;
            /** @description The plain-text receipt. */
            plainText?: string;
            /** @description Structured receipt fields (merchant identity, amounts, card data, EMV tags) per `receipt.xsd`, as JSON. */
            receiptData?: {
                [key: string]: unknown;
            };
        };
        /**
         * @description The structured customer-display model (`display.xsd`, `urn:bilt:display:v1`) as JSON:
         *     exactly one of `receipt`, `qrCode`, `image`, `standby` or `waiting`, plus the `layout` the
         *     terminal renders it with (`receipt.xslt`, `receipt-qr.xslt`, `qr.xslt`, `barcode.xslt`,
         *     `image.xslt`, `standby.xslt`, `waiting.xslt`; kept a free string so new layouts need no
         *     schema change). The host converts it to the XML the terminal expects.
         */
        DisplayPayload: {
            layout: string;
            /** @default 1.0 */
            version?: string;
            receipt?: components["schemas"]["DisplayReceipt"];
            qrCode?: components["schemas"]["DisplayQrCode"];
            image?: components["schemas"]["DisplayImage"];
            standby?: components["schemas"]["DisplayStandby"];
            waiting?: components["schemas"]["DisplayWaiting"];
        } & (unknown | unknown | unknown | unknown | unknown);
        Session: {
            /** @description Unique identifier of this session instance (`getSessionId()`). */
            id: string;
            kind: components["schemas"]["SessionKind"];
            saleId: string;
            /** @description The Nexo `POIID` the session's messages carry, as requested or the host's default; absent on a local session. */
            poiId?: string;
            currency: string;
            storeLocation?: string;
            autoDisplay: boolean;
            state: components["schemas"]["SessionState"];
            /** Format: date-time */
            createdAt: string;
            /** Format: date-time */
            endedAt?: string;
            /** @description Where to subscribe to the session's events; relative to the host when it does not carry a scheme. */
            eventsUrl: string;
        };
        /** @description The builder fields of `ShopperSession.Builder` and `TerminalShopperSession.Builder`. */
        CreateSessionRequest: {
            kind: components["schemas"]["SessionKind"];
            /** @description The register's identifier for this lane, sent as Nexo `SaleID`. */
            saleId: string;
            /**
             * @description The Nexo `POIID` the session's messages carry, for `kind: terminal`; ignored for `local`.
             *     It does not select a terminal: the host has exactly one and passes this value through.
             *     Optional; the host uses its own default when absent.
             */
            poiId?: string;
            /** @description ISO 4217 currency code. */
            currency: string;
            /** @description Store location identifier, sent as `TotalsGroupID` on every transaction; required for a retail-media widget. */
            storeLocation?: string;
            /**
             * @description Whether basket changes refresh the terminal's customer display automatically (terminal sessions).
             * @default true
             */
            autoDisplay?: boolean;
            /** @description The member to start with, when the shopper is known before the visit begins. Not announced through `member.changed`; a pending member's resolution is. */
            member?: components["schemas"]["MemberInput"];
            /** @description The initial phase and attributes of the session context. */
            context?: {
                /** @default SCANNING */
                phase?: components["schemas"]["CheckoutPhase"];
                attributes?: {
                    [key: string]: string;
                };
            };
            /** @description Shopper-facing widgets to attach, in order. */
            widgets?: components["schemas"]["WidgetConfig"][];
            /** @description What this client can render, applied to every widget placement that does not declare its own. */
            clientCapabilities?: components["schemas"]["ClientCapabilities"];
        };
        /** @description A consistent copy of the session context, mirroring `SessionContextSnapshot`. */
        SessionContext: {
            phase: components["schemas"]["CheckoutPhase"];
            /** @description Free-form attributes a widget may use, in insertion order. */
            attributes: {
                [key: string]: string;
            };
            saleId: string;
            currency: string;
            storeLocation?: string;
            poiId?: string;
        };
        /** @description Fields absent are left alone. An attribute set to `null` is removed. */
        SessionContextPatch: {
            phase?: components["schemas"]["CheckoutPhase"];
            attributes?: {
                [key: string]: string | null;
            };
        };
        /**
         * @description Where a checkout stands, as far as shopper-facing widgets are concerned. `SCANNING` is the
         *     default of a new session and of a freshly cleared basket; a terminal session enters
         *     `TENDERING` when a settlement starts executing and `COMPLETE` when it succeeds.
         *     `MEMBER_IDENTIFIED` is never set automatically.
         * @enum {string}
         */
        CheckoutPhase: "SCANNING" | "MEMBER_IDENTIFIED" | "TENDERING" | "COMPLETE";
        ForceEndRequest: {
            /** @description Nonblank operational reason, recorded in the host's warning log. */
            reason: string;
        };
        /**
         * @description An immutable snapshot of the session's basket. The payment breakdown totals
         *     (`rebateTotal`, `pointDiscountTotal`, `storedValueTotal`, `cardPaymentTotal`,
         *     `externalPaymentTotal`) are zero while the cart is being built and populated on the
         *     snapshots produced during and after settlement.
         */
        Basket: {
            /** @description Stable identifier of this cart within the session; a new one per `clear`. */
            cartId: string;
            saleTransactionId: components["schemas"]["TransactionIdentification"];
            items: components["schemas"]["BasketLineItem"][];
            /** @description Total tax; the sum of the lines' tax unless overridden. */
            taxTotal: components["schemas"]["Money"];
            originalTotal: components["schemas"]["Money"];
            discountTotal: components["schemas"]["Money"];
            subtotal: components["schemas"]["Money"];
            /** @description `subtotal + taxTotal`. */
            grandTotal: components["schemas"]["Money"];
            rebateTotal: components["schemas"]["Money"];
            pointDiscountTotal: components["schemas"]["Money"];
            storedValueTotal: components["schemas"]["Money"];
            cardPaymentTotal: components["schemas"]["Money"];
            externalPaymentTotal: components["schemas"]["Money"];
            /** Format: date-time */
            updatedAt: string;
        };
        /**
         * @description A line of a basket snapshot. On a return or credit line `originalTotal`, `subtotal`,
         *     `adjustedTotal` and `taxAmount` are negative; quantity and unit price stay positive.
         *     `rebateAmount` and `rebateLabel` are populated only on snapshots produced during payment.
         */
        BasketLineItem: {
            /** @description Session-assigned line id (`"1"`, `"2"`, ...). */
            itemId: string;
            reference?: string;
            sku: string;
            description: string;
            category?: string;
            quantity: number;
            unitPrice: components["schemas"]["Money"];
            discounts: components["schemas"]["BasketDiscount"][];
            discountTotal: components["schemas"]["Money"];
            /** @description Signed line value after register discounts and before terminal rebates. */
            subtotal: components["schemas"]["Money"];
            type: components["schemas"]["BasketItemType"];
            /** @description `unitPrice × quantity`, negated on a return or credit line. */
            originalTotal: components["schemas"]["Money"];
            rebateAmount: components["schemas"]["Money"];
            rebateLabel?: string;
            /** @description `subtotal − rebateAmount`. */
            adjustedTotal: components["schemas"]["Money"];
            taxRate?: components["schemas"]["Money"];
            taxAmount: components["schemas"]["Money"];
            metadata: {
                [key: string]: string;
            };
        };
        /**
         * @description An item as the register adds it (`BasketItem`). Unreferenced items are upserted by SKU and
         *     type; a referenced item stays a distinct line and can be targeted by settlement-time
         *     fulfillment.
         */
        BasketItem: {
            /** @description Register-stable reference for settlement-time fulfillment. */
            reference?: string;
            sku: string;
            description: string;
            /** @default 1 */
            quantity?: number;
            /** @description Non-negative catalog price. */
            unitPrice: components["schemas"]["Money"];
            discounts?: components["schemas"]["BasketDiscount"][];
            /** @default SALE */
            type?: components["schemas"]["BasketItemType"];
            /** @description Optional product category; aids terminal-side offer matching. */
            category?: string;
            /** @description Optional tax rate, e.g. `"0.08875"`. */
            taxRate?: components["schemas"]["Money"];
            /** @description Optional fixed tax amount; overrides `taxRate`. */
            taxAmount?: components["schemas"]["Money"];
            metadata?: {
                [key: string]: string;
            };
        };
        /** @description A register-applied discount on one line. `reference` names a register-known offer; a manual discount has none. */
        BasketDiscount: {
            reference?: string;
            label: string;
            /** @description Positive discount magnitude. */
            amount: components["schemas"]["Money"];
        };
        /**
         * @description Settlement treatment of a basket line. `RETURN` and `CREDIT` totals subtract from the basket.
         * @enum {string}
         */
        BasketItemType: "SALE" | "RETURN" | "CREDIT";
        /**
         * @description One change to the basket: the snapshots before and after, which updater produced it, and
         *     the line-level diff. Lines are paired by `reference` when present, otherwise by SKU and
         *     type, and only within one cart: across a `clear` every previous line is removed and every
         *     current line added. A paired line may appear in several of the changed lists at once.
         */
        BasketChange: {
            previous: components["schemas"]["Basket"];
            current: components["schemas"]["Basket"];
            /** @enum {string} */
            source: "INCREMENTAL" | "BATCH" | "REPLACE" | "CLEAR";
            added: components["schemas"]["BasketLineItem"][];
            removed: components["schemas"]["BasketLineItem"][];
            quantityChanged: components["schemas"]["LineChange"][];
            priceChanged: components["schemas"]["LineChange"][];
            discountsChanged: components["schemas"]["LineChange"][];
            taxChanged: components["schemas"]["LineChange"][];
            /** @description Paired lines whose type, SKU, description, category or metadata differs. */
            detailsChanged: components["schemas"]["LineChange"][];
            taxTotalChanged: boolean;
        };
        /**
         * @description One step of a `mutations` batch, mirroring a `BasketMutation` method and discriminated by
         *     `op` so each operation carries exactly its own arguments. Line-addressed ops take exactly
         *     one of `itemId` or `sku` (the `BySku` variants in Java).
         */
        BasketMutation: components["schemas"]["AddItemMutation"] | components["schemas"]["RemoveItemMutation"] | components["schemas"]["UpdateItemQuantityMutation"] | components["schemas"]["SetDiscountsMutation"] | components["schemas"]["SetTaxRateMutation"] | components["schemas"]["SetTaxAmountMutation"] | components["schemas"]["SetTaxTotalMutation"];
        AddBasketItemRequest: components["schemas"]["BasketItem"] & {
            /** @description An explicit line id for a new SKU, mirroring `addItem(item, itemId)`. */
            itemId?: string;
        };
        /** @description `taxRate` and `taxAmount` are mutually exclusive; a rate clears a fixed amount and vice versa. */
        BasketItemPatch: {
            /** @description Absolute quantity; `0` removes the line. */
            quantity?: number;
            /** @description Replaces the register-applied discounts; an empty list clears them. */
            discounts?: components["schemas"]["BasketDiscount"][];
            taxRate?: components["schemas"]["Money"];
            taxAmount?: components["schemas"]["Money"];
        };
        /** @description Exactly one of `snapshot` (a whole `Basket`) or `items` (a register-owned cart as `BasketItem`s). */
        ReplaceBasketRequest: {
            snapshot?: components["schemas"]["Basket"];
            items?: components["schemas"]["BasketItem"][];
        } & ({
            snapshot: components["schemas"]["Basket"];
        } | {
            items: components["schemas"]["BasketItem"][];
        });
        BasketMutationsRequest: {
            mutations: components["schemas"]["BasketMutation"][];
        };
        TaxTotalRequest: {
            /** @description The override, or `null` to restore item-level computation. */
            amount: components["schemas"]["Money"] | null;
        };
        /**
         * @description The member attached to a visit, mirroring the Java `Member`. `resolved` is `true` once the
         *     Bilt member id is known; a resolved member found by the terminal also carries the loyalty
         *     brand, rewards and point balance it reported.
         */
        Member: {
            resolved: boolean;
            /** @description The Bilt member id; absent while pending. */
            memberId?: string;
            /** @description How the member is being looked up; absent once resolved. Masked. */
            resolver?: components["schemas"]["MemberIdResolver"];
            /** @description `FOUND` for a resolved member; absent while pending. */
            status?: components["schemas"]["IdentifyStatus"];
            /** @description The loyalty program name the terminal reported, e.g. `"K-Club"`. */
            loyaltyBrand?: string;
            rewards: components["schemas"]["Reward"][];
            /** @description `0` when not reported. */
            pointBalance: number;
        };
        /** @description Exactly one of `id` (a member the POS already knows the Bilt member id of) or `resolver` (a member pending resolution). */
        MemberInput: {
            id?: string;
            resolver?: components["schemas"]["MemberIdResolver"];
        } & ({
            id: string;
        } | {
            resolver: components["schemas"]["MemberIdResolver"];
        });
        /**
         * @description How a member that is not yet resolved is to be looked up: the kind of identifier the POS
         *     has on file, its value, and whether the cashier typed it in (a terminal lookup then sends
         *     `EntryMode=Keyed` instead of `File`). In responses and events the `value` is masked for
         *     `PHONE`, `EMAIL` and `CUSTOM` (all but the last four characters); an `ACCOUNT_ID` is shown
         *     in full.
         */
        MemberIdResolver: {
            /** @enum {string} */
            type: "ACCOUNT_ID" | "PHONE" | "EMAIL" | "CUSTOM";
            /** @description The retailer-specific identifier kind; required for `CUSTOM`, absent otherwise. */
            customType?: string;
            value: string;
            /** @default false */
            keyedByCashier?: boolean;
        };
        /**
         * @description Outcome of a member identification attempt.
         * @enum {string}
         */
        IdentifyStatus: "FOUND" | "NOT_FOUND" | "SUSPENDED" | "CANCELLED" | "ERROR";
        /** @description Result of a member identification; member data is present only when `status` is `FOUND`. */
        IdentifyResult: {
            status: components["schemas"]["IdentifyStatus"];
            memberId?: string;
            loyaltyBrand?: string;
            rewards: components["schemas"]["Reward"][];
            pointBalance: number;
        };
        /** @description Options for the terminal-prompted identification. Defaults are any entry mode, any brand, member required. */
        IdentifyOptions: {
            forceEntryModes?: components["schemas"]["ForceEntryMode"][];
            allowedLoyaltyBrands?: string[];
            /**
             * @description Whether the lookup fails when no member is found (`LoyaltyHandling=Required`) or continues without one (`Proposed`).
             * @default true
             */
            requireMember?: boolean;
            timeout?: components["schemas"]["Duration"];
        };
        /** @description A reward or coupon available to an identified member; `rewardRef` is the redemption handle. */
        Reward: {
            rewardRef: string;
            /** @enum {string} */
            type?: "REWARD" | "COUPON" | "POINT";
            description?: string;
            /** Format: date-time */
            expirationDate?: string;
        };
        CardAcquisitionOptions: {
            forceEntryModes?: components["schemas"]["ForceEntryMode"][];
            /**
             * @description Optional routing hint, the Nexo `PaymentTypeEnum`.
             * @enum {string}
             */
            paymentType?: "CASH_ADVANCE" | "CASH_DEPOSIT" | "COMPLETION" | "FIRST_RESERVATION" | "INSTALMENT" | "ISSUER_INSTALMENT" | "NORMAL" | "ONE_TIME_RESERVATION" | "PAID_OUT" | "RECURRING" | "REFUND" | "UPDATE_RESERVATION";
            timeout?: components["schemas"]["Duration"];
        };
        /** @description Card data read without initiating a payment. A host may withhold `rawPan` even where the terminal returned it. */
        CardAcquisitionResult: {
            /** @description e.g. `"****1234"`. */
            maskedPan?: string;
            /** @description Full PAN; only for PLCC cards. */
            rawPan?: string;
            truncatedPan?: string;
            paymentBrand?: string;
            entryMode?: components["schemas"]["EntryMode"];
            cardToken?: string;
            /** @description `MMYY`. */
            expiryDate?: string;
            additionalData: {
                [key: string]: string;
            };
        };
        /** @description Options for the plain terminal input prompts (`requestDigitString`, `requestDecimalString`, `requestTextString`). */
        InputOptions: {
            maxLength?: number;
            minLength?: number;
            /** @description Maximum time the customer has to respond; the terminal default when absent. */
            timeout?: components["schemas"]["Duration"];
            /** @description Extra text line shown under the prompt. */
            additionalText?: string;
            /** @description Second extra text line. */
            additionalText2?: string;
        };
        /**
         * @description Options for `requestConfirmation`. The terminal confirmation screen has two buttons;
         *     custom labels replace the default confirm and cancel texts. For three or more choices use
         *     `requestMenuEntry`.
         */
        ConfirmationOptions: {
            confirmButton?: string;
            cancelButton?: string;
            timeout?: components["schemas"]["Duration"];
        };
        MenuOptions: {
            /**
             * @description Whether the customer may pick several entries.
             * @default false
             */
            multiSelect?: boolean;
            additionalText?: string;
            timeout?: components["schemas"]["Duration"];
        };
        /**
         * @description The entry (or entries, for multi-select menus) the customer picked. Indices are zero-based
         *     positions into the `entries` list of the request; the first element of each list is the
         *     selection for a single-select menu.
         */
        MenuSelection: {
            indices: number[];
            values: string[];
        };
        /**
         * @description Options for PIN entry and verification. For the verify modes the reference the terminal
         *     verifies against is addressed by `keyReference` and `pinVerificationMethod`; PIN blocks
         *     never travel in the clear. Length constraints are terminal-side policy.
         */
        PinOptions: {
            timeout?: components["schemas"]["Duration"];
            keyReference?: string;
            pinVerificationMethod?: string;
        };
        PinResult: {
            mode: components["schemas"]["PinMode"];
            /** @description For the verify modes, whether the PIN matched. */
            verified: boolean;
            /** @description The encrypted PIN block structure (Nexo `CardholderPIN`, a CMS envelope); absent for verify-only. */
            cardholderPin?: components["schemas"]["NexoObject"];
        };
        /** @description A signature captured on the terminal. */
        Signature: {
            /** @description Raw image bytes, Base64. */
            imageData: string;
            /** @description Image format, e.g. `"PNG"`. */
            format: string;
            /** @description Pixel width, `0` when unknown. */
            width: number;
            /** @description Pixel height, `0` when unknown. */
            height: number;
            /** Format: date-time */
            capturedAt?: string;
        };
        /**
         * @description Identifies a stored value (gift) card. The Java factories map as follows:
         *     `StoredValueCard.number(n)` is `{ storedValueId: n, identificationType: PAN, entryMode: KEYED }`,
         *     `scanned(b)` is `{ storedValueId: b, identificationType: BAR_CODE, entryMode: SCANNED }`,
         *     `swiped()` is `{ identificationType: PAN, entryMode: MAG_STRIPE }` with no id (the terminal
         *     prompts for the swipe).
         */
        StoredValueCard: {
            /** @description Card number or barcode; absent for a swiped card. */
            storedValueId?: string;
            /**
             * @description The Nexo `IdentificationTypeEnum`.
             * @enum {string}
             */
            identificationType: "ACCOUNT_NUMBER" | "BAR_CODE" | "ISO_TRACK2" | "PAN" | "PHONE_NUMBER";
            entryMode: components["schemas"]["EntryMode"];
            /**
             * @description The Nexo `StoredValueAccountTypeEnum`.
             * @default GIFT_CARD
             * @enum {string}
             */
            accountType?: "GIFT_CARD" | "OTHER" | "PHONE_CARD";
            /** @description Stored value provider, e.g. `"givex"`; the terminal default when absent. */
            provider?: string;
            /** @description `MMYY`. */
            expiryDate?: string;
        };
        StoredValueBalance: {
            balance: components["schemas"]["Money"];
            currency: string;
        };
        /** @description Outcome of a stored value operation (activate, load, unload, reserve, reverse, duplicate). */
        StoredValueOperationResult: {
            /**
             * @description The operation the terminal performed (Nexo `StoredValueTransactionTypeEnum`); absent when the terminal's response did not carry it.
             * @enum {string}
             */
            transactionType?: "ACTIVATE" | "DUPLICATE" | "LOAD" | "RESERVE" | "REVERSE" | "UNLOAD";
            /** @description Amount moved; absent when not echoed. */
            amount?: components["schemas"]["Money"];
            /** @description Balance on the card after the operation, when reported. */
            currentBalance?: components["schemas"]["Money"];
            currency?: string;
            /** @description Terminal reference, needed for `storedValueReverse`. */
            poiTransactionId?: string;
            /** Format: date-time */
            poiTransactionTimestamp?: string;
            /** @description The stored value provider's transaction reference. */
            hostTransactionId?: string;
        };
        /** @description The register's plan for resolving a basket, mirroring `SettlementOptions`; all fields optional. */
        SettlementOptions: {
            /** @default false */
            disableRebates?: boolean;
            /** @default false */
            disablePoints?: boolean;
            /** @default false */
            disableAward?: boolean;
            /** @description Positive cashback requested with the card payment. */
            cashback?: components["schemas"]["Money"];
            /** @description Overrides the display shown while the card payment is processing. */
            paymentProcessingDisplay?: components["schemas"]["DisplayPayload"];
            refunds?: components["schemas"]["RefundAllocation"][];
            fulfillments?: components["schemas"]["StoredValueLoad"][];
            /** @default REFUND_THEN_CHARGE */
            settlementType?: components["schemas"]["SettlementType"];
        };
        /**
         * @description Final outcome of a successful settlement. `movements` is the authoritative ledger of what
         *     remained committed; movement events during the run are provisional. The loyalty award is
         *     best-effort: a failed award does not fail the checkout and is reported in `warnings`.
         */
        SettlementResult: {
            success: boolean;
            finalBasket: components["schemas"]["Basket"];
            authorizedAmount: components["schemas"]["Money"];
            storedValueAmountUsed: components["schemas"]["Money"];
            storedValueLoadedAmount: components["schemas"]["Money"];
            cardAmountCharged: components["schemas"]["Money"];
            externalPaymentAmount: components["schemas"]["Money"];
            approvalCode?: string;
            acquirerTransactionId?: string;
            paymentBrand?: string;
            redeemedRebates: components["schemas"]["RedeemedRebate"][];
            totalRebateAmount: components["schemas"]["Money"];
            pointsRedeemed: number;
            pointsMonetaryValue: components["schemas"]["Money"];
            earnedRewards: components["schemas"]["EarnedReward"][];
            totalPointsEarned: number;
            pointsBalance: number;
            promotionMessages: string[];
            customerReceipt?: components["schemas"]["Receipt"];
            merchantReceipt?: components["schemas"]["Receipt"];
            /** @description Terminal reference of the card payment, for void and refund. */
            poiTransactionId?: string;
            /** Format: date-time */
            poiTransactionTimestamp?: string;
            storedValuePoiTransactionId?: string;
            /** Format: date-time */
            storedValuePoiTransactionTimestamp?: string;
            awardPoiTransactionId?: string;
            /** Format: date-time */
            awardPoiTransactionTimestamp?: string;
            rebatePoiTransactionId?: string;
            /** Format: date-time */
            rebatePoiTransactionTimestamp?: string;
            redemptionPoiTransactionId?: string;
            /** Format: date-time */
            redemptionPoiTransactionTimestamp?: string;
            cardRefundedAmount: components["schemas"]["Money"];
            storedValueRefundedAmount: components["schemas"]["Money"];
            externalRefundedAmount: components["schemas"]["Money"];
            loyaltyRefundedAmount: components["schemas"]["Money"];
            movements: components["schemas"]["SettlementMovement"][];
            warnings: string[];
        };
        /** @description One money or loyalty movement committed as part of a settlement. */
        SettlementMovement: {
            step: components["schemas"]["SettlementStep"];
            target: components["schemas"]["SettlementTarget"];
            amount: components["schemas"]["Money"];
            saleTransactionId?: string;
            poiTransactionId?: string;
            /** Format: date-time */
            poiTransactionTimestamp?: string;
            memberId?: string;
            points?: number;
            pointBalance?: number;
            externalTenderType?: string;
            externalReference?: string;
        };
        /** @description What the `onError` handler sees when a charge-side step failed. */
        SettlementFailure: {
            /** @description Absent when the failure did not occur inside a specific step (a pre-sequence rejection or refused recovery). */
            step?: components["schemas"]["SettlementStep"];
            error: components["schemas"]["SessionError"];
            /** @description The amount the failed step was responsible for resolving. */
            amountDue: components["schemas"]["Money"];
            committedMovements: components["schemas"]["SettlementMovement"][];
            /**
             * @description `INDETERMINATE` when TransactionStatus could not establish the terminal outcome; skip and external replacement are then invalid.
             * @enum {string}
             */
            outcomeCertainty: "DEFINITIVE" | "INDETERMINATE";
            messageCategory?: string;
            serviceId?: string;
        };
        /**
         * @description The register's answer to a `RECOVERY_REQUIRED` step. `RETRY` retries only the failed step
         *     (for an indeterminate failure it checks TransactionStatus again and never resends);
         *     `SKIP` skips an optional step (never the final card tender or a fulfillment); `EXTERNAL`
         *     records `externalPayment` for the outstanding balance and continues; `ABORT` unwinds the
         *     committed charge-side steps and fails the settlement; `ABANDON` stops recovery and hands
         *     the partial settlement to the register as an `AbandonedSettlementRecord`.
         */
        SettlementRecovery: {
            /** @enum {string} */
            action: "RETRY" | "SKIP" | "EXTERNAL" | "ABORT" | "ABANDON";
            /** @description Required for `EXTERNAL`. */
            externalPayment?: components["schemas"]["ExternalPayment"];
        };
        /** @description What the `beforeStep` handler sees before each payment step. */
        SettlementContext: {
            step: components["schemas"]["SettlementStep"];
            currentBasket: components["schemas"]["Basket"];
            currentTotal: components["schemas"]["Money"];
            /** @description The basket's shared sale transaction id, used when the reply names nothing else. */
            defaultTransactionId: string;
            priorSteps: components["schemas"]["CommittedStep"][];
        };
        /** @description The manual-takeover record produced when the register abandons recovery; every committed movement becomes its reconciliation responsibility. */
        AbandonedSettlementRecord: {
            settlementId: string;
            /** Format: date-time */
            abandonedAt: string;
            basket: components["schemas"]["Basket"];
            options: components["schemas"]["SettlementOptions"];
            memberId?: string;
            failure: components["schemas"]["SettlementFailure"];
            outstandingAmount: components["schemas"]["Money"];
            committedMovements: components["schemas"]["SettlementMovement"][];
        };
        /** @description References from a completed sale that a later session uses to void the whole transaction or allocate return settlement. */
        OriginalSaleRecord: {
            cardPoiTransactionId?: string;
            /** Format: date-time */
            cardPoiTransactionTimestamp?: string;
            storedValuePoiTransactionId?: string;
            /** Format: date-time */
            storedValuePoiTransactionTimestamp?: string;
            storedValueLoads?: components["schemas"]["StoredValueLoadRecord"][];
            rebatePoiTransactionId?: string;
            /** Format: date-time */
            rebatePoiTransactionTimestamp?: string;
            redemptionPoiTransactionId?: string;
            /** Format: date-time */
            redemptionPoiTransactionTimestamp?: string;
            awardPoiTransactionId?: string;
            /** Format: date-time */
            awardPoiTransactionTimestamp?: string;
            memberId?: string;
        };
        /** @description Outcome of a refund. A linked refund also reverses loyalty points awarded on the original transaction, best-effort. */
        RefundResult: {
            success: boolean;
            /** @description Absent when the terminal did not echo it. */
            refundedAmount?: components["schemas"]["Money"];
            approvalCode?: string;
            poiTransactionId?: string;
            /** Format: date-time */
            poiTransactionTimestamp?: string;
            customerReceipt?: components["schemas"]["Receipt"];
            merchantReceipt?: components["schemas"]["Receipt"];
            /** @description `0` for unlinked. */
            pointsReversed: number;
            remainingPointBalance: number;
        };
        /**
         * @description Outcome of `voidTransaction`: the movements *this call* reversed. A resumed void after a
         *     partial abort does not restate what earlier attempts reversed; its amount, reference and
         *     receipts cover only the legs sent this time and may all be absent.
         */
        VoidResult: {
            success: boolean;
            reversedAmount?: components["schemas"]["Money"];
            poiTransactionId?: string;
            /** Format: date-time */
            poiTransactionTimestamp?: string;
            customerReceipt?: components["schemas"]["Receipt"];
            merchantReceipt?: components["schemas"]["Receipt"];
            pointsReversed: number;
            remainingPointBalance: number;
        };
        /**
         * @description How a reversal proceeds after a step failed. `RETRY` re-sends the step; `SKIP` leaves the
         *     movement standing and continues (a void then fails as incomplete when a money leg was
         *     skipped; a refund's tender step may be skipped outright and the sale stays voidable);
         *     `ABORT` stops, with already-reversed steps standing so the operation can be retried.
         * @enum {string}
         */
        ReversalDecision: "RETRY" | "SKIP" | "ABORT";
        /**
         * @description One reversible leg of a sale. A void reverses the known legs in this order:
         *     `STORED_VALUE_LOAD`, `CARD`, `STORED_VALUE`, `REDEMPTION`, `REBATE`, `AWARD`. A refund flow
         *     has at most `CARD` (the tender refund) and `AWARD`.
         * @enum {string}
         */
        ReversalStep: "STORED_VALUE_LOAD" | "CARD" | "STORED_VALUE" | "REDEMPTION" | "REBATE" | "AWARD";
        /** @description By default the referenced request is assumed to be a payment and no receipt data is requested. */
        TransactionStatusOptions: {
            /** @default PAYMENT */
            originalCategory?: components["schemas"]["MessageCategory"];
            /**
             * @description Whether the terminal should include the original receipts, to reprint after a crash or connection loss.
             * @default false
             */
            receiptReprint?: boolean;
            /** @description Receipt kinds to include when reprinting; defaults to customer and cashier receipts. */
            documentQualifiers?: components["schemas"]["DocumentQualifier"][];
        };
        /**
         * @description When the original transaction was found the terminal repeats its response; exactly one of
         *     the typed responses is present, as `messageCategory` indicates. The responses are Nexo
         *     structures carried through unchanged.
         */
        TransactionStatusResult: {
            found: boolean;
            /** @description `"Payment"`, `"Loyalty"`, `"StoredValue"` or `"Reversal"`. */
            messageCategory?: string;
            paymentResponse?: components["schemas"]["NexoObject"];
            loyaltyResponse?: components["schemas"]["NexoObject"];
            storedValueResponse?: components["schemas"]["NexoObject"];
            reversalResponse?: components["schemas"]["NexoObject"];
        };
        /**
         * @description One lazy operation of `TerminalShopperSession`, discriminated by `type`. Each type maps to
         *     the Java method of the same name; `end` and `forceEnd` are created through their own
         *     endpoints and are not accepted here.
         */
        OperationRequest: components["schemas"]["IdentifyMemberRequest"] | components["schemas"]["AcquireCardRequest"] | components["schemas"]["InputPromptRequest"] | components["schemas"]["ConfirmationRequest"] | components["schemas"]["MenuEntryRequest"] | components["schemas"]["SignatureRequest"] | components["schemas"]["AmountConfirmationRequest"] | components["schemas"]["PinRequest"] | components["schemas"]["StoredValueCardRequest"] | components["schemas"]["StoredValueAmountRequest"] | components["schemas"]["StoredValueReverseRequest"] | components["schemas"]["SetStoredValueCardRequest"] | components["schemas"]["SettleRequest"] | components["schemas"]["RefundRequest"] | components["schemas"]["RefundUnlinkedRequest"] | components["schemas"]["VoidTransactionRequest"] | components["schemas"]["TransactionStatusRequest"] | components["schemas"]["UpdateDisplayRequest"] | components["schemas"]["UpdateInputDisplayRequest"];
        /**
         * @description An operation resource, discriminated by `type` so `result` is typed per operation. The
         *     `type` set is `OperationRequest`'s plus `end` and `forceEnd`.
         */
        Operation: components["schemas"]["IdentifyMemberOperation"] | components["schemas"]["AcquireCardOperation"] | components["schemas"]["StringOperation"] | components["schemas"]["DecimalOperation"] | components["schemas"]["BooleanOperation"] | components["schemas"]["MenuEntryOperation"] | components["schemas"]["SignatureOperation"] | components["schemas"]["PinOperation"] | components["schemas"]["StoredValueBalanceOperation"] | components["schemas"]["StoredValueOperation"] | components["schemas"]["SettleOperation"] | components["schemas"]["RefundOperation"] | components["schemas"]["VoidTransactionOperation"] | components["schemas"]["TransactionStatusOperation"] | components["schemas"]["VoidResultOperation"];
        /**
         * @description `queued` until the lane reaches it, `running` while it executes, `awaitingReply` while a
         *     step waits on the register, then exactly one of `succeeded`, `failed` or `aborted`.
         * @enum {string}
         */
        OperationStatus: "queued" | "running" | "awaitingReply" | "succeeded" | "failed" | "aborted";
        /**
         * @description A register handler the host is waiting on, emitted as `operation.step` while the
         *     operation is `awaitingReply`. The client answers before `deadlineAt` with
         *     `POST .../operations/{id}/reply`; otherwise `default` applies. The deadline is the host's
         *     (30 seconds for totals and transaction ids, 120 seconds for recovery and reversal
         *     decisions by default, configurable per host).
         *
         *     | Java handler | kind | reply | default |
         *     |---|---|---|---|
         *     | `beforeStep` | `BEFORE_STEP` | `saleTransactionId` | the basket's shared sale transaction id (`context.defaultTransactionId`) |
         *     | `onRebatesRedeemed`, `onPointsRedeemed`, `onGiftCardPayment` | `TOTAL_REQUIRED` | `total` | `suggestedTotal`: the previous total minus the step's amount |
         *     | `SettlementFlow.onError` | `RECOVERY_REQUIRED` | `recovery` | `ABORT` |
         *     | `ReversalFlow.onError` | `REVERSAL_DECISION_REQUIRED` | `decision` | the Java default policy: `ABORT` for a money step or a loyalty movement that is the substance of the reversal, `SKIP` for a loyalty movement riding along with a money step; `default.decision` says which |
         *
         *     `onMovement` and the per-movement callbacks, `onCardCharged` and `onAwarded` are
         *     `operation.movement` events and take no reply.
         */
        OperationStep: components["schemas"]["BeforeStepStep"] | components["schemas"]["TotalRequiredStep"] | components["schemas"]["RecoveryRequiredStep"] | components["schemas"]["ReversalDecisionRequiredStep"];
        /**
         * @description Which register handler a step stands for; see `OperationStep`.
         * @enum {string}
         */
        StepKind: "BEFORE_STEP" | "TOTAL_REQUIRED" | "RECOVERY_REQUIRED" | "REVERSAL_DECISION_REQUIRED";
        /** @description The answer to a pending step; set the one field the step kind takes. */
        StepReply: {
            stepId: string;
            /** @description For `BEFORE_STEP`; empty or equal to the default keeps the basket's transaction and its timestamp. */
            saleTransactionId?: string;
            /** @description For `TOTAL_REQUIRED`; the running total for the next step. */
            total?: components["schemas"]["Money"];
            /** @description For `RECOVERY_REQUIRED`. */
            recovery?: components["schemas"]["SettlementRecovery"];
            /** @description For `REVERSAL_DECISION_REQUIRED`. */
            decision?: components["schemas"]["ReversalDecision"];
        };
        /** @description The `RetailMedia.builder()` options the client may set; the host supplies the ad decision service and credentials. */
        WidgetConfig: {
            /** @enum {string} */
            type: "retail-media";
            placements: components["schemas"]["PlacementConfig"][];
            /** @description Phases in which media is requested and shown; default every phase except `COMPLETE`. */
            eligiblePhases?: components["schemas"]["CheckoutPhase"][];
            /** @description How long a decision may take before the placement stays blank; default `PT0.5S`. */
            decisionTimeout?: components["schemas"]["Duration"];
            /** @description Overrides how long a rendering stays before refresh; default each rendering's own TTL. */
            renderingTtl?: components["schemas"]["Duration"];
        };
        WidgetState: {
            /** @enum {string} */
            type: "retail-media";
            paused: boolean;
            placements: components["schemas"]["PlacementConfig"][];
            /** @description `true` when the widget failed to attach and runs no decisions this session; `error` says why. */
            inert?: boolean;
            error?: components["schemas"]["SessionError"];
        };
        /**
         * @description The client's report of a shopper interaction with a rendering (`ActionSink`). `perform`
         *     needs `token` (and the CTA's `action` when known, for cross-checking); the other kinds name
         *     the rendering by `creativeId` and `placement` alone.
         */
        WidgetAction: {
            /** @enum {string} */
            kind: "perform" | "viewed" | "dismissed" | "completed";
            creativeId: string;
            placement: string;
            action?: components["schemas"]["Action"];
            token?: string;
        };
        /**
         * @description A structured creative ready to be drawn: media, copy, up to two calls to action, a TTL and
         *     tracking beacons. Carries no PII. A client still showing a rendering past its TTL should
         *     expect a replacement or a `widget.clear` and must not act on its CTAs afterwards.
         */
        Rendering: {
            creativeId: string;
            placement: string;
            media: components["schemas"]["MediaSpec"];
            headline: string;
            body?: string;
            cta?: components["schemas"]["Cta"];
            secondary?: components["schemas"]["Cta"];
            ttl: components["schemas"]["Duration"];
            /** @description Beacons keyed by event name, e.g. `impression`, `viewability`. */
            tracking: {
                [key: string]: string;
            };
        };
        /**
         * @description A price action the ad platform validated for this checkout: exactly one of `amount` or
         *     `percentage` off, applying to the basket or to one SKU. How the register realises it is its
         *     choice; the creative's copy is never trusted for pricing.
         */
        Offer: {
            id: string;
            /** @enum {string} */
            scope: "BASKET" | "LINE_ITEM";
            /** @description Required for `LINE_ITEM`. */
            sku?: string;
            amount?: components["schemas"]["Money"];
            /** @description `0 < p <= 100`. */
            percentage?: components["schemas"]["Money"];
            /** Format: date-time */
            expiry?: string;
            creativeId: string;
        };
        /** @description One thing the shopper did with a creative, as reported to the ad platform. Informational. */
        AdInteraction: {
            creativeId: string;
            placement: string;
            /** @enum {string} */
            kind: "SHOWN" | "VIEWED" | "TAPPED" | "CTA_ACCEPTED" | "SEND_TO_PHONE_REQUESTED" | "DISMISSED" | "COMPLETED";
            /** Format: date-time */
            timestamp: string;
            /** @description The call to action involved, for tap-driven kinds. */
            action?: components["schemas"]["Action"];
        };
        /**
         * @description One event on a session's stream, discriminated by `type`. `seq` increases by one per event
         *     within a session and is what a client passes back as `since`.
         */
        Event: components["schemas"]["SessionStartedEvent"] | components["schemas"]["BasketChangedEvent"] | components["schemas"]["MemberChangedEvent"] | components["schemas"]["ContextChangedEvent"] | components["schemas"]["OperationStepEvent"] | components["schemas"]["OperationMovementEvent"] | components["schemas"]["OperationCompletedEvent"] | components["schemas"]["WidgetRenderingEvent"] | components["schemas"]["WidgetClearEvent"] | components["schemas"]["WidgetOfferEvent"] | components["schemas"]["WidgetInteractionEvent"] | components["schemas"]["BackgroundErrorEvent"] | components["schemas"]["SessionEndedEvent"];
        /** @description A movement that completed before a whole-sale void stopped. */
        ReversedMovement: {
            step: components["schemas"]["ReversalStep"];
            poiTransactionId: string;
        };
        /**
         * @description The Nexo `DocumentQualifierEnum`.
         * @enum {string}
         */
        DocumentQualifier: "CASHIER_RECEIPT" | "CUSTOMER_RECEIPT" | "DOCUMENT" | "JOURNAL" | "SALE_RECEIPT" | "VOUCHER";
        /**
         * @description `local` has no terminal (basket and member state only); `terminal` is bracketed on a terminal.
         * @enum {string}
         */
        SessionKind: "local" | "terminal";
        /**
         * @description How a placement is drawn, reported to the ad platform so it serves what the surface can do justice to.
         * @enum {string}
         */
        SurfaceKind: "NATIVE" | "WEB" | "TERMINAL" | "HANDOFF" | "HOSTED";
        /**
         * @description The kinds of media a surface may be asked to draw; `HTML` only on web-capable surfaces.
         * @enum {string}
         */
        MediaType: "IMAGE" | "VIDEO" | "HTML";
        /** @description A named zone the client has a surface for; `surfaceKind` and `formats` default to the session's `clientCapabilities`. */
        PlacementConfig: {
            /** @description The platform's placement id, e.g. `lane-banner`. */
            id: string;
            surfaceKind?: components["schemas"]["SurfaceKind"];
            formats?: components["schemas"]["MediaType"][];
        };
        /**
         * @description What a call to action asks for. Every action is validated by the host against the ad platform before anything happens.
         * @enum {string}
         */
        Action: "APPLY_OFFER" | "SEND_TO_PHONE" | "DETAILS" | "DISMISS";
        /** @description What the client can render, folded into the `Capabilities` the host sends with every ad decision. */
        ClientCapabilities: {
            formats: components["schemas"]["MediaType"][];
            surfaceKind: components["schemas"]["SurfaceKind"];
            /**
             * @description Calls to action the client handles. Accepted and ignored: the Java SDK's `Surface` has no
             *     counterpart, so the host still offers every action and the client decides which to render.
             */
            actions?: components["schemas"]["Action"][];
        };
        /** @enum {string} */
        SessionState: "open" | "ending" | "ended";
        /** @description A payment collected and owned by the register, accepted only as a replacement for a failed final card tender; its amount must equal the outstanding balance. */
        ExternalPayment: {
            /** @description `"CASH"` or an integrator-defined tender name. */
            tenderType: string;
            amount: components["schemas"]["Money"];
            /** @description A register transaction reference. */
            reference?: string;
        };
        StepBase: {
            operationId: string;
            /** @description Unique per step; the reply names it. */
            stepId: string;
            kind: components["schemas"]["StepKind"];
            /** Format: date-time */
            deadlineAt: string;
            /** @description The reply the host applies when none arrives in time. */
            default: components["schemas"]["StepReply"];
        };
        /**
         * @description A step of the settlement sequence.
         * @enum {string}
         */
        SettlementStep: "REBATE_REDEMPTION" | "POINT_REDEMPTION" | "STORED_VALUE_CHARGE" | "CARD_CHARGE" | "EXTERNAL_PAYMENT" | "STORED_VALUE_LOAD" | "AWARD" | "CARD_REFUND" | "STORED_VALUE_REFUND" | "EXTERNAL_REFUND" | "POINT_REDEMPTION_REFUND" | "REBATE_REFUND" | "AWARD_REFUND";
        /** @description The sale transaction identity every wire request of a basket's checkout carries. */
        TransactionIdentification: {
            transactionId: string;
            /** Format: date-time */
            timestamp: string;
        };
        CommittedStep: {
            step: components["schemas"]["SettlementStep"];
            saleTransactionId?: string;
            poiTransactionId?: string;
            /** Format: date-time */
            poiTransactionTimestamp?: string;
            success: boolean;
        };
        BeforeStepStep: components["schemas"]["StepBase"] & {
            /** @enum {string} */
            kind?: "BEFORE_STEP";
            context: components["schemas"]["SettlementContext"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            kind: "BEFORE_STEP";
        };
        RedeemedRebate: {
            /** @description The basket line the rebate applies to; absent for cart-level. */
            itemId?: string;
            sku?: string;
            amount: components["schemas"]["Money"];
            label?: string;
            promotionRef?: string;
        };
        /** @description What `onRebatesRedeemed` sees; the reply's `total` is the running total for the next step, typically `suggestedTotal`. */
        RebateRedemptionResult: {
            rebates: components["schemas"]["RedeemedRebate"][];
            totalRebateAmount: components["schemas"]["Money"];
            previousTotal: components["schemas"]["Money"];
            /** @description `previousTotal − totalRebateAmount`. */
            suggestedTotal: components["schemas"]["Money"];
            updatedBasket: components["schemas"]["Basket"];
        };
        PointRedemptionResult: {
            pointsUsed: number;
            monetaryValue: components["schemas"]["Money"];
            previousTotal: components["schemas"]["Money"];
            suggestedTotal: components["schemas"]["Money"];
            remainingPointBalance: number;
        };
        /** @description What `onGiftCardPayment` sees. With an insufficient balance the charge is partial and `suggestedTotal` carries the remainder for the card step. */
        GiftCardPaymentResult: {
            amountCharged: components["schemas"]["Money"];
            remainingCardBalance?: components["schemas"]["Money"];
            previousTotal: components["schemas"]["Money"];
            suggestedTotal: components["schemas"]["Money"];
        };
        TotalRequiredStep: components["schemas"]["StepBase"] & {
            /** @enum {string} */
            kind?: "TOTAL_REQUIRED";
            /** @enum {string} */
            step: "REBATE_REDEMPTION" | "POINT_REDEMPTION" | "STORED_VALUE_CHARGE";
            rebates?: components["schemas"]["RebateRedemptionResult"];
            points?: components["schemas"]["PointRedemptionResult"];
            giftCard?: components["schemas"]["GiftCardPaymentResult"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            kind: "TOTAL_REQUIRED";
        };
        /** @description The basket obligation a settlement instruction or movement resolves. */
        SettlementTarget: {
            /** @enum {string} */
            type: "SALES" | "REFUNDS" | "BASKET_LINE";
            /** @description The register reference of the line, for `BASKET_LINE`. */
            basketReference?: string;
        };
        RecoveryRequiredStep: components["schemas"]["StepBase"] & {
            /** @enum {string} */
            kind?: "RECOVERY_REQUIRED";
            failure: components["schemas"]["SettlementFailure"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            kind: "RECOVERY_REQUIRED";
        };
        ReversalDecisionRequiredStep: components["schemas"]["StepBase"] & {
            /** @enum {string} */
            kind?: "REVERSAL_DECISION_REQUIRED";
            step?: components["schemas"]["ReversalStep"];
            error: components["schemas"]["SessionError"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            kind: "REVERSAL_DECISION_REQUIRED";
        };
        OperationBase: {
            id: string;
            type: string;
            status: components["schemas"]["OperationStatus"];
            /** Format: date-time */
            createdAt: string;
            /** Format: date-time */
            startedAt?: string;
            /** Format: date-time */
            completedAt?: string;
            /** @description Present when `failed` or `aborted`. */
            error?: components["schemas"]["SessionError"];
            /** @description The step awaiting a reply while `awaitingReply`. */
            pendingStep?: components["schemas"]["OperationStep"];
            /**
             * @description The Nexo `ServiceID` the host sent the terminal request with, once it has been sent;
             *     absent for operations that send none. Keep it for an operation whose outcome is
             *     uncertain (the connection dropped, or it ended `failed` without a reply), then pass
             *     it as `originalServiceId` of a `getTransactionStatus` operation.
             */
            serviceId?: string;
        };
        IdentifyMemberOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "identifyMember";
            result?: components["schemas"]["IdentifyResult"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "identifyMember";
        };
        /**
         * @description How an identifier or card was actually captured.
         * @enum {string}
         */
        EntryMode: "CONTACTLESS" | "FILE" | "ICC" | "KEYED" | "MAG_STRIPE" | "MANUAL" | "MOBILE" | "RFID" | "SCANNED" | "SYNCHRONOUS_ICC" | "TAPPED";
        AcquireCardOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "acquireCard";
            result?: components["schemas"]["CardAcquisitionResult"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "acquireCard";
        };
        StringOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "requestDigitString" | "requestTextString";
            result?: string;
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "requestDigitString" | "requestTextString";
        };
        DecimalOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "requestDecimalString";
            result?: components["schemas"]["Money"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "requestDecimalString";
        };
        BooleanOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "requestConfirmation" | "requestAmountConfirmation";
            result?: boolean;
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "requestConfirmation" | "requestAmountConfirmation";
        };
        MenuEntryOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "requestMenuEntry";
            result?: components["schemas"]["MenuSelection"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "requestMenuEntry";
        };
        SignatureOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "requestSignature";
            result?: components["schemas"]["Signature"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "requestSignature";
        };
        /**
         * @description Kind of PIN operation, mapping to the Nexo `PINRequestType`.
         * @enum {string}
         */
        PinMode: "PIN_ENTER" | "PIN_VERIFY" | "PIN_VERIFY_ONLY";
        PinOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "requestPinEntry" | "requestPinVerify" | "requestPinVerifyOnly";
            result?: components["schemas"]["PinResult"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "requestPinEntry" | "requestPinVerify" | "requestPinVerifyOnly";
        };
        StoredValueBalanceOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "storedValueBalance";
            result?: components["schemas"]["StoredValueBalance"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "storedValueBalance";
        };
        StoredValueOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "storedValueActivate" | "storedValueLoad" | "storedValueUnload" | "storedValueDeactivate" | "storedValueReserve" | "storedValueReverse" | "storedValueDuplicate";
            result?: components["schemas"]["StoredValueOperationResult"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "storedValueActivate" | "storedValueLoad" | "storedValueUnload" | "storedValueDeactivate" | "storedValueReserve" | "storedValueReverse" | "storedValueDuplicate";
        };
        EarnedReward: {
            /** @enum {string} */
            type?: "REWARD" | "COUPON" | "POINT";
            description?: string;
            quantity: number;
            rewardRef?: string;
        };
        DisplayHeaderFooter: {
            text: string;
        };
        DisplayQrCode: {
            /**
             * @description Symbology, e.g. `qr`, `barcode128`, `pdf417`, `datamatrix`.
             * @default qr
             */
            type?: string;
            header?: components["schemas"]["DisplayHeaderFooter"];
            /** Format: uri-reference */
            data: string;
            callToAction?: string;
            footer?: components["schemas"]["DisplayHeaderFooter"];
        };
        DisplayImage: {
            /** @description Base64-encoded image bytes. */
            data: string;
            /** @default image/png */
            mediaType?: string;
            altText?: string;
        };
        /** @description Currency may be an ISO 4217 code or a display symbol. */
        DisplayMoney: {
            currency: string;
            value: components["schemas"]["Money"];
        };
        /** @description One receipt row; which children are meaningful depends on `kind`. */
        DisplayLineItem: {
            /**
             * @default item
             * @enum {string}
             */
            kind?: "item" | "return" | "void" | "separator" | "spacer";
            description?: string;
            subtitle?: string;
            image?: components["schemas"]["DisplayImage"];
            quantity?: components["schemas"]["Money"];
            unitPrice?: components["schemas"]["DisplayMoney"];
            amount?: components["schemas"]["DisplayMoney"];
            originalAmount?: components["schemas"]["DisplayMoney"];
            /** @description Discount groups applied to the item, each a label with descriptions. */
            sections?: {
                label: string;
                items: string[];
            }[];
        };
        DisplayLabeledAmount: {
            description?: string;
            amount?: components["schemas"]["DisplayMoney"];
        };
        /** @description An itemised purchase summary. */
        DisplayReceipt: {
            qrCode?: components["schemas"]["DisplayQrCode"];
            /** @description A waiting message shown while the transaction is processed; an empty string falls back to the terminal default. */
            waiting?: string;
            header?: components["schemas"]["DisplayHeaderFooter"];
            lineItems?: components["schemas"]["DisplayLineItem"][];
            subtotal?: components["schemas"]["DisplayLabeledAmount"];
            /** @description Order-level discounts (loyalty rewards, coupons). */
            adjustments?: components["schemas"]["DisplayLabeledAmount"][];
            tax?: {
                taxItems?: components["schemas"]["DisplayLabeledAmount"][];
                taxTotal?: components["schemas"]["DisplayLabeledAmount"];
            };
            total?: components["schemas"]["DisplayLabeledAmount"];
            footer?: components["schemas"]["DisplayHeaderFooter"];
        };
        /** @description A title and zero or more text lines (`common.xsd` `DisplayType`). */
        DisplayText: {
            title?: string;
            text?: string[];
        };
        DisplayStandby: {
            /** @description A seasonal or branded visual variant, e.g. `christmas`. */
            theme?: string;
            display?: components["schemas"]["DisplayText"];
        };
        DisplayWaiting: {
            display?: components["schemas"]["DisplayText"];
        };
        /** @enum {string} */
        RefundAllocationType: "CARD" | "STORED_VALUE" | "STORE_CREDIT" | "EXTERNAL" | "POINT_REDEMPTION" | "REBATE" | "AWARD";
        /**
         * @description A register-selected refund or restoration movement for a settlement with returns; one per
         *     movement the register wants recorded or executed. `CARD` without an original reference is
         *     an unlinked card refund. `STORED_VALUE`, `POINT_REDEMPTION`, `REBATE` and `AWARD` require
         *     `originalPoiTransactionId`; the loyalty types also `memberId`; `STORE_CREDIT` requires
         *     `storedValueCard`. `amount` is positive except for `AWARD`, which is bookkeeping and uses
         *     zero. `EXTERNAL` counts toward the return total but sends no terminal movement.
         */
        RefundAllocation: {
            type: components["schemas"]["RefundAllocationType"];
            /** @default 0 */
            amount?: components["schemas"]["Money"];
            originalPoiTransactionId?: string;
            /** Format: date-time */
            originalPoiTransactionTimestamp?: string;
            storedValueCard?: components["schemas"]["StoredValueCard"];
            memberId?: string;
        };
        /** @description Settlement-time stored value fulfillment of a referenced sale line; the card is loaded with the line's pre-discount value. */
        StoredValueLoad: {
            basketReference: string;
            /** @enum {string} */
            type: "ACTIVATE" | "RELOAD";
            card: components["schemas"]["StoredValueCard"];
        };
        /**
         * @description How a settlement containing sale, credit and return lines moves money.
         *     `REFUND_THEN_CHARGE` (the default) executes return refund allocations first, then charges
         *     sales less credits. `NET` moves only the signed difference: a positive total is charged, a
         *     negative one refunded, zero sends no monetary movement.
         * @enum {string}
         */
        SettlementType: "REFUND_THEN_CHARGE" | "NET";
        SettleOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "settle";
            result?: components["schemas"]["SettlementResult"];
            /** @description Present when the settlement failed with `ABANDONED`; the `onAbandoned` record. */
            abandonedSettlement?: components["schemas"]["AbandonedSettlementRecord"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "settle";
        };
        RefundOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "refund" | "refundUnlinked";
            result?: components["schemas"]["RefundResult"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "refund" | "refundUnlinked";
        };
        VoidTransactionOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "voidTransaction";
            result?: components["schemas"]["VoidResult"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "voidTransaction";
        };
        TransactionStatusOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "getTransactionStatus";
            result?: components["schemas"]["TransactionStatusResult"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "getTransactionStatus";
        };
        VoidResultOperation: components["schemas"]["OperationBase"] & {
            /** @enum {string} */
            type: "setStoredValueCard" | "updateDisplay" | "updateInputDisplay" | "end" | "forceEnd";
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "setStoredValueCard" | "updateDisplay" | "updateInputDisplay" | "end" | "forceEnd";
        };
        AddItemMutation: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            op: "ADD_ITEM";
            item: components["schemas"]["BasketItem"];
            /** @description An explicit line id for a new SKU, mirroring `addItem(item, itemId)`. */
            itemId?: string;
        };
        /** @description Addresses a basket line by exactly one of `itemId` or `sku` (the `BySku` variants in Java). */
        BasketLineAddress: {
            /** @description The line to address. */
            itemId: string;
        } | {
            /** @description The line to address by SKU. */
            sku: string;
        };
        RemoveItemMutation: components["schemas"]["BasketLineAddress"] & {
            /** @enum {string} */
            op: "REMOVE_ITEM";
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            op: "REMOVE_ITEM";
        };
        UpdateItemQuantityMutation: components["schemas"]["BasketLineAddress"] & {
            /** @enum {string} */
            op: "UPDATE_ITEM_QUANTITY";
            /** @description Absolute quantity; `0` removes the line. */
            quantity: number;
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            op: "UPDATE_ITEM_QUANTITY";
        };
        SetDiscountsMutation: components["schemas"]["BasketLineAddress"] & {
            /** @enum {string} */
            op: "SET_DISCOUNTS";
            /** @description Replaces the register-applied discounts; an empty list clears them. */
            discounts: components["schemas"]["BasketDiscount"][];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            op: "SET_DISCOUNTS";
        };
        SetTaxRateMutation: components["schemas"]["BasketLineAddress"] & {
            /** @enum {string} */
            op: "SET_TAX_RATE";
            /** @description The tax rate; clears a fixed amount on the line. */
            amount: components["schemas"]["Money"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            op: "SET_TAX_RATE";
        };
        SetTaxAmountMutation: components["schemas"]["BasketLineAddress"] & {
            /** @enum {string} */
            op: "SET_TAX_AMOUNT";
            /** @description The fixed tax amount; clears a rate on the line. */
            amount: components["schemas"]["Money"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            op: "SET_TAX_AMOUNT";
        };
        SetTaxTotalMutation: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            op: "SET_TAX_TOTAL";
            /** @description The basket-level tax override, or `null` to restore item-level computation. */
            amount: components["schemas"]["Money"] | null;
        };
        /**
         * @description Restricts how the terminal captures an identifier or card; `KEYED` for typed entry, `SCANNED` for a barcode.
         * @enum {string}
         */
        ForceEntryMode: "CHECK_READER" | "CONTACTLESS" | "FILE" | "ICC" | "KEYED" | "MAG_STRIPE" | "MANUAL" | "RFID" | "SCANNED" | "SYNCHRONOUS_ICC" | "TAPPED";
        /**
         * @description Without `resolver`, prompts the customer on the terminal (`identifyMember(options)`):
         *     outcomes that leave the checkout without a member (not found, suspended, cancelled) are
         *     successes with the matching `IdentifyStatus`. With `resolver`, a POS-driven lookup with no
         *     terminal prompt (`identifyMember(Member pending)`); account id and phone only — email and
         *     custom identifiers fail with `UNSUPPORTED`. Available after a failed settlement.
         */
        IdentifyMemberRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "identifyMember";
            options?: components["schemas"]["IdentifyOptions"];
            resolver?: components["schemas"]["MemberIdResolver"];
        };
        /** @description Reads card data from the terminal without initiating a payment. */
        AcquireCardRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "acquireCard";
            options?: components["schemas"]["CardAcquisitionOptions"];
        };
        /** @description `requestDigitString` (e.g. a ZIP code), `requestDecimalString` (e.g. a tip; result is `Money`) and `requestTextString` (e.g. an email address). */
        InputPromptRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "requestDigitString" | "requestDecimalString" | "requestTextString";
            prompt: string;
            options?: components["schemas"]["InputOptions"];
        };
        /** @description A yes/no confirmation. */
        ConfirmationRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "requestConfirmation";
            prompt: string;
            options?: components["schemas"]["ConfirmationOptions"];
        };
        MenuEntryRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "requestMenuEntry";
            prompt: string;
            entries: string[];
            options?: components["schemas"]["MenuOptions"];
        };
        /** @description Captures a handwritten signature on the terminal. */
        SignatureRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "requestSignature";
            prompt: string;
        };
        /** @description Asks the customer to confirm an amount. */
        AmountConfirmationRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "requestAmountConfirmation";
            amount: components["schemas"]["Money"];
            prompt: string;
        };
        /** @description `requestPinEntry` captures and encrypts a PIN; `requestPinVerify` verifies it and returns the block; `requestPinVerifyOnly` verifies without returning it. */
        PinRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "requestPinEntry" | "requestPinVerify" | "requestPinVerifyOnly";
            options?: components["schemas"]["PinOptions"];
        };
        /** @description `storedValueBalance` (result `StoredValueBalance`), `storedValueDeactivate` (an unload of zero; provider support varies) and `storedValueDuplicate` (provider support varies). */
        StoredValueCardRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "storedValueBalance" | "storedValueDeactivate" | "storedValueDuplicate";
            card: components["schemas"]["StoredValueCard"];
        };
        /** @description `storedValueActivate` (`amount` is the initial balance, `"0"` to activate without funds), `storedValueLoad`, `storedValueUnload` (cash out) and `storedValueReserve` (provider support varies). */
        StoredValueAmountRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "storedValueActivate" | "storedValueLoad" | "storedValueUnload" | "storedValueReserve";
            card: components["schemas"]["StoredValueCard"];
            amount: components["schemas"]["Money"];
        };
        /** @description Reverses a prior stored value operation by its terminal reference. */
        StoredValueReverseRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "storedValueReverse";
            originalPoiTransactionId: string;
            /** Format: date-time */
            originalPoiTransactionTimestamp?: string;
        };
        /**
         * @description Registers the gift card charged as part of a split tender during `settle`, or clears it
         *     with `card: null`. A setter in Java: applied immediately, never queued, and the response
         *     already carries `succeeded`. Dropped by `clear` on the basket.
         */
        SetStoredValueCardRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "setStoredValueCard";
            card: components["schemas"]["StoredValueCard"] | null;
        };
        /**
         * @description The step kinds this client will answer. A step kind not listed is resolved with its
         *     default immediately and emits no `operation.step`, which is the Java SDK's behaviour when
         *     no handler is registered. Default: none.
         */
        HandledSteps: components["schemas"]["StepKind"][];
        /**
         * @description Starts the settlement orchestration: refund allocations, rebate redemption, point
         *     redemption, stored value line fulfillment, stored value tender, card charge, award (or the
         *     signed difference only, with `settlementType: NET`). Preconditions are checked when the
         *     lane reaches it: the session open with a non-empty, unconsumed basket, and no same-session
         *     void partially reversed. Charge-side failures consult the register through
         *     `RECOVERY_REQUIRED` steps when declared; refund allocation failures are never retried in
         *     the same run and the register retries `settle` with the same committed allocation prefix.
         */
        SettleRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "settle";
            options?: components["schemas"]["SettlementOptions"];
            handledSteps?: components["schemas"]["HandledSteps"];
        };
        /**
         * @description Full (no `amount`) or partial linked refund of this session's completed payment, also
         *     reversing the loyalty award best-effort. After a split tender this references the card
         *     leg; the committed rebate and redemption movements are reversed by `voidTransaction`.
         *     Refused once a void has partially reversed the payment's money legs.
         */
        RefundRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "refund";
            amount?: components["schemas"]["Money"];
            handledSteps?: components["schemas"]["HandledSteps"];
        };
        /** @description An unlinked refund, not tied to a prior transaction; payment only, no loyalty reversal. */
        RefundUnlinkedRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "refundUnlinked";
            amount: components["schemas"]["Money"];
            handledSteps?: components["schemas"]["HandledSteps"];
        };
        /** @description Persistable reference for reversing one fulfilled stored value basket line. */
        StoredValueLoadRecord: {
            basketReference: string;
            amount: components["schemas"]["Money"];
            poiTransactionId: string;
            /** Format: date-time */
            poiTransactionTimestamp?: string;
        };
        /**
         * @description Without `originalSale`, reverses this session's completed payment: every movement it
         *     committed, in `ReversalStep` order; a retried void resumes at the first movement still
         *     standing. With `originalSale`, a whole-transaction void of a prior sale by its persisted
         *     record; retry it on the same session so the in-memory progress prevents re-sending
         *     reversed legs. Not allowed once the payment has been refunded from this session.
         */
        VoidTransactionRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "voidTransaction";
            originalSale?: components["schemas"]["OriginalSaleRecord"];
            handledSteps?: components["schemas"]["HandledSteps"];
        };
        /**
         * @description The Nexo `MessageCategoryType`.
         * @enum {string}
         */
        MessageCategory: "ABORT" | "ADMIN" | "BALANCE_INQUIRY" | "BATCH" | "CARD_ACQUISITION" | "CARD_READER_APDU" | "CARD_READER_INIT" | "CARD_READER_POWER_OFF" | "DIAGNOSIS" | "DISPLAY" | "ENABLE_SERVICE" | "EVENT" | "GET_TOTALS" | "INPUT" | "INPUT_UPDATE" | "LOGIN" | "LOGOUT" | "LOYALTY" | "PAYMENT" | "PIN" | "PRINT" | "RECONCILIATION" | "REVERSAL" | "SOUND" | "STORED_VALUE" | "TRANSACTION_STATUS" | "TRANSMIT";
        /** @description Checks the status of a prior request by the `ServiceID` it was sent with; the terminal repeats the original response when found. */
        TransactionStatusRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "getTransactionStatus";
            /** @description The `serviceId` of the operation to check, as reported on its operation resource, or of a failed settlement step (`SettlementFailure.serviceId`). */
            originalServiceId: string;
            options?: components["schemas"]["TransactionStatusOptions"];
        };
        /** @description Refreshes the customer display from a basket snapshot (rendered by the host's display renderer) or shows a custom payload; exactly one. Failures are this operation's, not `background.error`. */
        UpdateDisplayRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "updateDisplay";
            basket?: components["schemas"]["Basket"];
            display?: components["schemas"]["DisplayPayload"];
        } & ({
            basket: components["schemas"]["Basket"];
        } | {
            display: components["schemas"]["DisplayPayload"];
        });
        /** @description Replaces the display content of the input prompt currently awaiting a response (Nexo `InputUpdate`). Unordered; fails with `INVALID_STATE` when no input is in progress. */
        UpdateInputDisplayRequest: {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "updateInputDisplay";
            display: components["schemas"]["DisplayPayload"];
        };
        EventBase: {
            /** Format: int64 */
            seq: number;
            /** Format: date-time */
            at: string;
            type: string;
        };
        SessionStartedEvent: components["schemas"]["EventBase"] & {
            /** @enum {string} */
            type: "session.started";
            payload: {
                session: components["schemas"]["Session"];
                context: components["schemas"]["SessionContext"];
            };
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "session.started";
        };
        /** @description A line paired across the two snapshots of a change; `after` keeps the same item id. */
        LineChange: {
            before: components["schemas"]["BasketLineItem"];
            after: components["schemas"]["BasketLineItem"];
        };
        BasketChangedEvent: components["schemas"]["EventBase"] & {
            /** @enum {string} */
            type: "basket.changed";
            payload: components["schemas"]["BasketChange"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "basket.changed";
        };
        MemberChangedEvent: components["schemas"]["EventBase"] & {
            /** @enum {string} */
            type: "member.changed";
            payload: {
                member: components["schemas"]["Member"] | null;
            };
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "member.changed";
        };
        ContextChangedEvent: components["schemas"]["EventBase"] & {
            /** @enum {string} */
            type: "context.changed";
            payload: components["schemas"]["SessionContext"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "context.changed";
        };
        OperationStepEvent: components["schemas"]["EventBase"] & {
            /** @enum {string} */
            type: "operation.step";
            payload: components["schemas"]["OperationStep"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "operation.step";
        };
        OperationMovementEvent: components["schemas"]["EventBase"] & {
            /** @enum {string} */
            type: "operation.movement";
            payload: {
                operationId: string;
                movement: components["schemas"]["SettlementMovement"];
            };
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "operation.movement";
        };
        OperationCompletedEvent: components["schemas"]["EventBase"] & {
            /** @enum {string} */
            type: "operation.completed";
            payload: components["schemas"]["Operation"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "operation.completed";
        };
        /** @description The media asset of a rendering; URLs are signed, short-lived CDN links. */
        MediaSpec: {
            type: components["schemas"]["MediaType"];
            /** Format: uri */
            url: string;
            /** @description Running time of a video. */
            duration?: components["schemas"]["Duration"];
            /**
             * Format: uri
             * @description Still frame to show before a video starts.
             */
            poster?: string;
        };
        /** @description A call to action; the `token` is opaque, minted per creative and session, and must be handed back exactly as received. */
        Cta: {
            label: string;
            action: components["schemas"]["Action"];
            token: string;
        };
        WidgetRenderingEvent: components["schemas"]["EventBase"] & {
            /** @enum {string} */
            type: "widget.rendering";
            payload: {
                /** @enum {string} */
                widget: "retail-media";
                placement: string;
                rendering: components["schemas"]["Rendering"];
            };
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "widget.rendering";
        };
        WidgetClearEvent: components["schemas"]["EventBase"] & {
            /** @enum {string} */
            type: "widget.clear";
            payload: {
                /** @enum {string} */
                widget: "retail-media";
                placement: string;
            };
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "widget.clear";
        };
        WidgetOfferEvent: components["schemas"]["EventBase"] & {
            /** @enum {string} */
            type: "widget.offer";
            payload: {
                /** @enum {string} */
                widget: "retail-media";
                offer: components["schemas"]["Offer"];
            };
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "widget.offer";
        };
        WidgetInteractionEvent: components["schemas"]["EventBase"] & {
            /** @enum {string} */
            type: "widget.interaction";
            payload: {
                /** @enum {string} */
                widget: "retail-media";
                interaction: components["schemas"]["AdInteraction"];
            };
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "widget.interaction";
        };
        BackgroundErrorEvent: components["schemas"]["EventBase"] & {
            /** @enum {string} */
            type: "background.error";
            payload: components["schemas"]["SessionError"];
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "background.error";
        };
        SessionEndedEvent: components["schemas"]["EventBase"] & {
            /** @enum {string} */
            type: "session.ended";
            payload: {
                sessionId: string;
                /** @description `true` after `force-end`. */
                forced: boolean;
                /** @description The `force-end` reason, when forced. */
                reason?: string;
            };
        } & {
            /**
             * @description discriminator enum property added by openapi-typescript
             * @enum {string}
             */
            type: "session.ended";
        };
    };
    responses: {
        /** @description The request did not validate. */
        BadRequest: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["SessionError"];
            };
        };
        /** @description No such session, operation, item or widget. */
        NotFound: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["SessionError"];
            };
        };
        /** @description The host has no terminal configured. */
        NoTerminal: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["SessionError"];
            };
        };
        /** @description Refused by a lifecycle guard; the body says which. */
        Conflict: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["SessionError"];
            };
        };
        /** @description The `Idempotency-Key` was already used for a different request (`VALIDATION`). */
        IdempotencyKeyReused: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["SessionError"];
            };
        };
        /** @description The host could not reach the terminal. */
        TerminalUnreachable: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["SessionError"];
            };
        };
        /** @description The host has no terminal configured. */
        "responses-NoTerminal": {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["SessionError"];
            };
        };
        /** @description The host could not reach the terminal. */
        "responses-TerminalUnreachable": {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["SessionError"];
            };
        };
        /** @description The request did not validate. */
        "responses-BadRequest": {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["SessionError"];
            };
        };
        /** @description The `Idempotency-Key` was already used for a different request (`VALIDATION`). */
        "responses-IdempotencyKeyReused": {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["SessionError"];
            };
        };
        /** @description No such session, operation, item or widget. */
        "responses-NotFound": {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["SessionError"];
            };
        };
        /** @description Refused by a lifecycle guard; the body says which. */
        "responses-Conflict": {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["SessionError"];
            };
        };
        /** @description The updated basket snapshot. */
        basketResponse: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["Basket"];
            };
        };
    };
    parameters: {
        /** @description The session's identifier, as `Session.id` reports it. */
        sessionId: string;
        /** @description The operation's identifier, as `Operation.id` reports it. */
        operationId: string;
        /**
         * @description A client-generated key, unique per intended request (a UUID). The host remembers the
         *     key for the life of the session and answers a replay with the original response. The
         *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
         *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
         *     dropped connection and money may have moved.
         */
        idempotencyKey: string;
        /**
         * @description The Nexo `POIID` to send. It does not select a terminal; the host has exactly one and passes
         *     this value through, using its own default when absent.
         */
        poiIdParam: string;
        /**
         * @description The register identifier sent as Nexo `SaleID`. Defaults to the host's configured sale id
         *     for session-less operations.
         */
        saleIdParam: string;
        /** @description Store location identifier used as the `TotalsGroupID` filter. */
        storeLocationParam: string;
        /** @description The widget type, as `WidgetConfig.type` named it; `retail-media` is the only type today. */
        widgetType: "retail-media";
    };
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    getHealth: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The host is up. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    /**
                     * @example {
                     *       "host": "bridge",
                     *       "hostVersion": "0.1.0",
                     *       "sdkVersion": "0.30.0",
                     *       "protocolVersions": [
                     *         "2"
                     *       ],
                     *       "terminal": {
                     *         "label": "Lane 3",
                     *         "model": "VictaLane",
                     *         "reachable": true
                     *       }
                     *     }
                     */
                    "application/json": components["schemas"]["Health"];
                };
            };
        };
    };
    getTerminal: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The configured terminal. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TerminalInfo"];
                };
            };
            404: components["responses"]["responses-NoTerminal"];
        };
    };
    diagnoseTerminal: {
        parameters: {
            query?: {
                /**
                 * @description The Nexo `POIID` to send. It does not select a terminal; the host has exactly one and passes
                 *     this value through, using its own default when absent.
                 */
                poiId?: components["parameters"]["poiIdParam"];
                /**
                 * @description The register identifier sent as Nexo `SaleID`. Defaults to the host's configured sale id
                 *     for session-less operations.
                 */
                saleId?: components["parameters"]["saleIdParam"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The terminal answered. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DiagnosisResult"];
                };
            };
            404: components["responses"]["responses-NoTerminal"];
            503: components["responses"]["responses-TerminalUnreachable"];
        };
    };
    getTerminalTotals: {
        parameters: {
            query?: {
                /**
                 * @description The Nexo `POIID` to send. It does not select a terminal; the host has exactly one and passes
                 *     this value through, using its own default when absent.
                 */
                poiId?: components["parameters"]["poiIdParam"];
                /**
                 * @description The register identifier sent as Nexo `SaleID`. Defaults to the host's configured sale id
                 *     for session-less operations.
                 */
                saleId?: components["parameters"]["saleIdParam"];
                /** @description Store location identifier used as the `TotalsGroupID` filter. */
                storeLocation?: components["parameters"]["storeLocationParam"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The totals. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReconciliationResult"];
                };
            };
            404: components["responses"]["responses-NoTerminal"];
            503: components["responses"]["responses-TerminalUnreachable"];
        };
    };
    reconcileTerminal: {
        parameters: {
            query?: {
                /**
                 * @description The Nexo `POIID` to send. It does not select a terminal; the host has exactly one and passes
                 *     this value through, using its own default when absent.
                 */
                poiId?: components["parameters"]["poiIdParam"];
                /**
                 * @description The register identifier sent as Nexo `SaleID`. Defaults to the host's configured sale id
                 *     for session-less operations.
                 */
                saleId?: components["parameters"]["saleIdParam"];
                /** @description Store location identifier used as the `TotalsGroupID` filter. */
                storeLocation?: components["parameters"]["storeLocationParam"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The reconciliation outcome. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReconciliationResult"];
                };
            };
            404: components["responses"]["responses-NoTerminal"];
            503: components["responses"]["responses-TerminalUnreachable"];
        };
    };
    printOnTerminal: {
        parameters: {
            query?: {
                /**
                 * @description The Nexo `POIID` to send. It does not select a terminal; the host has exactly one and passes
                 *     this value through, using its own default when absent.
                 */
                poiId?: components["parameters"]["poiIdParam"];
                /**
                 * @description The register identifier sent as Nexo `SaleID`. Defaults to the host's configured sale id
                 *     for session-less operations.
                 */
                saleId?: components["parameters"]["saleIdParam"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PrintPayload"];
            };
        };
        responses: {
            /** @description Printed. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            400: components["responses"]["responses-BadRequest"];
            404: components["responses"]["responses-NoTerminal"];
            503: components["responses"]["responses-TerminalUnreachable"];
        };
    };
    terminalSound: {
        parameters: {
            query?: {
                /**
                 * @description The Nexo `POIID` to send. It does not select a terminal; the host has exactly one and passes
                 *     this value through, using its own default when absent.
                 */
                poiId?: components["parameters"]["poiIdParam"];
                /**
                 * @description The register identifier sent as Nexo `SaleID`. Defaults to the host's configured sale id
                 *     for session-less operations.
                 */
                saleId?: components["parameters"]["saleIdParam"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SoundRequest"];
            };
        };
        responses: {
            /** @description Done. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            400: components["responses"]["responses-BadRequest"];
            404: components["responses"]["responses-NoTerminal"];
            503: components["responses"]["responses-TerminalUnreachable"];
        };
    };
    createSession: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateSessionRequest"];
            };
        };
        responses: {
            /** @description The session is open. `eventsUrl` is where to subscribe. */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Session"];
                };
            };
            400: components["responses"]["responses-BadRequest"];
            /**
             * @description `UNSUPPORTED`: `kind: terminal` on a host with no terminal configured, or a
             *     `retail-media` widget requested on a host without an ad decision service.
             */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SessionError"];
                };
            };
            422: components["responses"]["responses-IdempotencyKeyReused"];
            503: components["responses"]["responses-TerminalUnreachable"];
        };
    };
    getSession: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The session. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Session"];
                };
            };
            404: components["responses"]["responses-NotFound"];
        };
    };
    endSession: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The end operation was queued. */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Operation"];
                };
            };
            404: components["responses"]["responses-NotFound"];
            409: components["responses"]["responses-Conflict"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    forceEndSession: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ForceEndRequest"];
            };
        };
        responses: {
            /** @description The force-end operation was queued. */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Operation"];
                };
            };
            400: components["responses"]["responses-BadRequest"];
            404: components["responses"]["responses-NotFound"];
            409: components["responses"]["responses-Conflict"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    abortSession: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The abort was issued (or there was nothing to abort). */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        /** @description The operation the abort targeted; absent when nothing was in flight. */
                        operation?: components["schemas"]["Operation"];
                    };
                };
            };
            404: components["responses"]["responses-NotFound"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    getBasket: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The basket. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Basket"];
                };
            };
            404: components["responses"]["responses-NotFound"];
        };
    };
    replaceBasket: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReplaceBasketRequest"];
            };
        };
        responses: {
            200: components["responses"]["basketResponse"];
            400: components["responses"]["responses-BadRequest"];
            404: components["responses"]["responses-NotFound"];
            409: components["responses"]["responses-Conflict"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    addBasketItem: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AddBasketItemRequest"];
            };
        };
        responses: {
            200: components["responses"]["basketResponse"];
            400: components["responses"]["responses-BadRequest"];
            404: components["responses"]["responses-NotFound"];
            409: components["responses"]["responses-Conflict"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    removeBasketItem: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
                /** @description The session-assigned line id (`BasketLineItem.itemId`). */
                itemId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            200: components["responses"]["basketResponse"];
            404: components["responses"]["responses-NotFound"];
            409: components["responses"]["responses-Conflict"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    updateBasketItem: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
                /** @description The session-assigned line id (`BasketLineItem.itemId`). */
                itemId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BasketItemPatch"];
            };
        };
        responses: {
            200: components["responses"]["basketResponse"];
            400: components["responses"]["responses-BadRequest"];
            404: components["responses"]["responses-NotFound"];
            409: components["responses"]["responses-Conflict"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    mutateBasket: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["BasketMutationsRequest"];
            };
        };
        responses: {
            200: components["responses"]["basketResponse"];
            400: components["responses"]["responses-BadRequest"];
            404: components["responses"]["responses-NotFound"];
            409: components["responses"]["responses-Conflict"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    setBasketTaxTotal: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TaxTotalRequest"];
            };
        };
        responses: {
            200: components["responses"]["basketResponse"];
            400: components["responses"]["responses-BadRequest"];
            404: components["responses"]["responses-NotFound"];
            409: components["responses"]["responses-Conflict"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    clearBasket: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            200: components["responses"]["basketResponse"];
            404: components["responses"]["responses-NotFound"];
            409: components["responses"]["responses-Conflict"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    getMember: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The attached member. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Member"];
                };
            };
            /** @description No member is attached (guest checkout). */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            404: components["responses"]["responses-NotFound"];
        };
    };
    setMember: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MemberInput"];
            };
        };
        responses: {
            /** @description The member as attached (pending until a lookup completes). */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Member"];
                };
            };
            400: components["responses"]["responses-BadRequest"];
            404: components["responses"]["responses-NotFound"];
            409: components["responses"]["responses-Conflict"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    clearMember: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No member is attached any more. */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            404: components["responses"]["responses-NotFound"];
            409: components["responses"]["responses-Conflict"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    getContext: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The context as it is now. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SessionContext"];
                };
            };
            404: components["responses"]["responses-NotFound"];
        };
    };
    updateContext: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SessionContextPatch"];
            };
        };
        responses: {
            /** @description The context after the change. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SessionContext"];
                };
            };
            400: components["responses"]["responses-BadRequest"];
            404: components["responses"]["responses-NotFound"];
            409: components["responses"]["responses-Conflict"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    listOperations: {
        parameters: {
            query?: {
                /** @description Only operations in these statuses. */
                status?: components["schemas"]["OperationStatus"][];
            };
            header?: never;
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The operations. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Operation"][];
                };
            };
            404: components["responses"]["responses-NotFound"];
        };
    };
    startOperation: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["OperationRequest"];
            };
        };
        responses: {
            /** @description The operation was accepted onto the lane. */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Operation"];
                };
            };
            400: components["responses"]["responses-BadRequest"];
            404: components["responses"]["responses-NotFound"];
            409: components["responses"]["responses-Conflict"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    getOperation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
                /** @description The operation's identifier, as `Operation.id` reports it. */
                operationId: components["parameters"]["operationId"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The operation. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Operation"];
                };
            };
            404: components["responses"]["responses-NotFound"];
        };
    };
    replyToStep: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
                /** @description The operation's identifier, as `Operation.id` reports it. */
                operationId: components["parameters"]["operationId"];
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["StepReply"];
            };
        };
        responses: {
            /** @description The reply was taken; the operation continues. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Operation"];
                };
            };
            400: components["responses"]["responses-BadRequest"];
            404: components["responses"]["responses-NotFound"];
            /**
             * @description The step is not pending (answered, expired or unknown: `INVALID_STATE`), or the
             *     `Idempotency-Key` was already used for a different request (`VALIDATION`).
             */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SessionError"];
                };
            };
        };
    };
    abortOperation: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
                /** @description The operation's identifier, as `Operation.id` reports it. */
                operationId: components["parameters"]["operationId"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The operation had already completed; nothing to abort. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Operation"];
                };
            };
            /** @description The abort was issued. */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Operation"];
                };
            };
            404: components["responses"]["responses-NotFound"];
            409: components["responses"]["responses-Conflict"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    listWidgets: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The widgets and their state. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WidgetState"][];
                };
            };
            404: components["responses"]["responses-NotFound"];
        };
    };
    pauseWidget: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
                /** @description The widget type, as `WidgetConfig.type` named it; `retail-media` is the only type today. */
                widgetType: components["parameters"]["widgetType"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The widget is paused. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WidgetState"];
                };
            };
            404: components["responses"]["responses-NotFound"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    resumeWidget: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
                /** @description The widget type, as `WidgetConfig.type` named it; `retail-media` is the only type today. */
                widgetType: components["parameters"]["widgetType"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The widget is showing content again. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WidgetState"];
                };
            };
            404: components["responses"]["responses-NotFound"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    reportRetailMediaAction: {
        parameters: {
            query?: never;
            header: {
                /**
                 * @description A client-generated key, unique per intended request (a UUID). The host remembers the
                 *     key for the life of the session and answers a replay with the original response. The
                 *     same key with a different body is refused with 422 (`VALIDATION`). Required on every
                 *     POST, PUT, PATCH and DELETE under `/v1/sessions`, because a browser may retry after a
                 *     dropped connection and money may have moved.
                 */
                "Idempotency-Key": components["parameters"]["idempotencyKey"];
            };
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["WidgetAction"];
            };
        };
        responses: {
            /** @description Accepted for validation. */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            400: components["responses"]["responses-BadRequest"];
            404: components["responses"]["responses-NotFound"];
            422: components["responses"]["responses-IdempotencyKeyReused"];
        };
    };
    streamEvents: {
        parameters: {
            query?: {
                /** @description Replay events with a `seq` greater than this. */
                since?: number;
            };
            header?: never;
            path: {
                /** @description The session's identifier, as `Session.id` reports it. */
                sessionId: components["parameters"]["sessionId"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Switching to WebSocket; each text frame is one `Event`. */
            101: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The event stream. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/event-stream": components["schemas"]["Event"];
                };
            };
            404: components["responses"]["responses-NotFound"];
            /** @description The `since` position is no longer buffered; resynchronise from the resources. */
            410: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SessionError"];
                };
            };
        };
    };
}
