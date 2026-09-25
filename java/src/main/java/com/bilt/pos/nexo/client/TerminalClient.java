/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.nexo.client;

import com.bilt.pos.nexo.model.NexoTerminalAPI;
import java.time.Duration;

/**
 * The transport a session uses to reach a Bilt terminal: one Nexo Sale to POI request in, one
 * response out.
 *
 * <p>This is the whole seam between the session layer ({@code com.bilt.pos.session}) and the wire.
 * Everything a session sends — the session bracket, loyalty, prompts, payments, display updates,
 * aborts — goes through {@link #request(NexoTerminalAPI)} or its timeout-overriding twin, and the
 * session never asks the transport for anything else. The Nexo {@code MessageHeader} already
 * carries {@code POIID} and {@code SaleID} on every message, so a transport is not bound to a
 * session or a terminal: the same instance can serve many sessions addressing many devices, and an
 * implementation needs no per-session configuration.
 *
 * <p>{@link BiltNexoTerminalClient} is the local-network implementation, speaking HTTPS to the
 * device's own endpoint. A cloud-relayed implementation, reaching the terminal through Bilt's
 * backend instead of the store LAN, is planned; registers that only need the session API should
 * program against this interface so the transport can be swapped without touching checkout code.
 *
 * <p><strong>Threading.</strong> Implementations must be safe to call from more than one thread at
 * a time. A session issues its ordered operations from a single operation thread, but {@code
 * abort()} and {@code updateInputDisplay()} are deliberately unordered — they exist to overlap the
 * request currently blocking that thread — so an implementation must tolerate two concurrent
 * requests in flight, one of them referencing the other by {@code ServiceID}. Each call blocks
 * until the terminal answers or the timeout elapses.
 */
public interface TerminalClient {

  /**
   * Sends a Sale to POI request and returns the terminal's response envelope, or {@code null} when
   * the terminal replies with an empty body (as it does for abort requests). The request envelope
   * must carry a {@code SaleToPOIRequest}. A {@link BiltNexoClientException} reports any transport
   * failure; a transport timeout should surface with a {@link java.net.SocketTimeoutException} or
   * {@link java.io.InterruptedIOException} in its cause chain so the session can classify it as
   * such.
   */
  NexoTerminalAPI request(NexoTerminalAPI request) throws BiltNexoClientException;

  /**
   * Like {@link #request(NexoTerminalAPI)} with a per-request read timeout overriding the
   * transport's default; {@code null} means the default. Sessions use this for operations that
   * legitimately wait on the customer, such as prompts and card presentment.
   */
  NexoTerminalAPI request(NexoTerminalAPI request, Duration timeout) throws BiltNexoClientException;
}
