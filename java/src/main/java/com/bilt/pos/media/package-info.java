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
 * <p>{@link com.bilt.pos.media.RetailMedia} is the widget itself. Registered on a session builder,
 * it follows the session's basket, member and context, decides through an {@link
 * com.bilt.pos.media.service.AdDecisionService} what to show on the surfaces the register bound to
 * its placements, and hands the register validated offers ({@code onOffer}) and an informational
 * interaction stream ({@code onInteraction}).
 *
 * <p>The service layer works from its own {@code AdSessionSnapshot} rather than the session types,
 * so the ad platform integration can evolve independently of the session API; the widget is the one
 * place that translates between the two.
 */
package com.bilt.pos.media;
