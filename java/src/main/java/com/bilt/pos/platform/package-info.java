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
 * Access to the Bilt platform: credentials, environments and an authenticated HTTP client.
 *
 * <p>Everything else in the SDK talks to a Bilt terminal on the store network. This package is the
 * first place the SDK talks to Bilt cloud. It holds the contracts that every cloud-backed feature
 * shares rather than any of those features themselves:
 *
 * <ul>
 *   <li>{@link com.bilt.pos.platform.BiltCredentials} identifies the integrator to the platform.
 *       Today that is an OAuth 2.0 client-credentials pair; the type leaves room for other grants.
 *   <li>{@link com.bilt.pos.platform.BiltEnvironment} selects production or staging, or a custom
 *       pair of endpoints for tests and the emulator, following the {@link
 *       com.bilt.pos.nexo.client.BiltTerminalEnvironment} pattern.
 *   <li>{@link com.bilt.pos.platform.BiltPlatformClient} executes {@link
 *       com.bilt.pos.platform.PlatformRequest}s against the environment's API gateway and returns
 *       {@link com.bilt.pos.platform.PlatformResponse}s. {@link
 *       com.bilt.pos.platform.OkHttpPlatformClient} is the shipped implementation: it acquires,
 *       caches and refreshes the access token so that no caller ever handles one.
 * </ul>
 *
 * <p>What will build on it: member resolution for POS-provided identifiers, registration of a
 * shopper session with the platform, ad decision requests and event reporting for the retail media
 * widget, and the server-to-SDK event channel that carries offer acceptances and interactions back
 * from hosted surfaces. Those endpoints are defined by the platform integration spec, not here;
 * this package only fixes how the SDK authenticates and moves bytes.
 *
 * <p>Wiring the credentials and environment into the session builders is deliberately not part of
 * this package. It arrives with the shopper session and widget work.
 */
package com.bilt.pos.platform;
