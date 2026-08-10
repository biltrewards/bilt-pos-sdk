package com.bilt.pos.session;

import com.bilt.pos.session.payment.CheckoutResult;
import com.bilt.pos.session.payment.GiftCardPaymentResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The flows' asynchronous terminal-method contract — the same shape as
 * {@code SessionResult}'s, covered there in depth; these tests pin the
 * parity: execute() runs on the session executor, handlers deliver on the
 * callback executor with the flow thread awaiting each answer (directly on
 * the flow thread without one), executeSync() stays inline, onComplete
 * always fires (rejection included), get() awaits an in-flight execute.
 */
class FlowAsyncExecutionTest {

    private final SessionOperations operations = new SessionOperations();
    private final ExecutorService callbackExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "cb-thread"));

    @AfterEach
    void shutDownExecutors() {
        operations.shutdown();
        callbackExecutor.shutdownNow();
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(5, TimeUnit.SECONDS), "timed out waiting for dispatch");
    }

    // ─── PaymentFlow ───

    @Test
    void paymentExecuteRunsAsyncWithHandlersOnTheOperationThread() throws Exception {
        CheckoutResult result = CheckoutResult.builder().build();
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<String> handlerThread = new AtomicReference<>();

        new PaymentFlow(flow -> result)
                .session(operations)
                .onSuccess(r -> handlerThread.set(Thread.currentThread().getName()))
                .onComplete(complete::countDown)
                .execute();

        await(complete);
        assertTrue(handlerThread.get().startsWith("bilt-session-"),
                "delivered on " + handlerThread.get());
    }

    @Test
    void paymentHandlersDeliverOnTheCallbackExecutorAndSteerTheFlow() throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<String> stepThread = new AtomicReference<>();
        AtomicReference<String> successThread = new AtomicReference<>();
        AtomicReference<BigDecimal> steered = new AtomicReference<>();

        // the executor function plays the orchestrator: it consults the
        // marshalled gift card step handler mid-flow and records the total
        // the register answered with
        PaymentFlow flow = new PaymentFlow(f -> {
            steered.set(f.giftCardHandler().apply(new GiftCardPaymentResult(
                    new BigDecimal("5.00"), BigDecimal.ZERO,
                    new BigDecimal("12.00"), new BigDecimal("7.00"))));
            return CheckoutResult.builder().build();
        });
        flow.session(operations)
                .callbackOn(callbackExecutor)
                .onGiftCardPayment(giftCard -> {
                    stepThread.set(Thread.currentThread().getName());
                    return giftCard.getSuggestedTotal();
                })
                .onSuccess(r -> successThread.set(Thread.currentThread().getName()))
                .onComplete(complete::countDown)
                .execute();

        await(complete);
        assertEquals("cb-thread", stepThread.get(), "step handlers marshal to the callback executor");
        assertEquals("cb-thread", successThread.get());
        assertEquals(new BigDecimal("7.00"), steered.get(),
                "the awaited answer steers the flow");
    }

    @Test
    void paymentExecuteWithoutExecutorPointsToExecuteSync() {
        PaymentFlow flow = new PaymentFlow(f -> CheckoutResult.builder().build());
        IllegalStateException e = assertThrows(IllegalStateException.class, flow::execute);
        assertTrue(e.getMessage().contains("executeSync"));
    }

    @Test
    void paymentRejectionAfterShutdownFailsThroughTheHandlers() throws Exception {
        operations.shutdown();
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<SessionError> received = new AtomicReference<>();

        PaymentFlow flow = new PaymentFlow(f -> CheckoutResult.builder().build())
                .session(operations)
                .onError(error -> {
                    received.set(error);
                    return null;
                })
                .onComplete(complete::countDown);
        flow.execute();

        await(complete);
        assertEquals(SessionErrorCode.INVALID_STATE, received.get().getCode());
        assertNull(flow.getOrNull());
    }

    @Test
    void paymentStepHandlersMayInvokeNestedSessionOperations() throws Exception {
        // a mid-payment prompt from a step handler is legitimate: the
        // handler is awaited by the payment thread, so the nested blocking
        // operation runs inline on the callback thread instead of
        // deadlocking behind the parked payment
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<String> prompted = new AtomicReference<>();

        PaymentFlow flow = new PaymentFlow(f -> {
            f.giftCardHandler().apply(new GiftCardPaymentResult(
                    BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE));
            return CheckoutResult.builder().build();
        });
        flow.session(operations)
                .callbackOn(callbackExecutor)
                .onGiftCardPayment(giftCard -> {
                    prompted.set(new SessionResult<>("prompt", () -> "confirmed")
                            .session(operations)
                            .get());
                    return giftCard.getSuggestedTotal();
                })
                .onComplete(complete::countDown)
                .execute();

        await(complete);
        assertEquals("confirmed", prompted.get());
    }

    @Test
    void paymentAccessorsSurfaceAsyncHandlerFailures() {
        // same guarantee as SessionResult: get() must not report a clean
        // success while a throwing onSuccess is still being recorded
        IllegalStateException handlerBug = new IllegalStateException("handler bug");
        PaymentFlow flow = new PaymentFlow(f -> CheckoutResult.builder().build())
                .session(operations)
                .onSuccess(r -> {
                    throw handlerBug;
                });
        flow.execute();

        assertSame(handlerBug, assertThrows(IllegalStateException.class, flow::get));
    }

    @Test
    void paymentGetAwaitsAnInFlightExecute() throws Exception {
        CheckoutResult result = CheckoutResult.builder().build();
        CountDownLatch bodyMayFinish = new CountDownLatch(1);
        PaymentFlow flow = new PaymentFlow(f -> {
            try {
                bodyMayFinish.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return result;
        }).session(operations);
        flow.execute();

        Thread releaser = new Thread(bodyMayFinish::countDown);
        releaser.start();
        assertSame(result, flow.get());
        releaser.join();
    }

    // ─── ReversalFlow ───

    @Test
    void reversalExecuteRunsAsyncAndCompletes() throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<String> handlerThread = new AtomicReference<>();

        new ReversalFlow<String>(flow -> "reversed")
                .session(operations)
                .onSuccess(r -> handlerThread.set(Thread.currentThread().getName()))
                .onComplete(complete::countDown)
                .execute();

        await(complete);
        assertTrue(handlerThread.get().startsWith("bilt-session-"),
                "delivered on " + handlerThread.get());
    }

    @Test
    void reversalRejectionDeliversANullStepError() throws Exception {
        operations.shutdown();
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<ReversalStep> step = new AtomicReference<>(ReversalStep.CARD);
        AtomicReference<SessionError> received = new AtomicReference<>();

        ReversalFlow<String> flow = new ReversalFlow<String>(f -> "reversed")
                .session(operations)
                .onError((s, error) -> {
                    step.set(s);
                    received.set(error);
                    return null;
                })
                .onComplete(complete::countDown);
        flow.execute();

        await(complete);
        assertNull(step.get(), "nothing ran, so the failure carries no step");
        assertEquals(SessionErrorCode.INVALID_STATE, received.get().getCode());
    }

    @Test
    void reversalStepDecisionsDeliverOnTheCallbackExecutor() throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<String> decisionThread = new AtomicReference<>();
        AtomicReference<ReversalDecision> decided = new AtomicReference<>();

        // the executor function plays the reversal manager: it consults the
        // marshalled step decider mid-flow
        ReversalFlow<String> flow = new ReversalFlow<>(f -> {
            decided.set(f.decider().decide(ReversalStep.CARD,
                    new SessionError(SessionErrorCode.DECLINED, "declined")));
            return "reversed";
        });
        flow.session(operations)
                .callbackOn(callbackExecutor)
                .onError((step, error) -> {
                    decisionThread.set(Thread.currentThread().getName());
                    return ReversalDecision.SKIP;
                })
                .onComplete(complete::countDown)
                .execute();

        await(complete);
        assertEquals("cb-thread", decisionThread.get());
        assertEquals(ReversalDecision.SKIP, decided.get(), "the awaited decision steers the flow");
    }

    @Test
    void reversalExecuteWithoutExecutorPointsToExecuteSync() {
        ReversalFlow<String> flow = new ReversalFlow<>(f -> "reversed");
        IllegalStateException e = assertThrows(IllegalStateException.class, flow::execute);
        assertTrue(e.getMessage().contains("executeSync"));
    }
}
