/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import java.util.Objects;

/** Context handed to the {@link ReversalFlow#beforeStep} handler before each reversal leg. */
public final class ReversalContext {

  private final ReversalStep step;
  private final String serviceId;

  public ReversalContext(ReversalStep step, String serviceId) {
    this.step = Objects.requireNonNull(step, "step");
    this.serviceId = Objects.requireNonNull(serviceId, "serviceId");
  }

  /** The leg about to be sent. */
  public ReversalStep getStep() {
    return step;
  }

  /**
   * The Nexo {@code ServiceID} this leg's terminal request will be sent with — minted for this
   * attempt before the handler runs, so the register can record it before anything is sent and
   * resolve the request with Transaction Status after a crash. Each retry of a leg mints a new id
   * and calls the handler again.
   */
  public String getServiceId() {
    return serviceId;
  }
}
