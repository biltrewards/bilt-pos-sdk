package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.session.identity.IdentifyResult;
import com.bilt.pos.session.identity.Member;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;

class SessionMemberTest {
  @Test
  void enrichmentArrivingAfterSessionEndIsDiscarded() throws Exception {
    AtomicBoolean ended = new AtomicBoolean();
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    List<Member> changes = new CopyOnWriteArrayList<>();
    List<SessionError> errors = new CopyOnWriteArrayList<>();
    SessionOperations operations = new SessionOperations(Runnable::run, errors::add);
    Member attached = Member.id("member");
    SessionMember state =
        new SessionMember(
            new ReentrantLock(),
            operations,
            MemberResolver.NONE,
            member -> {
              started.countDown();
              try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
              } catch (InterruptedException e) {
                throw new RuntimeException(e);
              }
              return Member.resolved(IdentifyResult.found("member", "Brand", List.of(), 42));
            },
            ended::get,
            changes::add,
            member -> {},
            null);
    try {
      state.set(attached, () -> {});
      assertTrue(started.await(5, TimeUnit.SECONDS));
      ended.set(true);
      release.countDown();
      operations.callOrdered(() -> null);
      assertSame(attached, state.current());
      assertEquals(List.of(attached), changes);
      assertEquals(1, errors.size());
      assertEquals(SessionErrorCode.INVALID_STATE, errors.get(0).getCode());
    } finally {
      release.countDown();
      operations.shutdown();
    }
  }
}
