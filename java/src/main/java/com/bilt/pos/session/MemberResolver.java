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

import com.bilt.pos.session.identity.Member;

/**
 * Turns a {@link Member} pending resolution into a resolved one; each session implementation binds
 * its own (the terminal session looks the identifier up on the terminal, the local session has
 * nothing to look it up with yet).
 *
 * <p>Synchronous on purpose: {@link SessionMember} runs it on the session's operation lane, which
 * is what makes resolution ordered against everything else the session does — a {@code settle()}
 * executed after {@code member(pending)} runs after the lookup, so the member it charges against is
 * the resolved one — and the terminal lookup it wraps is itself a blocking Nexo exchange. A {@code
 * CompletionStage} would let a remote resolver leave the lane, but then that ordering would have to
 * be rebuilt by hand at the join.
 *
 * <p>Outcomes: a resolved member attaches; {@code null} means the identifier affirmatively matched
 * nobody and clears the member; an unresolved member (typically {@code pending} itself) means
 * nothing was learned and the member stays pending; a thrown {@link SessionException} (or any
 * runtime exception) is reported through the session's {@code onBackgroundError} and leaves the
 * member pending.
 */
interface MemberResolver {

  /** The placeholder for sessions with nothing to resolve against: the member stays pending. */
  MemberResolver NONE = pending -> pending;

  Member resolve(Member pending);
}
