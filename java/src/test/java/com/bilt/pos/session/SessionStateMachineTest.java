package com.bilt.pos.session;

import com.bilt.pos.session.internal.SessionStateMachine;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static com.bilt.pos.session.SessionState.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionStateMachineTest {

    @Test
    void startsIdle() {
        assertEquals(IDLE, new SessionStateMachine().current());
    }

    @Test
    void happyPathCheckout() {
        SessionStateMachine machine = new SessionStateMachine();
        machine.transitionTo(IDENTIFIED);
        machine.transitionTo(ACTIVE);
        machine.transitionTo(PAYING);
        machine.transitionTo(COMPLETED);
        assertEquals(COMPLETED, machine.current());
    }

    @Test
    void failedPaymentCanRetryOrVoid() {
        SessionStateMachine machine = new SessionStateMachine();
        machine.transitionTo(ACTIVE);
        machine.transitionTo(PAYING);
        machine.transitionTo(FAILED);
        assertTrue(machine.canTransitionTo(PAYING));
        assertTrue(machine.canTransitionTo(VOIDING));
        machine.transitionTo(VOIDING);
        machine.transitionTo(VOIDED);
        assertEquals(VOIDED, machine.current());
    }

    @Test
    void emptyingBasketReturnsToIdleOrIdentified() {
        SessionStateMachine machine = new SessionStateMachine();
        machine.transitionTo(ACTIVE);
        assertTrue(machine.canTransitionTo(IDLE));
        assertTrue(machine.canTransitionTo(IDENTIFIED));
    }

    @Test
    void completedAllowsVoidAndEndOnly() {
        SessionStateMachine machine = new SessionStateMachine();
        machine.transitionTo(ACTIVE);
        machine.transitionTo(PAYING);
        machine.transitionTo(COMPLETED);
        for (SessionState target : SessionState.values()) {
            assertEquals(target == VOIDING || target == ENDED,
                    machine.canTransitionTo(target), "COMPLETED -> " + target);
        }
    }

    @Test
    void settledStatesAllowOnlyEnd() {
        SessionStateMachine aborted = new SessionStateMachine();
        aborted.transitionTo(ABORTED);
        for (SessionState target : SessionState.values()) {
            assertEquals(target == ENDED, aborted.canTransitionTo(target),
                    "ABORTED -> " + target);
        }
    }

    @Test
    void endedAllowsNothing() {
        SessionStateMachine ended = new SessionStateMachine();
        ended.transitionTo(ENDED);
        for (SessionState target : SessionState.values()) {
            assertFalse(ended.canTransitionTo(target), "ENDED -> " + target);
        }
    }

    @Test
    void moneyInFlightStatesRefuseEnd() {
        SessionStateMachine paying = new SessionStateMachine();
        paying.transitionTo(ACTIVE);
        paying.transitionTo(PAYING);
        assertFalse(paying.canTransitionTo(ENDED), "PAYING -> ENDED");

        SessionStateMachine voiding = new SessionStateMachine();
        voiding.transitionTo(VOIDING);
        assertFalse(voiding.canTransitionTo(ENDED), "VOIDING -> ENDED");
    }

    @Test
    void illegalTransitionThrows() {
        SessionStateMachine machine = new SessionStateMachine();
        assertThrows(IllegalStateException.class, () -> machine.transitionTo(COMPLETED));
        assertEquals(IDLE, machine.current());
    }

    @Test
    void voidableFromIdleWithPriorTransactionReference() {
        SessionStateMachine machine = new SessionStateMachine();
        machine.transitionTo(VOIDING);
        machine.transitionTo(VOIDED);
        assertEquals(VOIDED, machine.current());
    }

    @Test
    void requireStateReturnsNullWhenAllowed() {
        SessionStateMachine machine = new SessionStateMachine();
        assertNull(machine.requireState(EnumSet.of(IDLE, ACTIVE), "op"));
    }

    @Test
    void requireStateReturnsInvalidStateError() {
        SessionStateMachine machine = new SessionStateMachine();
        SessionError error = machine.requireState(EnumSet.of(PAYING), "pay");
        assertNotNull(error);
        assertEquals(SessionErrorCode.INVALID_STATE, error.getCode());
        assertTrue(error.getMessage().contains("pay"));
        assertTrue(error.getMessage().contains("IDLE"));
    }

    @Test
    void isTerminalMatchesTable() {
        assertTrue(COMPLETED.isTerminal());
        assertTrue(ABORTED.isTerminal());
        assertTrue(VOIDED.isTerminal());
        assertTrue(ENDED.isTerminal());
        assertFalse(IDLE.isTerminal());
        assertFalse(PAYING.isTerminal());
        assertFalse(FAILED.isTerminal());
        assertFalse(VOIDING.isTerminal());
    }
}
