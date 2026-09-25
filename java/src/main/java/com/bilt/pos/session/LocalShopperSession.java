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

/**
 * A shopper session with no terminal: the basket, member state, and lifecycle of {@link
 * AbstractShopperSession} and nothing else. Its only lifecycle rule is that an ended session stays
 * ended.
 */
final class LocalShopperSession extends AbstractShopperSession {

  private volatile boolean ended;

  LocalShopperSession(ShopperSession.Builder builder) {
    super(
        builder.saleId,
        builder.currency,
        builder.storeLocation,
        builder.callbackExecutor,
        builder.onBackgroundError);
  }

  @Override
  void requireBasketMutable() {
    if (ended) {
      throw new IllegalStateException("the basket cannot be modified after end()");
    }
  }

  @Override
  void requireBasketClearable() {
    if (ended) {
      throw new IllegalStateException("the basket cannot be cleared after end()");
    }
  }

  @Override
  boolean ended() {
    return ended;
  }

  @Override
  public SessionResult<Void> end() {
    return operation(
        "end",
        () -> {
          lock.lock();
          try {
            if (ended) {
              throw invalidState("the session has already ended; create a new session");
            }
            ended = true;
          } finally {
            lock.unlock();
          }
          // no further operations may run; asynchronous submissions after
          // this fail into their handlers instead of queueing forever
          operations.shutdown();
          return null;
        });
  }
}
