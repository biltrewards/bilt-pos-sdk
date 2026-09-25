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

import com.bilt.pos.session.identity.IdentifyResult;
import com.bilt.pos.session.identity.IdentifyStatus;
import com.bilt.pos.session.identity.Member;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A session's member state: the current {@link Member}, the background resolution of a pending one,
 * and the {@code onMemberChanged} notification. Every way a member reaches the session — the POS
 * setting it, a terminal prompt, a background lookup — lands here, so anything observing the member
 * sees one path.
 *
 * <p>Transitions take the session lock the owner hands in. Notifications are dispatched after the
 * lock is released, fire-and-forget on the callback executor, and a throwing handler is contained.
 *
 * <p>Latest attempt wins: a pending member is resolved on the session's operation lane, and the
 * outcome is applied only if that very pending member is still the session's member when it arrives
 * — a member set in the meantime, resolved or not, is never overwritten by a stale lookup.
 */
final class SessionMember {

  private static final Logger LOGGER = Logger.getLogger(SessionMember.class.getName());

  private final ReentrantLock lock;
  private final SessionOperations operations;
  private final MemberResolver resolver;
  private final BooleanSupplier ended;
  private final Consumer<Member> onMemberChanged;
  private final Consumer<Member> observers;
  private volatile Member member;

  /**
   * {@code seed} is the builder's pre-seeded member, or null; it is installed silently, as initial
   * state rather than a change. A pending seed is resolved once the owner calls {@link
   * #resolveSeed()}. {@code observers} is the session's internal fan-out to its observers, told of
   * every change before the register's {@code onMemberChanged} handler is dispatched.
   */
  SessionMember(
      ReentrantLock lock,
      SessionOperations operations,
      MemberResolver resolver,
      BooleanSupplier ended,
      Consumer<Member> onMemberChanged,
      Consumer<Member> observers,
      Member seed) {
    this.lock = lock;
    this.operations = operations;
    this.resolver = Objects.requireNonNull(resolver, "resolver");
    this.ended = ended;
    this.onMemberChanged = onMemberChanged;
    this.observers = Objects.requireNonNull(observers, "observers");
    this.member = seed;
  }

  /** The current member, resolved or pending; null when none is attached. */
  Member current() {
    return member;
  }

  /**
   * The current member as an identification result, for the settlement path; null unless resolved.
   */
  IdentifyResult identified() {
    Member current = member;
    return current == null || !current.isResolved()
        ? null
        : IdentifyResult.found(
            current.memberId(), current.loyaltyBrand(), current.rewards(), current.pointBalance());
  }

  /**
   * The public setter: installs {@code next} (null clears), announces the change, and starts the
   * lookup when {@code next} is pending.
   */
  void set(Member next) {
    boolean changed;
    lock.lock();
    try {
      changed = install(next);
    } finally {
      lock.unlock();
    }
    if (changed) {
      fireChanged(next);
    }
    if (next != null && !next.isResolved()) {
      scheduleResolution(next);
    }
  }

  /** Starts the lookup of a pending seed; a no-op for no seed or a resolved one. */
  void resolveSeed() {
    Member seed = member;
    if (seed != null && !seed.isResolved()) {
      scheduleResolution(seed);
    }
  }

  /**
   * Applies an identification outcome, for the terminal prompt path. The latest completed attempt
   * wins: {@code FOUND} attaches the member; {@code NOT_FOUND} and {@code SUSPENDED} are
   * affirmative "no usable member" outcomes and detach any previously attached member (so a
   * re-identify cannot leave loyalty running against a stale account); {@code CANCELLED} only means
   * the customer dismissed this prompt — a prior member, resolved or pending, stands.
   *
   * <p>Must be called with the lock held; returns whether the member changed, in which case the
   * caller announces it with {@link #fireChanged(Member)} once it has released the lock.
   */
  boolean applyIdentification(IdentifyResult result) {
    if (result.getStatus() == IdentifyStatus.FOUND) {
      return install(Member.resolved(result));
    }
    if (result.getStatus() == IdentifyStatus.CANCELLED) {
      return false;
    }
    return install(null);
  }

  /**
   * Replaces the member under the lock. Always installs the given instance, even one equal to the
   * current member, so a re-attached pending member is the instance its lookup then checks for;
   * reports whether the value changed.
   */
  private boolean install(Member next) {
    Member previous = member;
    member = next;
    return !Objects.equals(previous, next);
  }

  /**
   * Announces a change: to the session's observers first, then {@code onMemberChanged} on the
   * callback executor, never throwing into the caller.
   */
  void fireChanged(Member now) {
    observers.accept(now);
    if (onMemberChanged == null) {
      return;
    }
    HandlerDispatch.fireAndForget(
        operations.callback(),
        "onMemberChanged",
        () -> {
          try {
            onMemberChanged.accept(now);
          } catch (RuntimeException handlerFailure) {
            LOGGER.log(Level.SEVERE, "the onMemberChanged handler threw", handlerFailure);
          }
        });
  }

  private void scheduleResolution(Member pending) {
    try {
      operations.executor().execute(() -> resolve(pending));
    } catch (RejectedExecutionException e) {
      operations.backgroundError(
          "resolving " + pending,
          AbstractShopperSession.invalidState(
              "the session has ended; the member cannot be resolved"));
    }
  }

  private void resolve(Member pending) {
    // superseded before the lookup began — nothing to look up
    if (member != pending) {
      return;
    }
    Member outcome;
    try {
      outcome = resolver.resolve(pending);
    } catch (RuntimeException e) {
      operations.backgroundError("resolving " + pending, e);
      return;
    }
    boolean changed;
    lock.lock();
    try {
      if (member != pending) {
        return;
      }
      if (ended.getAsBoolean()) {
        operations.backgroundError(
            "resolving " + pending,
            AbstractShopperSession.invalidState(
                "the session ended while the member was being resolved; the outcome was discarded"));
        return;
      }
      if (outcome != null && !outcome.isResolved()) {
        // nothing learned; the member stays pending
        return;
      }
      changed = install(outcome);
    } finally {
      lock.unlock();
    }
    if (changed) {
      fireChanged(outcome);
    }
  }
}
