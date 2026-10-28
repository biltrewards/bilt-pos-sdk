---
---

# Terminal Bridge — Recovery

Settlement runs on the bridge, not in the page, so what can be recovered depends on which side went away. The bridge keeps everything in memory: sessions, operations, their results and the event buffer. Nothing is written to disk, so anything that has to outlive a bridge restart has to be kept by the register.

**Keep on the register side**, before and during each settlement:

- the lane's `saleId` and the session `id` (for example in `sessionStorage`). Keep the `saleId` with every `ServiceID` you store: a status lookup from a session with another `saleId` has to name it as `originalSaleId`;
- the `OriginalSaleRecord` of every successful sale, built when `settle()` resolves (below). A later session needs it to void the sale;
- `failure.serviceId` from a `RECOVERY_REQUIRED` step (`SettlementFailure`), and the `abandonedSettlement` record after an `ABANDON`.
- the `ServiceID` of every money request: `context.serviceId` from each settlement `beforeStep` and each refund/void `beforeStep`. These run before the request is sent, and again with a new id on a retry.
- `op.serviceId` of `refundUnlinked` and stored value changes. The bridge assigns it when it accepts the operation, but it starts the operation before the page receives that response. If the bridge crashes in between, the terminal may already have the request while the page never learned its id. That request can only be found by hand (step 4 of [The bridge stopped or crashed](#the-bridge-stopped-or-crashed)).

The record is the result's per-leg POI references plus the member. The JavaScript SDK has no helper for this (Java has `OriginalSaleRecord.from(result, memberId)`), so copy the fields:

```ts
const result = await session.settle();
const record: OriginalSaleRecord = {
  cardPoiTransactionId: result.poiTransactionId,
  cardPoiTransactionTimestamp: result.poiTransactionTimestamp,
  storedValuePoiTransactionId: result.storedValuePoiTransactionId,
  storedValuePoiTransactionTimestamp: result.storedValuePoiTransactionTimestamp,
  rebatePoiTransactionId: result.rebatePoiTransactionId,
  rebatePoiTransactionTimestamp: result.rebatePoiTransactionTimestamp,
  redemptionPoiTransactionId: result.redemptionPoiTransactionId,
  redemptionPoiTransactionTimestamp: result.redemptionPoiTransactionTimestamp,
  awardPoiTransactionId: result.awardPoiTransactionId,
  awardPoiTransactionTimestamp: result.awardPoiTransactionTimestamp,
  memberId: session.member.current?.memberId,
};
persist(record);
```

A sale that loaded a gift card (a `STORED_VALUE_LOAD` movement) also needs `storedValueLoads`: one `{ basketReference, amount, poiTransactionId, poiTransactionTimestamp }` per loaded basket line. The result does not carry the basket reference, so build these from your basket line and the matching movement.

## The page reloaded or the register crashed

The bridge is still running, and so are the session and any operation in flight:

- The operation finishes on its own. A step the page declared in `handledSteps` takes its default when its deadline passes: 30 s for totals and before-step, 120 s for recovery and reversal decisions. The default for a recovery decision is `ABORT`, which unwinds what the settlement had committed.
- The new page can reattach to the old session with the JavaScript SDK (below), or read its state over HTTP. Read requests need no `Idempotency-Key`:

  ```bash
  curl -s http://127.0.0.1:48333/v1/sessions/<id>                          # basket, member, context, ended?
  curl -s http://127.0.0.1:48333/v1/sessions/<id>/operations                # every operation, newest first; the settle you want is the first `type: settle`
  curl -s "http://127.0.0.1:48333/v1/sessions/<id>/operations?status=running,awaitingReply"  # only what is still in flight
  curl -s http://127.0.0.1:48333/v1/sessions/<id>/operations/<operationId> # result, error, abandonedSettlement
  ```

  From the page, `attachTerminalSession` gives the new page a handle on the old session. Its basket, member and context are rebuilt from the session's event stream, and its operations come back as live `Operation` handles:

  ```ts
  import { BiltPos } from '@biltorg/pos-sdk';
  import { localBridge } from '@biltorg/pos-sdk/bridge';

  const pos = await BiltPos.connect(localBridge());
  const session = await pos.attachTerminalSession(sessionId); // { widgets } if this page has widget surfaces

  const operations = await session.operations();           // every operation, newest first
  const settle = operations.find((operation) => operation.type === 'settle');
  const inFlight = await session.operations({ status: ['running', 'awaitingReply'] });
  settle?.guide(settlementHandlers);                        // answer the steps of a settle still in flight
  const result = settle && (await settle);                 // finished: settles at once; running: when the bridge finishes it
  ```

  Handlers go on each adopted operation with `guide()`, not on the attach call: a pending step of an adopted operation stays open until `guide()` registers the handlers that answer it (`SettlementHandlers` for a settle, `ReversalHandlers` for a refund or void), or until its deadline gives it the default. The handle rejects with the `SessionError` for a `failed` or `aborted` settle. An already-ended session still attaches, with `state` `ended`.

  A `succeeded` settle carries its `SettlementResult` in `result`; store it as if the page had received it. A `failed` or `aborted` one carries the `SessionError` in `error`, and `abandonedSettlement` when recovery was abandoned. Ended sessions stay readable for a while (the last 100), and the event stream replays from `?since=<seq>` for 10 minutes or 1000 events.
- Then end the old session so the lane is free, as in [A stuck lane](#a-stuck-lane-invalid_state).

## The bridge stopped or crashed

Quitting from the menu, Ctrl-C and a normal OS shutdown all run the bridge's shutdown: every open session gets a best-effort `end()`. That `end()` is refused, and only logged, while money is unresolved. A crash or a killed process skips even that. Either way, after a restart the old session ids answer `404`, and every operation record and buffered event is gone.

The terminal is not affected. The next terminal session started with the same `saleId` and `poiId` counts as an implicit end of the old one on the terminal (see [Session start and end](./session-start-end.md#terminal-behavior)). To settle what happened to money that was moving when the bridge went down:

1. Start a terminal session. The status request references the original by its `ServiceID` and `SaleID`; the `SaleID` is this session's `saleId` unless you pass `originalSaleId`. So keep the lane's `saleId` next to each `ServiceID`, and pass it when the new session uses a different one (another lane, a per-session `saleId`, another device).
2. If you have the `ServiceID` of the uncertain request, ask the terminal for it:

   ```ts
   const status = await session.getTransactionStatus(serviceId, { originalSaleId: 'LANE-3' });
   // originalSaleId can be left out when this session has the original's saleId
   // to reprint: { receiptReprint: true }; for a non-payment original: { originalCategory: 'LOYALTY' } etc.
   if (!status.found) {
     // the terminal has no transaction under that ServiceID: it was never processed
   } else {
     // status.messageCategory is Payment, Loyalty, StoredValue or Reversal, and the matching
     // paymentResponse / loyaltyResponse / storedValueResponse / reversalResponse repeats the original
   }
   ```

   Inside a running settlement the SDK already does this itself when an exchange ends without an outcome: `outcomeCertainty: INDETERMINATE` means the status check could not settle it either. [Verify payment status](./verify-transaction-status.md) has the Nexo semantics, including when *not found* is trustworthy.
3. If the sale completed, `session.voidTransaction(record)` with its persisted `OriginalSaleRecord` reverses it from the new session.
4. If you have neither a `ServiceID` nor a result, use `pos.terminal().totals()` (or `curl -X POST http://127.0.0.1:48333/v1/terminal/totals`) and the terminal's and acquirer's reports to find the transaction, and reconcile it by hand.

The bridge keeps no record of these ids across a restart, so they are only available if the register stored them when `beforeStep` or the operation handed them out. A request with no stored `ServiceID` and no result ends in step 4.

## A stuck lane (`INVALID_STATE`)

An old session the register did not end stays open on the bridge. It does not block a new session by itself: the terminal treats the new start as the implicit end of the old one on its side. But while the old session's payment is still in flight on the terminal, the new start fails with `TERMINAL_ERROR` (`Admin request failed: Busy`), and the old session keeps its unresolved money until someone finishes it. Reattach to it ([above](#the-page-reloaded-or-the-register-crashed)) or end it:

```bash
curl -X DELETE http://127.0.0.1:48333/v1/sessions/<id> -H "Idempotency-Key: $(uuidgen)"
```

`DELETE` is a normal `end()`, and it answers `409` while money is unresolved: a settlement mid-flight, an incomplete rollback, a partially completed void, or committed refund allocations. Finish the money first:

- stop a running settlement with `POST /v1/sessions/<id>/abort`, which unwinds it at the next step boundary;
- answer a pending step through `POST .../operations/<operationId>/reply`, or wait for its default;
- finish an incomplete rollback of a failed settlement with `voidTransaction()` in the same session;
- retry a partially failed void in the same session (`voidTransaction()`, or `voidTransaction(record)` with the same record for a prior sale);
- retry `settle()` with the same refund allocations when a failed settlement had already committed some.

If none of that can complete, record the incident and the references for reconciliation, then force-end it:

```bash
curl -X POST http://127.0.0.1:48333/v1/sessions/<id>/force-end -H "Idempotency-Key: $(uuidgen)" \
  -H 'Content-Type: application/json' -d '{"reason":"operator escalated incomplete recovery"}'
```

From the page, on the session `attachTerminalSession` returned:

```ts
await session.abort();                                     // stop the running settlement (or inFlight[0].abort())
await session.end();                                       // rejects with a SessionError while money is unresolved
await session.forceEnd('operator escalated incomplete recovery'); // only when the money cannot be finished
```

`force-end` needs a non-blank `reason`. It seals the session with the recovery still unresolved. Restarting the bridge clears a stuck lane too, with the losses described [above](#the-bridge-stopped-or-crashed).

---

## Next steps

- [Terminal Bridge setup guide](./terminal-bridge.md): install, configure and troubleshoot the bridge.
- [JavaScript & React SDK](./javascript-sdk-integration.md): the page side, including recovery decisions for a failed settlement step.
- [Verify payment status](./verify-transaction-status.md): the Nexo semantics behind `getTransactionStatus`.
