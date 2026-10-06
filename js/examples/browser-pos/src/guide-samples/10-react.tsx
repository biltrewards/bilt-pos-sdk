// Samples for "React".
import {
  BiltPosProvider,
  RetailMediaSurface,
  useBasket,
  useMember,
  useOperation,
  useSessionEvent,
  useSettlement,
  useTerminalSession,
} from '@bilt/pos-react';
import { BridgeGate, InstallBridgePrompt, useBridge } from '@bilt/pos-react/bridge';
import { BiltPos } from '@bilt/pos-sdk';
import type {
  Basket,
  IdentifyResult,
  Money,
  Offer,
  Operation,
  SettlementFailure,
  SettlementRecoveryAction,
  TerminalShopperSession,
} from '@bilt/pos-sdk';
import { localBridge } from '@bilt/pos-sdk/bridge';
import { useState, type ReactNode } from 'react';

declare function applyOffer(offer: Offer): void;
declare function retax(basket: Basket): Money;
declare function phoneFromInput(): string;
declare function RecoveryPrompt(props: {
  failure: SettlementFailure;
  deadline: Date;
  onChoose(recovery: SettlementRecoveryAction): void;
  onDefault(): void;
}): ReactNode;

const GUIDE_URL = 'https://biltrewards.github.io/bilt-pos-sdk/terminal-bridge.html';

export function Register(): ReactNode {
  return (
    <BridgeGate downloadUrl={GUIDE_URL}>
      <BiltPosProvider connect={() => BiltPos.connect(localBridge())}>
        <Lane />
      </BiltPosProvider>
    </BridgeGate>
  );
}

function Lane(): ReactNode {
  const { session, status, error, restart } = useTerminalSession({
    saleId: 'LANE-3',
    poiId: 'VictaLane-275839164',
    currency: 'USD',
    storeLocation: 'STR-0142',
    widgets: [{ type: 'retail-media', placements: ['lane-banner'] }],
  });
  const { basket, addItem } = useBasket(session);
  const { member, set: signIn } = useMember(session);
  const settlement = useSettlement(session, { interactive: ['RECOVERY_REQUIRED'] });
  useSessionEvent(session, 'widget.offer', ({ offer }) => applyOffer(offer));

  if (status === 'error') {
    return (
      <p>
        Could not start the lane: {error?.message} <button onClick={restart}>Retry</button>
      </p>
    );
  }
  if (!session) return <p>Starting the lane…</p>;

  const step = settlement.pendingStep;
  return (
    <>
      <RetailMediaSurface session={session} placement="lane-banner" />
      <button
        onClick={() =>
          addItem({ sku: 'GRC-OJ-1L', description: 'Orange Juice 1L', unitPrice: '4.49' })
        }
      >
        Scan juice
      </button>
      <p>
        {member?.resolved ? `Member ${member.memberId}` : 'Guest'} · total {basket?.grandTotal}
      </p>
      <button onClick={() => signIn({ resolver: { type: 'PHONE', value: phoneFromInput() } })}>
        Sign in
      </button>
      {step?.kind === 'RECOVERY_REQUIRED' ? (
        <RecoveryPrompt
          failure={step.failure}
          deadline={step.deadline}
          onChoose={(recovery) => settlement.reply({ recovery })}
          onDefault={step.useDefault}
        />
      ) : (
        <button
          disabled={settlement.status !== 'idle'}
          onClick={() => settlement.settle({ onRebatesRedeemed: (r) => retax(r.updatedBasket) })}
        >
          Pay
        </button>
      )}
      {settlement.status === 'succeeded' && (
        <pre>{settlement.result?.customerReceipt?.plainText}</pre>
      )}
      {settlement.status === 'failed' && <p>{settlement.error?.message}</p>}
    </>
  );
}

export function IdentifyButton({ session }: { session: TerminalShopperSession }): ReactNode {
  const [op, setOp] = useState<Operation<IdentifyResult> | null>(null);
  const identify = useOperation(op);
  return (
    <button onClick={() => setOp(session.identifyMember())} disabled={identify.pending}>
      {identify.pending ? `${identify.status}…` : 'Identify on terminal'}
    </button>
  );
}

export function ExplainedGate(): ReactNode {
  // autoDetect: false shows the loopback-permission note first; Continue runs the first probe.
  const bridge = useBridge({ autoDetect: false, pollIntervalMs: 2000 });
  if (bridge.status !== 'ready')
    return <InstallBridgePrompt bridge={bridge} downloadUrl={GUIDE_URL} />;
  return (
    <BiltPosProvider connect={() => BiltPos.connect(localBridge())}>
      <Lane />
    </BiltPosProvider>
  );
}
