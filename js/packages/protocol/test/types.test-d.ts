import { describe, expectTypeOf, it } from 'vitest';
import type {
  Basket,
  BasketChange,
  Money,
  Operation,
  OperationOf,
  OperationRequestOf,
  OperationResult,
  OperationStepOf,
  SessionEvent,
  SessionEventMap,
  SessionEventPayload,
  SessionEventType,
  SettlementRecovery,
  SettlementResult,
  StepReply,
} from '../src/index';

describe('@bilt/pos-protocol types', () => {
  it('derives event payloads from the Event union', () => {
    expectTypeOf<SessionEventPayload<'basket.changed'>>().toEqualTypeOf<BasketChange>();
    expectTypeOf<SessionEventMap['widget.offer']['offer']['scope']>().toEqualTypeOf<
      'BASKET' | 'LINE_ITEM'
    >();
    expectTypeOf<SessionEventType>().toMatchTypeOf<string>();
    expectTypeOf<
      Extract<SessionEvent, { type: 'session.ended' }>['payload']['forced']
    >().toEqualTypeOf<boolean>();
  });

  it('types operation results per operation type', () => {
    expectTypeOf<OperationResult<'settle'>>().toEqualTypeOf<SettlementResult>();
    expectTypeOf<OperationResult<'requestConfirmation'>>().toEqualTypeOf<boolean>();
    expectTypeOf<OperationResult<'requestDecimalString'>>().toEqualTypeOf<Money>();
    expectTypeOf<OperationResult<'storedValueLoad'>['transactionType']>().toEqualTypeOf<
      'ACTIVATE' | 'DUPLICATE' | 'LOAD' | 'RESERVE' | 'REVERSE' | 'UNLOAD' | undefined
    >();
    expectTypeOf<OperationResult<'end'>>().toEqualTypeOf<undefined>();
    expectTypeOf<OperationOf<'settle'>['abandonedSettlement']>().not.toBeNever();
  });

  it('keeps operation requests discriminated by type', () => {
    expectTypeOf<OperationRequestOf<'settle'>['options']>().not.toBeNever();
    expectTypeOf<OperationRequestOf<'requestMenuEntry'>['entries']>().toEqualTypeOf<string[]>();
    expectTypeOf<OperationRequestOf<'storedValueLoad'>['amount']>().toEqualTypeOf<Money>();
  });

  it('types steps by kind', () => {
    expectTypeOf<OperationStepOf<'TOTAL_REQUIRED'>['step']>().toEqualTypeOf<
      'REBATE_REDEMPTION' | 'POINT_REDEMPTION' | 'STORED_VALUE_CHARGE'
    >();
    expectTypeOf<
      OperationStepOf<'RECOVERY_REQUIRED'>['failure']['amountDue']
    >().toEqualTypeOf<Money>();
    expectTypeOf<StepReply['recovery']>().toEqualTypeOf<SettlementRecovery | undefined>();
  });

  it('uses decimal strings for money', () => {
    expectTypeOf<Basket['grandTotal']>().toEqualTypeOf<string>();
    expectTypeOf<Operation['status']>().toEqualTypeOf<
      'queued' | 'running' | 'awaitingReply' | 'succeeded' | 'failed' | 'aborted'
    >();
  });
});
