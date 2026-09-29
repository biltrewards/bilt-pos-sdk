/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.media.service;

import com.bilt.pos.media.AdInteraction;
import com.bilt.pos.media.Offer;

/**
 * Receives events the ad platform originates for a session, as opposed to answers to the SDK's own
 * calls.
 *
 * <p>Two things reach the SDK this way: an {@link Offer} the platform issued without a local tap —
 * a hosted page the register cannot script accepted a CTA, or a send-to-phone was confirmed on the
 * shopper's device — and an {@link AdInteraction} that happened on a surface the SDK does not
 * drive. Callbacks arrive on a service-owned thread; implementations return promptly and marshal to
 * the widget themselves.
 */
public interface AdEventListener {

  /** The platform issued an offer for this session outside the local action flow. */
  void onOffer(Offer offer);

  /** An interaction with this session's creatives was observed outside the SDK's renderer. */
  void onInteraction(AdInteraction interaction);
}
