/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Internal API — subject to change without notice.
 */
package com.bilt.pos.session.internal;

import com.bilt.pos.nexo.model.MessageCategoryType;
import java.util.function.Supplier;

/**
 * Lets a host assign the {@code ServiceID} of a single-request money operation (an unlinked refund,
 * a stored value change) when it accepts the operation, so the register holds the id before the
 * request is sent.
 */
public interface PresetServiceIds {

  /**
   * Mints an id through the session's {@code ServiceIdProvider}, validation and {@code
   * ServiceIdListener}, as for any request the session sends.
   */
  String mintServiceId(MessageCategoryType category);

  /**
   * Runs {@code create} on this thread with {@code serviceId} preset: the operation {@code create}
   * makes sends its request with exactly that id. A retried {@code refundUnlinked} tender mints a
   * fresh id for each later attempt and reports it through {@code ReversalFlow.beforeStep}.
   * Operations other than {@code refundUnlinked} and the stored value changes ignore it.
   */
  <T> T withPresetServiceId(String serviceId, Supplier<T> create);
}
