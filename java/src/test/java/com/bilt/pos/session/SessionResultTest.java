package com.bilt.pos.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class SessionResultTest {

    private static final SessionError ERROR =
            new SessionError(SessionErrorCode.DECLINED, "declined");

    private final ExecutorService operationExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "op-thread"));
    private final ExecutorService callbackExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "cb-thread"));

    @AfterEach
    void shutDownExecutors() {
        operationExecutor.shutdownNow();
        callbackExecutor.shutdownNow();
    }

    private static <T> SessionResult<T> result(Supplier<T> body) {
        return new SessionResult<>("testOp", body);
    }

    private <T> SessionResult<T> attached(Supplier<T> body) {
        return result(body).executors(operationExecutor, null);
    }

    private static <T> SessionResult<T> failing() {
        return result(() -> {
            throw new SessionException(ERROR);
        });
    }

    /** Fails the test if [latch] does not open within five seconds. */
    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(5, TimeUnit.SECONDS), "timed out waiting for dispatch");
    }

    @Test
    void unexpectedExceptionIsNotMaskedAsSuccess() {
        SessionResult<String> result = result(() -> {
            throw new IllegalStateException("boom");
        });

        IllegalStateException first =
                assertThrows(IllegalStateException.class, result::executeSync);

        // later accessors must rethrow the failure, never report success
        assertSame(first, assertThrows(IllegalStateException.class, result::get));
        assertSame(first, assertThrows(IllegalStateException.class, result::getOrNull));
        assertSame(first, assertThrows(IllegalStateException.class, result::isSuccess));
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
        result.executeSync();
        assertEquals(1, invocations.get());
    }

    @Test
    void executeDispatchesSuccessHandler() {
        AtomicReference<String> received = new AtomicReference<>();
        List<String> errors = new ArrayList<>();
        result(() -> "hello")
                .onSuccess(received::set)
                .onError(e -> errors.add(e.getMessage()))
                .executeSync();

        assertEquals("hello", received.get());
        assertTrue(errors.isEmpty());
    }

    @Test
    void executeDispatchesErrorHandler() {
        AtomicReference<SessionError> received = new AtomicReference<>();
        SessionResult<String> result = SessionResultTest.<String>failing()
                .onSuccess(v -> fail("success handler must not run"))
                .onError(received::set);
        result.executeSync();

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
        result.executeSync();
        assertEquals("ok", result.get());
    }

    @Test
    void doubleExecuteThrows() {
        SessionResult<String> result = result(() -> "ok");
        result.executeSync();
        assertThrows(IllegalStateException.class, result::executeSync);
    }

    @Test
    void registeringHandlerAfterExecutionThrows() {
        SessionResult<String> result = result(() -> "ok");
        result.executeSync();
        assertThrows(IllegalStateException.class, () -> result.onSuccess(v -> { }));
        assertThrows(IllegalStateException.class, () -> result.onError(e -> { }));
    }

    @Test
    void errorHandlerIsOptionalOnFailure() {
        SessionResult<String> result = failing();
        assertDoesNotThrow(result::executeSync);
        assertNull(result.getOrNull());
    }

    // ─── Asynchronous execution ───

    @Test
    void executeWithoutSessionExecutorPointsToExecuteSync() {
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> result(() -> "ok").execute());
        assertTrue(e.getMessage().contains("executeSync"));
    }

    @Test
    void executeRunsOnTheOperationExecutorAndReturnsImmediately() throws Exception {
        CountDownLatch bodyMayFinish = new CountDownLatch(1);
        AtomicReference<String> bodyThread = new AtomicReference<>();
        SessionResult<String> result = attached(() -> {
            bodyThread.set(Thread.currentThread().getName());
            try {
                bodyMayFinish.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "ok";
        });

        result.execute();          // must not block on the held-open body
        bodyMayFinish.countDown();

        assertEquals("ok", result.get());
        assertEquals("op-thread", bodyThread.get());
    }

    @Test
    void handlersDeliverOnTheCallbackExecutor() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<String> handlerThread = new AtomicReference<>();
        result(() -> "ok")
                .executors(operationExecutor, callbackExecutor)
                .onSuccess(v -> {
                    handlerThread.set(Thread.currentThread().getName());
                    delivered.countDown();
                })
                .execute();

        await(delivered);
        assertEquals("cb-thread", handlerThread.get());
    }

    @Test
    void callbackOnOverridesTheSessionDefault() throws Exception {
        ExecutorService override = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "override-thread"));
        try {
            CountDownLatch delivered = new CountDownLatch(1);
            AtomicReference<String> handlerThread = new AtomicReference<>();
            result(() -> "ok")
                    .executors(operationExecutor, callbackExecutor)
                    .callbackOn(override)
                    .onSuccess(v -> {
                        handlerThread.set(Thread.currentThread().getName());
                        delivered.countDown();
                    })
                    .execute();

            await(delivered);
            assertEquals("override-thread", handlerThread.get());
        } finally {
            override.shutdownNow();
        }
    }

    @Test
    void onCompleteFiresOnSuccessAndError() throws Exception {
        CountDownLatch successComplete = new CountDownLatch(1);
        attached(() -> "ok").onComplete(successComplete::countDown).execute();
        await(successComplete);

        CountDownLatch errorComplete = new CountDownLatch(1);
        SessionResultTest.<String>failing()
                .executors(operationExecutor, null)
                .onComplete(errorComplete::countDown)
                .execute();
        await(errorComplete);
    }

    @Test
    void onCompleteFiresOnUnexpectedExceptionAndStaysLoud() throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        IllegalStateException boom = new IllegalStateException("boom");
        SessionResult<String> result = this.<String>attached(() -> {
            throw boom;
        }).onComplete(complete::countDown);

        result.execute();

        await(complete);
        assertSame(boom, assertThrows(IllegalStateException.class, result::get));
    }

    @Test
    void onCompleteFiresWhenTheSuccessHandlerThrows() throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        SessionResult<String> result = attached(() -> "ok")
                .onSuccess(v -> {
                    throw new IllegalStateException("handler bug");
                })
                .onComplete(complete::countDown);

        result.execute();

        await(complete);
    }

    @Test
    void rejectionAfterShutdownFailsThroughTheHandlers() throws Exception {
        operationExecutor.shutdown();
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<SessionError> received = new AtomicReference<>();
        SessionResult<String> result = attached(() -> "ok")
                .onSuccess(v -> fail("success handler must not run"))
                .onError(received::set)
                .onComplete(complete::countDown);

        result.execute();

        await(complete);
        assertEquals(SessionErrorCode.INVALID_STATE, received.get().getCode());
        assertThrows(SessionException.class, result::get);
    }

    @Test
    void getAwaitsAnInFlightExecute() throws Exception {
        CountDownLatch bodyMayFinish = new CountDownLatch(1);
        SessionResult<String> result = attached(() -> {
            try {
                bodyMayFinish.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "ok";
        });
        result.execute();

        Thread releaser = new Thread(bodyMayFinish::countDown);
        releaser.start();
        assertEquals("ok", result.get());
        releaser.join();
    }

    @Test
    void registeringOnCompleteOrCallbackOnAfterStartThrows() {
        SessionResult<String> result = result(() -> "ok");
        result.executeSync();
        assertThrows(IllegalStateException.class, () -> result.onComplete(() -> { }));
        assertThrows(IllegalStateException.class,
                () -> result.callbackOn(Runnable::run));
    }

    @Test
    void onCompleteFiresOnTheSyncPathsToo() {
        AtomicInteger completions = new AtomicInteger();
        SessionResult<String> result = result(() -> "ok")
                .onComplete(completions::incrementAndGet);
        result.executeSync();
        assertEquals(1, completions.get());

        AtomicInteger failureCompletions = new AtomicInteger();
        SessionResult<String> failing = SessionResultTest.<String>failing()
                .onComplete(failureCompletions::incrementAndGet);
        assertNull(failing.getOrNull());
        assertEquals(1, failureCompletions.get());
    }
}
