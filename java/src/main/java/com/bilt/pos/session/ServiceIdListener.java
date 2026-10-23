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

import com.bilt.pos.nexo.model.MessageCategoryType;

/**
 * Observes every {@code ServiceID} the SDK assigns, whether minted by the default generator or a
 * {@link ServiceIdProvider}. Register one through {@code serviceIdListener(...)} on {@link
 * TerminalShopperSession.Builder} or {@link Terminal.Builder}.
 *
 * <p>Called synchronously right after the id is minted and before the request carrying it is sent.
 * If the listener throws, that request is not sent and fails with a {@link SessionException}. Like
 * the provider, it can be called from different threads concurrently and must be thread-safe.
 */
@FunctionalInterface
public interface ServiceIdListener {

  /**
   * An id was assigned to a request about to be sent.
   *
   * @param serviceId the {@code ServiceID} the request will carry
   * @param category the request's Nexo message category
   */
  void serviceIdAssigned(String serviceId, MessageCategoryType category);
}
