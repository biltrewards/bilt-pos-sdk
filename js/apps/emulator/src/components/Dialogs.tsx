import { Fragment, type ReactNode } from 'react';
import { useController, useEmulatorState } from '../emulator/context';
import {
  RECOVERY_ACTIONS,
  partialApprovalChoice,
  type PartialApprovalPrompt,
  type PaymentRecoveryPrompt,
} from '../emulator/state';
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

/**
 * A card approved less than it was asked for and the host waits for the cashier: not
 * dismissible, one button per choice, each with a one-line description.
 */
function PartialApprovalDialog({ prompt }: { prompt: PartialApprovalPrompt }): ReactNode {
  return (
    <Dialog title="Partial approval" titleClass="error">
      <p>
        Card approved ${prompt.approved} of ${prompt.requested}, ${prompt.amountDue} still due
      </p>
      <p>Choose how to collect the rest:</p>
      {prompt.actions.map((action) => {
        const choice = partialApprovalChoice(action, prompt);
        return (
          <button
            key={action}
            type="button"
            className="choice"
            onClick={() => prompt.choose(action)}
          >
            <span>{choice.label}</span>
            <span className="small">{choice.description}</span>
          </button>
        );
      })}
    </Dialog>
  );
}

/** The recovery and partial-approval prompts and the outcome popup, over everything else. */
export function Dialogs(): ReactNode {
  const controller = useController();
  const state = useEmulatorState();
  const outcome = state.paymentOutcome;
  const receipts = outcome?.receipts ?? (outcome?.receipt ? [{ text: outcome.receipt }] : []);
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
          {receipts.map((receipt, index) => (
            <Fragment key={index}>
              <hr />
              {receipt.caption ? <p className="small">{receipt.caption}</p> : null}
              <pre className="receipt" aria-label={receipt.caption ?? 'Receipt'}>
                {receipt.text}
              </pre>
            </Fragment>
          ))}
        </Dialog>
      ) : null}
      {state.paymentRecovery ? <PaymentRecoveryDialog prompt={state.paymentRecovery} /> : null}
      {state.partialApproval ? <PartialApprovalDialog prompt={state.partialApproval} /> : null}
    </>
  );
}
