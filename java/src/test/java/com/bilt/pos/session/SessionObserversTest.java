package com.bilt.pos.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bilt.pos.session.identity.Member;
import com.bilt.pos.widget.SessionObserver;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SessionObserversTest {

  @Test
  void nothingIsDeliveredAfterEnded() throws Exception {
    SessionOperations operations = new SessionOperations(null);
    SessionObservers observers = new SessionObservers(operations);
    List<String> heard = new CopyOnWriteArrayList<>();
    CountDownLatch done = new CountDownLatch(1);
    observers.add(
        new SessionObserver() {
          @Override
          public void memberChanged(Member member) {
            heard.add("memberChanged");
          }

          @Override
          public void ended() {
            heard.add("ended");
          }
        });

    observers.memberChanged(Member.id("mbr_1"));
    observers.ended(done::countDown);
    // a member notification drained after the session began to end
    observers.memberChanged(Member.id("mbr_2"));

    assertEquals(true, done.await(5, TimeUnit.SECONDS));
    operations.shutdown();
    assertEquals(Arrays.asList("memberChanged", "ended"), heard);
  }
}
