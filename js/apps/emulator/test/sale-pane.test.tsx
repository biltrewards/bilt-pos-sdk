// The Sale pane against the SDK double: the three updaters, discounts and credits, keyed-in
// lines, loyalty, gift cards, and a settlement with its persisted step, recovery prompt and
// recorded sale.
import { SessionError } from '@bilt/pos-sdk';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PENDING_STEP_KEY } from '../src/lane/LaneProvider';
import { flush, renderApp } from './harness';

const grandTotal = () => screen.getByTestId('grand-total').textContent;

describe('the Sale pane', () => {
  it('scans incrementally, adjusts quantities, discounts and credits a line, and syncs a POS-owned cart', async () => {
    const { session } = await renderApp();

    fireEvent.click(screen.getByRole('button', { name: /Desk Lamp/ }));
    // 34.99 + 6.625% tax (2.32)
    await waitFor(() => expect(grandTotal()).toContain('37.31'));
    expect(screen.getByText('last change: INCREMENTAL')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Increase Desk Lamp' }));
    await waitFor(() => expect(grandTotal()).toContain('74.62'));

    fireEvent.click(screen.getByRole('button', { name: 'discount' }));
    const editor = screen.getByRole('form', { name: 'Add discount' });
    fireEvent.change(within(editor).getByLabelText('Amount'), { target: { value: '5' } });
    fireEvent.click(within(editor).getByLabelText('offer'));
    fireEvent.change(within(editor).getByLabelText('Offer reference'), {
      target: { value: 'OFFER-42' },
    });
    fireEvent.click(within(editor).getByRole('button', { name: 'Apply discount' }));
    await waitFor(() => expect(screen.getByText(/Offer OFFER-42 \(OFFER-42\)/)).toBeTruthy());
    expect(session().basket.current.items[0]?.discounts).toEqual([
      { label: 'Offer OFFER-42', amount: '5.00', reference: 'OFFER-42' },
    ]);

    fireEvent.click(screen.getByRole('button', { name: 'credit' }));
    const creditEditor = screen.getByRole('form', { name: 'Add credit' });
    fireEvent.change(within(creditEditor).getByLabelText('Amount'), { target: { value: '2.00' } });
    fireEvent.change(within(creditEditor).getByLabelText('Label'), {
      target: { value: 'Goodwill' },
    });
    fireEvent.click(within(creditEditor).getByRole('button', { name: 'Add credit' }));
    await waitFor(() => expect(screen.getByText('Goodwill')).toBeTruthy());
    expect(session().basket.current.items[1]).toMatchObject({ type: 'CREDIT', unitPrice: '2.00' });

    fireEvent.click(screen.getByRole('button', { name: /Remove all discounts/ }));
    await waitFor(() => expect(screen.getByText('last change: BATCH')).toBeTruthy());

    fireEvent.change(screen.getByLabelText('Custom amount'), { target: { value: '12.50' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add custom item' }));
    await waitFor(() => expect(screen.getByText('Custom amount')).toBeTruthy());
    expect(session().basket.current.items.at(-1)?.sku).toMatch(/^CUSTOM-/);

    fireEvent.click(screen.getByRole('button', { name: 'show' }));
    fireEvent.change(screen.getByLabelText('Banana draft quantity'), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: /Sync cart \(1 line\)/ }));
    await waitFor(() => expect(screen.getByText('last change: REPLACE')).toBeTruthy());
    expect(session().basket.current.items).toHaveLength(1);
    expect(session().basket.current.items[0]).toMatchObject({ sku: 'SKU-0002', quantity: 3 });
  });

  it('signs a member in by phone and on the terminal and shows the member card', async () => {
    const { terminal } = await renderApp();
    fireEvent.change(screen.getByLabelText('Phone number'), {
      target: { value: '+1 201 555 0123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in by phone' }));
    await waitFor(() => expect(screen.getByText(/Looking up phone/)).toBeTruthy());
    expect(terminal().member.current?.resolver).toMatchObject({
      type: 'PHONE',
      keyedByCashier: true,
    });

    fireEvent.click(screen.getByRole('button', { name: 'Identify on terminal' }));
    await waitFor(() => expect(screen.getByTestId('member-card')).toBeTruthy());
    expect(screen.getByTestId('member-card').textContent).toContain('mbr_8f2a');
    expect(screen.getByText(/Terminal says: FOUND/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    await waitFor(() => expect(screen.getByText('Guest checkout.')).toBeTruthy());
  });

  it('rings a gift-card line with its fulfilment and registers a tender card', async () => {
    const { session } = await renderApp();
    fireEvent.change(screen.getByLabelText('Gift card face value'), { target: { value: '25' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add gift card line' }));
    await waitFor(() =>
      expect(screen.getByText(/activate \$25\.00 on 6006491260550218157/)).toBeTruthy(),
    );
    const line = session().basket.current.items[0];
    expect(line).toMatchObject({ sku: 'GIFT-CARD', unitPrice: '25.00' });
    expect(line?.reference).toMatch(/^gift-card-/);

    fireEvent.click(screen.getByRole('button', { name: 'Use as tender' }));
    await waitFor(() =>
      expect(screen.getByTestId('tender-card').textContent).toContain('6006491260550218157'),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Balance' }));
    await waitFor(() => expect(screen.getByText(/Balance: \$25\.00/)).toBeTruthy());

    // Removing the line drops its fulfilment; clearing the basket drops the tender card.
    fireEvent.click(screen.getByRole('button', { name: 'Remove Gift card' }));
    await waitFor(() => expect(screen.queryByText(/activate \$25\.00/)).toBeNull());
    fireEvent.click(screen.getByRole('button', { name: /Desk Lamp/ }));
    await waitFor(() => expect(grandTotal()).toContain('37.31'));
    fireEvent.click(screen.getByRole('button', { name: 'Clear basket' }));
    await waitFor(() => expect(screen.getByTestId('tender-card').textContent).toContain('none'));
  });

  it('settles with the persisted step, re-taxes after rebates, records the sale and moves to the next shopper', async () => {
    const { session, store } = await renderApp();
    fireEvent.click(screen.getByRole('button', { name: /Desk Lamp/ }));
    await waitFor(() => expect(grandTotal()).toContain('37.31'));
    fireEvent.click(screen.getByRole('button', { name: /^Pay/ }));

    await waitFor(() =>
      expect(screen.getByTestId('settlement-status').textContent).toContain('succeeded'),
    );
    const result = screen.getByTestId('settlement-result');
    // The double's 2.00 rebate is cart-level, so it comes off the re-taxed total.
    expect(result.textContent).toContain('Authorized$35.31');
    expect(result.textContent).toContain('CARD_CHARGE $35.31');
    expect(localStorage.getItem(PENDING_STEP_KEY)).toBeNull();
    expect(session().context.phase()).toBe('COMPLETE');

    await waitFor(async () => expect(await store.listSales()).toHaveLength(1));
    const [sale] = await store.listSales();
    expect(sale?.sale).toMatchObject({ authorizedAmount: '35.31', poiId: 'VictaLane-275839164' });
    expect(sale?.sale.legs.map((leg) => leg.type)).toEqual(['CARD']);
    expect(screen.getByText(/Recorded as sale/)).toBeTruthy();

    // Locked while settled: no scanning until the next shopper.
    expect((screen.getByRole('button', { name: /^Desk Lamp/ }) as HTMLButtonElement).disabled).toBe(
      true,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Next shopper' }));
    await waitFor(() =>
      expect(screen.getByTestId('settlement-status').textContent).toContain('idle'),
    );
    expect(session().basket.current.items).toHaveLength(0);
    expect(screen.getByText('Nothing rung yet.')).toBeTruthy();
  });

  it('holds a charge-side failure open for the cashier with a countdown and retries on request', async () => {
    const { terminal } = await renderApp();
    fireEvent.click(screen.getByRole('button', { name: /Coffee/ }));
    await waitFor(() => expect(grandTotal()).toContain('3.75'));
    terminal().failNextChargeWith = new SessionError({
      code: 'DECLINED',
      message: 'Card declined',
    });

    fireEvent.click(screen.getByRole('button', { name: /^Pay/ }));
    const prompt = await screen.findByRole('alertdialog', { name: 'Payment step failed' });
    expect(prompt.textContent).toContain('CARD_CHARGE failed: DECLINED');
    expect(within(prompt).getByTestId('countdown').textContent).toMatch(/^\d+s$/);
    expect(screen.getByTestId('settlement-status').textContent).toContain('awaitingReply');
    expect(
      (within(prompt).getByLabelText('External tender amount') as HTMLInputElement).value,
    ).toBe('0.35');

    fireEvent.click(within(prompt).getByRole('button', { name: 'Retry the step' }));
    await waitFor(() =>
      expect(screen.getByTestId('settlement-status').textContent).toContain('succeeded'),
    );
    expect(terminal().recoveries).toEqual(['RETRY']);
    await flush();
  });

  it('records an external tender for the amount due when the cashier took cash', async () => {
    const { terminal } = await renderApp();
    fireEvent.click(screen.getByRole('button', { name: /Coffee/ }));
    await waitFor(() => expect(grandTotal()).toContain('3.75'));
    terminal().failNextChargeWith = new SessionError({ code: 'TIMEOUT', message: 'No answer' });
    fireEvent.click(screen.getByRole('button', { name: /^Pay/ }));
    const prompt = await screen.findByRole('alertdialog', { name: 'Payment step failed' });
    fireEvent.click(within(prompt).getByRole('button', { name: 'Paid in cash' }));
    // The double treats anything but RETRY as a failure; what matters is the decision it saw.
    await waitFor(() => expect(terminal().recoveries).toEqual(['EXTERNAL']));
    await waitFor(() =>
      expect(screen.getByTestId('settlement-status').textContent).toContain('failed'),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Back to the basket' }));
    await waitFor(() =>
      expect(screen.getByTestId('settlement-status').textContent).toContain('idle'),
    );
  });
});
