/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.media;

import com.bilt.pos.media.service.ActionOutcome;
import com.bilt.pos.media.service.SessionHandle;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.SessionErrorCode;
import com.bilt.pos.widget.Action;
import com.bilt.pos.widget.ActionSink;
import com.bilt.pos.widget.Cta;
import com.bilt.pos.widget.Rendering;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@link ActionSink} a {@link RetailMedia} hands a surface together with one rendering. It is
 * the register-facing half of the action contract: a tap becomes an {@code onOffer} only after the
 * token is one of this rendering's own, the rendering is still the one on display, and the ad
 * platform accepted it; everything else is dropped and reported through the session's {@code
 * onBackgroundError}, never thrown back to the surface.
 *
 * <p>Local checks run on the caller's thread, since a surface may call from a UI or JavaScript
 * thread; the platform round-trip and the register callbacks are marshalled to the widget thread
 * and the session's callback executor respectively. Measurement reporting of these events to the
 * platform is the engine's job and arrives with RET-6776.
 */
final class RetailMediaActionSink implements ActionSink {

  private final RetailMedia widget;
  private final Placement placement;
  private final Rendering rendering;
  private final AtomicBoolean viewed = new AtomicBoolean();

  RetailMediaActionSink(RetailMedia widget, Placement placement, Rendering rendering) {
    this.widget = widget;
    this.placement = placement;
    this.rendering = rendering;
  }

  @Override
  public void perform(Cta cta) {
    if (cta == null) {
      reject("the surface reported a null call to action");
      return;
    }
    Cta issued = rendering.ctaForToken(cta.getToken());
    if (issued == null) {
      reject("token '" + cta.getToken() + "' was not issued for creative " + creativeId());
      return;
    }
    if (issued.getAction() != cta.getAction()) {
      reject("token was issued for " + issued.getAction() + ", not " + cta.getAction());
      return;
    }
    if (!isCurrent()) {
      reject("creative " + creativeId() + " is no longer showing");
      return;
    }
    widget.deliverInteraction(interaction(AdInteraction.Kind.TAPPED, issued.getAction()));
    widget.onWidgetThread(() -> validate(issued));
  }

  private void validate(Cta cta) {
    SessionHandle handle = widget.handle();
    if (handle == null) {
      reject("the session is not registered with the ad platform");
      return;
    }
    ActionOutcome outcome;
    try {
      outcome = widget.adService().validateAction(handle, cta, creativeId());
    } catch (RuntimeException e) {
      widget.report("validating a " + cta.getAction() + " tap on " + placement.getId(), e);
      return;
    }
    if (!outcome.isAccepted()) {
      reject(
          SessionErrorCode.DECLINED,
          "the ad platform refused " + cta.getAction() + ": " + outcome.getReason());
      return;
    }
    if (cta.getAction() == Action.APPLY_OFFER) {
      Offer offer = outcome.getOffer().orElse(null);
      if (offer == null) {
        reject(SessionErrorCode.DECLINED, "the ad platform accepted APPLY_OFFER without an offer");
        return;
      }
      widget.deliverOffer(offer);
    }
    widget.deliverInteraction(interaction(AdInteraction.Kind.CTA_ACCEPTED, cta.getAction()));
    if (cta.getAction() == Action.DISMISS) {
      widget.clear(placement, rendering);
    }
  }

  @Override
  public void viewed(Rendering shown) {
    if (isThis(shown) && isCurrent() && viewed.compareAndSet(false, true)) {
      widget.deliverInteraction(interaction(AdInteraction.Kind.VIEWED, null));
    }
  }

  @Override
  public void dismissed(Rendering shown) {
    if (isThis(shown) && isCurrent()) {
      widget.deliverInteraction(interaction(AdInteraction.Kind.DISMISSED, null));
      widget.clear(placement, rendering);
    }
  }

  @Override
  public void completed(Rendering shown) {
    if (isThis(shown) && isCurrent()) {
      widget.deliverInteraction(interaction(AdInteraction.Kind.COMPLETED, null));
    }
  }

  private boolean isThis(Rendering shown) {
    return rendering.equals(shown);
  }

  private boolean isCurrent() {
    return widget.current(placement) == rendering;
  }

  private String creativeId() {
    return rendering.getCreativeId();
  }

  private AdInteraction interaction(AdInteraction.Kind kind, Action action) {
    return AdInteraction.builder()
        .creativeId(creativeId())
        .placement(placement)
        .kind(kind)
        .action(action)
        .build();
  }

  private void reject(String reason) {
    reject(SessionErrorCode.INVALID_STATE, reason);
  }

  private void reject(SessionErrorCode code, String reason) {
    widget.report(
        new SessionError(
            code, "RetailMedia ignored an action on " + placement.getId() + ": " + reason));
  }
}
