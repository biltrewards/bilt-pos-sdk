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
 * Retail-media vocabulary shared by the widget, the ad decision service and the register.
 *
 * <p>These are the nouns of the retail-media flow that are not about drawing: a {@link
 * com.bilt.pos.media.Placement} names where an ad may go, an {@link com.bilt.pos.media.Offer} is
 * the validated price action the platform hands the register when a shopper accepts, an {@link
 * com.bilt.pos.media.AdInteraction} is one thing the shopper did with a creative, and {@link
 * com.bilt.pos.media.Capabilities} tells the platform what this SDK build can render so it never
 * serves what cannot be shown. The drawing side — {@code Rendering}, {@code Surface}, {@code
 * ActionSink} — lives in {@code com.bilt.pos.widget}; the service contract that produces renderings
 * and validates actions lives in {@link com.bilt.pos.media.service}.
 *
 * <p>Nothing here depends on the checkout session types. The service layer works from its own
 * {@code AdSessionSnapshot} so the ad platform integration can evolve independently of the session
 * API.
 */
package com.bilt.pos.media;
