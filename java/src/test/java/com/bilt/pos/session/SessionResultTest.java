package com.bilt.pos.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class SessionResultTest {

    private static final SessionError ERROR =
            new SessionError(SessionErrorCode.DECLINED, "declined");

    private static <T> SessionResult<T> result(Supplier<T> body) {
        return new SessionResult<>("testOp", body);
    }

    private static <T> SessionResult<T> failing() {
        return result(() -> {
            throw new SessionException(ERROR);
        });
    }

    @Test
    void nothingRunsUntilExecute() {
        AtomicInteger invocations = new AtomicInteger();
        SessionResult<String> result = result(() -> {
            invocations.incrementAndGet();
            return "ok";
        });
        result.onSuccess(v -> { });
        result.onError(e -> { });

        assertEquals(0, invocations.get());
        result.execute();
        assertEquals(1, invocations.get());
    }

    @Test
    void executeDispatchesSuccessHandler() {
        AtomicReference<String> received = new AtomicReference<>();
        List<String> errors = new ArrayList<>();
        result(() -> "hello")
                .onSuccess(received::set)
                .onError(e -> errors.add(e.getMessage()))
                .execute();

        assertEquals("hello", received.get());
        assertTrue(errors.isEmpty());
    }

    @Test
    void executeDispatchesErrorHandler() {
        AtomicReference<SessionError> received = new AtomicReference<>();
        SessionResult<String> result = SessionResultTest.<String>failing()
                .onSuccess(v -> fail("success handler must not run"))
                .onError(received::set);
        result.execute();

        assertSame(ERROR, received.get());
    }

    @Test
    void getReturnsValueAndCachesAcrossCalls() {
        AtomicInteger invocations = new AtomicInteger();
        SessionResult<String> result = result(() -> "v" + invocations.incrementAndGet());

        assertEquals("v1", result.get());
        assertEquals("v1", result.get());
        assertEquals(1, invocations.get());
    }

    @Test
    void getThrowsSessionExceptionOnFailure() {
        SessionException e = assertThrows(SessionException.class, () -> failing().get());
        assertSame(ERROR, e.getError());
    }

    @Test
    void getOrNullReturnsNullOnFailure() {
        assertNull(failing().getOrNull());
        assertEquals("ok", result(() -> "ok").getOrNull());
    }

    @Test
    void isSuccessRunsTheOperation() {
        AtomicInteger invocations = new AtomicInteger();
        SessionResult<String> result = result(() -> {
            invocations.incrementAndGet();
            return "ok";
        });

        assertTrue(result.isSuccess());
        assertEquals(1, invocations.get());
        assertFalse(failing().isSuccess());
    }

    @Test
    void getAfterExecuteReturnsCachedOutcome() {
        SessionResult<String> result = result(() -> "ok");
        result.execute();
        assertEquals("ok", result.get());
    }

    @Test
    void doubleExecuteThrows() {
        SessionResult<String> result = result(() -> "ok");
        result.execute();
        assertThrows(IllegalStateException.class, result::execute);
    }

    @Test
    void registeringHandlerAfterExecutionThrows() {
        SessionResult<String> result = result(() -> "ok");
        result.execute();
        assertThrows(IllegalStateException.class, () -> result.onSuccess(v -> { }));
        assertThrows(IllegalStateException.class, () -> result.onError(e -> { }));
    }

    @Test
    void errorHandlerIsOptionalOnFailure() {
        SessionResult<String> result = failing();
        assertDoesNotThrow(result::execute);
        assertNull(result.getOrNull());
    }
}
