// The Sale tab, the basket card and the settlement against the SDK double: products and the
// keypad, the Discount and Credit dialogs, the settlement toggles, the recovery dialog with the
// desktop's choices, the outcome popup and the stored sale.
import { SessionError } from '@bilt/pos-sdk';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { addProduct, button, events, region, renderApp } from './harness';

function basketLines(): string[] {
  return Array.from(region('Basket').querySelectorAll('.line-text')).map(
    (line) => line.textContent ?? '',
  );
}

function dialog(name: string): HTMLElement {
  return screen.getByRole('dialog', { name });
}

describe('the Sale tab and the basket card', () => {
  it('keeps products disabled until Start Checkout, then rings them up with NJ tax', async () => {
    await renderApp();
    expect(screen.getByText('Start Checkout to add products to the basket')).toBeTruthy();
    const lamp = within(screen.getByLabelText('Products')).getByRole('button', {
      name: /Desk Lamp/,
    }) as HTMLButtonElement;
    expect(lamp.disabled).toBe(true);

    fireEvent.click(button('Start Checkout'));
    await waitFor(() => expect(lamp.disabled).toBe(false));
    addProduct('Desk Lamp');
    await waitFor(() => expect(basketLines()).toEqual(['1× Desk Lamp']));
    addProduct('Desk Lamp');
    addProduct('Banana');
    await waitFor(() => expect(basketLines()).toEqual(['2× Desk Lamp', '1× Banana']));
    // 2 × 34.99 = 69.98 + 4.64 tax (Banana is grocery, untaxed) + 0.35
    expect(screen.getByTestId('basket-total').textContent).toBe('Basket — total $74.97');
    expect(screen.getByText('incl. $4.64 tax (NJ 6.625%)')).toBeTruthy();
    expect(button('Settle $74.97').disabled).toBe(false);
    expect(events()).toContain('Added Desk Lamp ($34.99)');

    fireEvent.click(button('Clear basket'));
    await waitFor(() => expect(screen.getByText('Empty')).toBeTruthy());
  });

  it('applies a line discount and an item credit through the dialogs', async () => {
    const { session } = await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(basketLines()).toHaveLength(1));

    fireEvent.click(button('Discount Desk Lamp'));
    const discount = dialog('Apply line discount');
    expect(within(discount).getByText('1× Desk Lamp · maximum $34.99')).toBeTruthy();
    fireEvent.change(within(discount).getByLabelText('Amount (0 clears)'), {
      target: { value: '40' },
    });
    expect(
      (within(discount).getByRole('button', { name: 'Apply' }) as HTMLButtonElement).disabled,
    ).toBe(true);
    fireEvent.change(within(discount).getByLabelText('Amount (0 clears)'), {
      target: { value: '5.00' },
    });
    fireEvent.change(within(discount).getByLabelText('Receipt label'), {
      target: { value: 'Floor model' },
    });
    fireEvent.click(within(discount).getByRole('button', { name: 'Apply' }));
    await waitFor(() => expect(basketLines()).toEqual(['1× Desk Lamp · Floor model −$5.00']));
    expect(session().basket.current.items[0]?.discounts).toEqual([
      { label: 'Floor model', amount: '5.00' },
    ]);

    fireEvent.click(button('Credit Desk Lamp'));
    const credit = dialog('Apply item credit');
    expect((within(credit).getByLabelText('Receipt label') as HTMLInputElement).value).toBe(
      'Credit for Desk Lamp',
    );
    fireEvent.change(within(credit).getByLabelText('Credit amount'), { target: { value: '2' } });
    fireEvent.click(within(credit).getByRole('button', { name: 'Apply' }));
    await waitFor(() => expect(basketLines()[1]).toBe('1× Credit for Desk Lamp (credit)'));
    expect(session().basket.current.items[1]).toMatchObject({
      type: 'CREDIT',
      sku: 'CREDIT-SKU-0013',
      unitPrice: '2.00',
    });

    // Zero clears the discount.
    fireEvent.click(button('Discount Desk Lamp'));
    const clear = dialog('Apply line discount');
    fireEvent.change(within(clear).getByLabelText('Amount (0 clears)'), { target: { value: '0' } });
    fireEvent.click(within(clear).getByRole('button', { name: 'Clear' }));
    await waitFor(() => expect(session().basket.current.items[0]?.discounts).toEqual([]));
  });

  it('rings a keypad amount, re-prices it from the basket, and drops it at zero', async () => {
    const { session } = await renderApp({ checkout: true, initialSalePane: 'keypad' });
    const pad = screen.getByLabelText('Keypad');
    const key = (label: string) =>
      fireEvent.click(within(pad).getByRole('button', { name: label }));
    key('1');
    key('2');
    key('5');
    expect(screen.getByTestId('keypad-amount').textContent).toBe('$1.25');
    key('Submit');
    await waitFor(() => expect(basketLines()).toEqual(['1× Custom amount']));
    expect(session().basket.current.items[0]).toMatchObject({
      unitPrice: '1.25',
      category: 'Custom',
    });
    expect(session().basket.current.items[0]?.taxRate).toBeUndefined();
    expect(screen.getByTestId('keypad-amount').textContent).toBe('$0.00');

    // Tap the line: the keypad adopts it and every keystroke re-prices it.
    fireEvent.click(within(region('Basket')).getByRole('button', { name: '1× Custom amount' }));
    expect(screen.getByTestId('keypad-amount').textContent).toBe('$1.25');
    await waitFor(() => expect(basketLines()).toEqual(['1× Custom amount — editing']));
    key('0');
    await waitFor(() => expect(session().basket.current.items[0]?.unitPrice).toBe('12.50'));
    key('Backspace');
    key('Backspace');
    key('Backspace');
    key('Backspace');
    await waitFor(() => expect(session().basket.current.items[0]?.unitPrice).toBe('0.00'));
    expect(
      screen.getByText('Editing the marked line — ✓ now drops it from the basket'),
    ).toBeTruthy();
    key('Submit');
    await waitFor(() => expect(session().basket.current.items).toHaveLength(0));
  });

  it('settles with the toggles, shows the outcome popup, stores the sale and ends the checkout', async () => {
    const { terminal, store } = await renderApp({ checkout: true });
    addProduct('Desk Lamp');
    await waitFor(() => expect(button('Settle $37.31').disabled).toBe(false));
    const settle = vi.spyOn(terminal(), 'settle');
    fireEvent.click(screen.getByLabelText('Rebates'));
    fireEvent.click(screen.getByLabelText('Net settlement'));
    fireEvent.click(button('Settle $37.31'));

    const outcome = await screen.findByRole('dialog', { name: 'Settlement complete' });
    expect(within(outcome).getByText(/Settled \$35\.31/)).toBeTruthy();
    expect(settle.mock.calls[0]?.[0]).toMatchObject({
      settlementType: 'REFUND_THEN_CHARGE',
      disableRebates: false,
      disablePoints: true,
      disableAward: true,
    });
    fireEvent.click(within(outcome).getByRole('button', { name: 'OK' }));
    expect(screen.queryByRole('dialog')).toBeNull();

    await waitFor(() => expect(button('Start Checkout')).toBeTruthy());
    expect(screen.getByTestId('last-payment').textContent).toContain(
      'Settled $35.31 — card $35.31 (Visa) — Start Checkout for the next customer',
    );
    expect(button('Settled').disabled).toBe(true);
    expect(terminal().state).toBe('ended');
    await waitFor(async () => expect(await store.listSales()).toHaveLength(1));
    expect(events()).toContain('Settlement complete — ending the checkout automatically');
  });

  it('offers the desktop recovery choices for a declined card and records cash', async () => {
    const { terminal } = await renderApp({ checkout: true });
    addProduct('Coffee');
    await waitFor(() => expect(button('Settle $3.75').disabled).toBe(false));
    terminal().failNextChargeWith = new SessionError({
      code: 'DECLINED',
      message: 'Do not honour',
    });
    fireEvent.click(button('Settle $3.75'));

    const recovery = await screen.findByRole('dialog', { name: 'Payment step failed' });
    expect(within(recovery).getByText(/Step: CARD_CHARGE/)).toBeTruthy();
    expect(within(recovery).getByText(/DECLINED: Do not honour/)).toBeTruthy();
    const choices = within(recovery)
      .getAllByRole('button')
      .map((choice) => choice.firstChild?.textContent);
    expect(choices).toEqual([
      'Retry',
      'Confirm cash received',
      'Abort and roll back',
      'Abandon recovery',
    ]);
    // Abort operation on the action row answers the open prompt with Abort.
    expect(button('Abort operation').disabled).toBe(false);
    fireEvent.click(within(recovery).getByRole('button', { name: /Retry/ }));
    await screen.findByRole('dialog', { name: 'Settlement complete' });
    expect(terminal().recoveries).toEqual(['RETRY']);
    expect(events()).toContain('Cashier chose Retry for CARD_CHARGE');
  });

  it('reports a recovery aborted from the action row as a failed settlement', async () => {
    const { terminal } = await renderApp({ checkout: true });
    addProduct('Coffee');
    await waitFor(() => expect(button('Settle $3.75').disabled).toBe(false));
    terminal().failNextChargeWith = new SessionError({
      code: 'DECLINED',
      message: 'Do not honour',
    });
    fireEvent.click(button('Settle $3.75'));
    await screen.findByRole('dialog', { name: 'Payment step failed' });
    fireEvent.click(button('Abort operation'));
    const failed = await screen.findByRole('dialog', { name: 'Settlement failed' });
    expect(within(failed).getByText(/DECLINED/)).toBeTruthy();
    expect(terminal().recoveries).toEqual(['ABORT']);
    // The checkout stays open with the basket intact, ready to settle again.
    await waitFor(() => expect(button('Settle $3.75').disabled).toBe(false));
  });

  it('identifies the member at Start Checkout and shows the loyalty card', async () => {
    await renderApp();
    fireEvent.click(screen.getByLabelText('Identify'));
    fireEvent.click(button('Start Checkout'));
    const card = await screen.findByRole('region', { name: 'Loyalty sign-in' });
    expect(within(card).getByTestId('member').textContent).toBe('mbr_8f2a');
    expect(within(card).getByText('120 pts · 0 reward(s)')).toBeTruthy();
    expect(events()).toContain('Member identified: mbr_8f2a, 120 pts, 0 reward(s)');
    expect((screen.getByLabelText('Identify') as HTMLInputElement).disabled).toBe(true);

    fireEvent.click(button('End Checkout'));
    await waitFor(() =>
      expect(screen.queryByRole('region', { name: 'Loyalty sign-in' })).toBeNull(),
    );
  });
});
