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

/**
 * A live {@link AdEventListener} registration; closing it stops delivery.
 *
 * <p>{@link #close()} is idempotent and never throws. After it returns, no further callbacks are
 * started for the listener, though one already in flight on the service's thread may still
 * complete. Closing the session ({@link AdDecisionService#closeSession}) closes every subscription
 * on it.
 */
public interface AdEventSubscription extends AutoCloseable {

  @Override
  void close();
}
