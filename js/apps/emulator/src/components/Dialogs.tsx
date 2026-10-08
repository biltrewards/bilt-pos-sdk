import type { ReactNode } from 'react';
import { useController, useEmulatorState } from '../emulator/context';
import { RECOVERY_ACTIONS, type PaymentRecoveryPrompt } from '../emulator/state';
import { Dialog } from './common';

/**
 * A settlement step failed and the host waits for the cashier: the desktop's
 * `PaymentRecoveryDialog`, not dismissible, one button per choice the failure allows.
 */
function PaymentRecoveryDialog({ prompt }: { prompt: PaymentRecoveryPrompt }): ReactNode {
  return (
    <Dialog title="Payment step failed" titleClass="error">
      <pre className="message">{prompt.message}</pre>
      <p>Choose how to continue:</p>
      {prompt.actions.map((action) => (
        <button key={action} type="button" className="choice" onClick={() => prompt.choose(action)}>
          <span>{RECOVERY_ACTIONS[action].label}</span>
          <span className="small">{RECOVERY_ACTIONS[action].description}</span>
        </button>
      ))}
    </Dialog>
  );
}

/** The recovery prompt and the outcome popup, over everything else. */
export function Dialogs(): ReactNode {
  const controller = useController();
  const state = useEmulatorState();
  const outcome = state.paymentOutcome;
  return (
    <>
      {outcome ? (
        <Dialog
          title={outcome.title}
          titleClass={outcome.success ? 'success' : 'error'}
          onDismiss={() => controller.dismissPaymentOutcome()}
          actions={
            <button
              type="button"
              className="text"
              onClick={() => controller.dismissPaymentOutcome()}
            >
              OK
            </button>
          }
        >
          <pre className="message">{outcome.message}</pre>
          {outcome.receipt ? (
            <>
              <hr />
              <pre className="receipt" aria-label="Receipt">
                {outcome.receipt}
              </pre>
            </>
          ) : null}
        </Dialog>
      ) : null}
      {state.paymentRecovery ? <PaymentRecoveryDialog prompt={state.paymentRecovery} /> : null}
    </>
  );
}
