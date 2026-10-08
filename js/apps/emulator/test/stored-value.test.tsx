// The Stored Value tab against the SDK double: balance inquiry and activation with their
// outcome popups, a card read adopted into the card field, and a gift-card purchase that loads
// at settlement.
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { argOf, button, events, region, renderApp } from './harness';

async function outcome(name: string): Promise<HTMLElement> {
  const popup = await screen.findByRole('dialog', { name });
  return popup;
}

function dismiss(popup: HTMLElement): void {
  fireEvent.click(within(popup).getByRole('button', { name: 'OK' }));
}

describe('the Stored Value tab', () => {
  it('checks a balance and activates a card, each with its outcome popup', async () => {
    const { terminal } = await renderApp({ checkout: true, initialTab: 'storedValue' });
    const tab = region('Stored value');
    expect(Array.from(tab.querySelectorAll('[role=tab]')).map((t) => t.textContent)).toEqual([
      'Balance inquiry',
      'Activation',
      'Purchase',
    ]);
    const balance = vi.spyOn(terminal(), 'storedValueBalance');
    fireEvent.change(within(tab).getByLabelText('Card number'), {
      target: { value: '6006491260550218157' },
    });
    fireEvent.click(button('Check balance'));
    const popup = await outcome('Balance inquiry complete');
    expect(within(popup).getByText('Available balance: $25.00 USD')).toBeTruthy();
    expect(argOf(balance, 0)).toEqual({
      storedValueId: '6006491260550218157',
      identificationType: 'PAN',
      entryMode: 'KEYED',
    });
    dismiss(popup);

    fireEvent.click(within(tab).getByRole('tab', { name: 'Activation' }));
    const activate = vi.spyOn(terminal(), 'storedValueActivate');
    fireEvent.click(button('Activate card'));
    const activated = await outcome('Activation complete');
    expect(within(activated).getByText('Stored value card activated (txn POI-SV-1)')).toBeTruthy();
    expect(argOf(activate, 1)).toBe('0.00');
  });

  it('adopts a terminal card read into the card field', async () => {
    const { terminal } = await renderApp({ checkout: true, initialTab: 'storedValue' });
    vi.spyOn(terminal(), 'acquireCard').mockImplementation(() =>
      Object.assign(
        Promise.resolve({
          rawPan: '6006491260550218157',
          maskedPan: '****8157',
          additionalData: {},
        }),
        {
          id: 'op_x',
          type: 'acquireCard' as const,
          status: 'succeeded' as const,
          abort: async () => undefined,
        },
      ),
    );
    fireEvent.click(button('Read card'));
    await waitFor(() =>
      expect((screen.getByLabelText('Card number') as HTMLInputElement).value).toBe(
        '6006491260550218157',
      ),
    );
    expect(events()).toContain('Card read: ****8157 — filled into the stored value field');
  });

  it('rings a gift-card purchase that the settlement loads', async () => {
    const { terminal } = await renderApp({ checkout: true, initialTab: 'storedValue' });
    const tab = region('Stored value');
    fireEvent.click(within(tab).getByRole('tab', { name: 'Purchase' }));
    expect((button('Add purchase to basket') as HTMLButtonElement).disabled).toBe(true);
    fireEvent.change(within(tab).getByLabelText('Purchase amount'), { target: { value: '25.00' } });
    fireEvent.change(within(tab).getByLabelText('Card number'), { target: { value: '6006' } });
    fireEvent.click(button('Add purchase to basket'));
    await waitFor(() =>
      expect(
        within(region('Basket')).getByText('1× Gift card (stored value purchase)'),
      ).toBeTruthy(),
    );
    const settle = vi.spyOn(terminal(), 'settle');
    fireEvent.click(button('Settle $25.00'));
    await outcome('Settlement complete');
    expect(settle.mock.calls[0]?.[0]?.fulfillments).toEqual([
      {
        basketReference: expect.stringMatching(/^gift-card-/),
        type: 'RELOAD',
        card: { storedValueId: '6006', identificationType: 'PAN', entryMode: 'KEYED' },
      },
    ]);
  });
});
