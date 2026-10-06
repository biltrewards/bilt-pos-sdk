/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
/**
 * The Session Host: the SDK's {@link com.bilt.pos.session.ShopperSession} and {@link
 * com.bilt.pos.session.TerminalShopperSession} served over HTTP, Server-Sent Events and WebSocket
 * so a browser can drive a checkout.
 *
 * <p>This is a library, not an application. The Terminal Bridge embeds it on loopback, the Cloud
 * Session Service behind Bilt auth, and tests on an ephemeral port. {@link
 * com.bilt.pos.host.SessionHost} is the entry point; the embedding application supplies a {@link
 * com.bilt.pos.host.TerminalClientProvider} and, optionally, a {@link com.bilt.pos.host.HostAuth}
 * policy, a {@link com.bilt.pos.host.SessionFactory} and {@link com.bilt.pos.host.StepDeadlines}.
 * Everything under {@code com.bilt.pos.host.internal} is implementation detail with no
 * compatibility promise.
 */
package com.bilt.pos.host;
