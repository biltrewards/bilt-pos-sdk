/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.widget;

/**
 * What a {@link Cta} asks the SDK to do when the shopper taps it.
 *
 * <p>Every action is validated by the widget against the ad platform before anything happens, using
 * the CTA's opaque token; the surface only reports the tap. The enum is closed so a surface can
 * decide up front which buttons it is able to draw and a capabilities report can say so.
 */
public enum Action {

  /**
   * Apply the advertised offer to the current checkout. Handled by the widget: it exchanges the
   * token with the ad platform for a validated offer and hands that offer to the register, which
   * turns it into a basket discount or credit. Only the validated offer ever reaches the POS; the
   * creative's copy is never trusted for pricing.
   */
  APPLY_OFFER,

  /**
   * Send the offer or details to the shopper's phone. Handled by the widget through the ad
   * platform; the platform owns the phone number (from the identified member) and the message.
   * Nothing reaches the POS beyond an interaction record.
   */
  SEND_TO_PHONE,

  /**
   * Show more about the product or offer, typically a second screen or a QR code on the same
   * surface. Handled by the surface itself once the widget confirms the token; the POS is told only
   * that details were opened.
   */
  DETAILS,

  /**
   * The shopper closed the creative. Handled by the surface (it clears) and reported to the
   * platform so the same creative is not served again in this session. Nothing reaches the POS.
   */
  DISMISS
}
