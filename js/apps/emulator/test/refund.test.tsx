// The Refund tab against the SDK double: the completed sales list, a full refund (a void of the
// stored sale on a fresh session, with no checkout open) and an item return rung into the active
// basket and settled against the original card leg.
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import {
  addProduct,
  argOf,
  button,
  events,
  MockTerminalSession,
  region,
  renderApp,
} from './harness';

async function sell(...products: string[]): Promise<void> {
  fireEvent.click(button('Start Checkout'));
  await waitFor(() => expect(button('End Checkout')).toBeTruthy());
  for (const product of products) addProduct(product);
  const settle = await screen.findByRole('button', { name: /^Settle \$/ });
  await waitFor(() => expect((settle as HTMLButtonElement).disabled).toBe(false));
  fireEvent.click(settle);
  const popup = await screen.findByRole('dialog', { name: 'Settlement complete' });
  fireEvent.click(within(popup).getByRole('button', { name: 'OK' }));
  await waitFor(() => expect(button('Start Checkout')).toBeTruthy());
}

function openRefundTab(): void {
  fireEvent.click(
    within(screen.getByRole('tablist', { name: 'Screens' })).getByRole('tab', { name: 'Refund' }),
  );
}

describe('the Refund tab', () => {
  it('lists completed sales and refunds one in full on its own session', async () => {
    const { pos } = await renderApp();
    openRefundTab();
    expect(screen.getByText('None stored yet — complete a payment on the Sale tab')).toBeTruthy();
    fireEvent.click(
      within(screen.getByRole('tablist', { name: 'Screens' })).getByRole('tab', { name: 'Sale' }),
    );
    await sell('Desk Lamp');
    openRefundTab();

    const list = region('Completed sales');
    await waitFor(() => expect(within(list).getByText('$37.31')).toBeTruthy());
    expect(within(list).getByText('guest')).toBeTruthy();
    const details = region('Refund');
    expect(within(details).getByText('1× Desk Lamp')).toBeTruthy();
    expect(within(details).getByText('$37.31')).toBeTruthy();
    expect((screen.getByLabelText('Full amount') as HTMLInputElement).checked).toBe(true);

    const voids = vi.spyOn(MockTerminalSession.prototype, 'voidTransaction');
    fireEvent.click(button('Refund $37.31'));
    const popup = await screen.findByRole('dialog', { name: 'Refund complete' });
    expect(within(popup).getByText('Refunded')).toBeTruthy();
    expect(argOf(voids, 0)).toMatchObject({ cardPoiTransactionId: 'POI-1' });
    // A fresh session for the refund, ended afterwards.
    expect(pos.sessions).toHaveLength(2);
    await waitFor(() => expect(pos.sessions[1]?.state).toBe('ended'));
    await waitFor(() => expect(within(list).getByText('guest · refunded')).toBeTruthy());
    expect(within(details).getByText('Refunded in full — nothing left to refund')).toBeTruthy();
    expect(events()).toContain('Starting full refund of sale');
    voids.mockRestore();
  });

  it('rings selected items back into the active basket and settles them against the card', async () => {
    const { terminal } = await renderApp();
    await sell('Desk Lamp', 'Banana');
    openRefundTab();
    const details = await screen.findByRole('region', { name: 'Refund' });
    await waitFor(() => expect(within(details).getByText('1× Desk Lamp')).toBeTruthy());

    fireEvent.click(screen.getByLabelText('Selected items'));
    expect(screen.getByText('Start a checkout to ring returns into its basket')).toBeTruthy();
    fireEvent.click(button('Start Checkout'));
    await waitFor(() => expect(button('End Checkout')).toBeTruthy());
    expect((button('Add return to basket ($0.00)') as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByLabelText('Return Desk Lamp'));
    fireEvent.click(button('Add return to basket ($37.31)'));

    await waitFor(() =>
      expect(within(region('Basket')).getByText('1× Desk Lamp (return)')).toBeTruthy(),
    );
    // The rung return is no longer returnable.
    await waitFor(() => expect(region('Refund').textContent).toContain('0× Desk Lamp'));
    expect(events()).toContain('Return rung in: 1× Desk Lamp — restores to the card on settlement');

    const settle = vi.spyOn(terminal(), 'settle');
    fireEvent.click(screen.getByLabelText('Net settlement'));
    fireEvent.click(await screen.findByRole('button', { name: /^Settle/ }));
    const popup = await screen.findByRole('dialog', { name: 'Settlement complete' });
    expect(within(popup).getByText(/returned \$37\.31 to the card/)).toBeTruthy();
    expect(settle.mock.calls[0]?.[0]?.refunds).toEqual([
      { type: 'CARD', amount: '37.31', originalPoiTransactionId: 'POI-1' },
    ]);
    fireEvent.click(within(popup).getByRole('button', { name: 'OK' }));
    await waitFor(() =>
      expect(within(details).getByText('0× Desk Lamp (1 of 1 refunded)')).toBeTruthy(),
    );
    expect(within(region('Completed sales')).getByText('guest · partially refunded')).toBeTruthy();
    // Item refunds block the full refund: it would over-return.
    expect((screen.getByLabelText('Full amount') as HTMLInputElement).disabled).toBe(true);
  });
});
