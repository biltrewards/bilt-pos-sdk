// The identify control in the connection card: the terminal PIN pad sign-in, a register-supplied
// phone, email or account number looked up on the terminal, and a resolved member id attached
// and confirmed in the background.
import type { IdentifyResult, Member } from '@biltorg/pos-sdk';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { argOf, button, events, flush, MockTerminalSession, renderApp } from './harness';

function identifyBy(label: string): void {
  fireEvent.change(screen.getByLabelText('Identify by'), {
    target: {
      value: {
        'Terminal PIN pad': 'PIN_PAD',
        Phone: 'PHONE',
        Email: 'EMAIL',
        'Account number': 'ACCOUNT_NUMBER',
        'Resolved member id': 'RESOLVED_ID',
      }[label],
    },
  });
}

function enter(label: string, value: string): void {
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

function card(): HTMLElement {
  return screen.getByRole('region', { name: 'Loyalty sign-in' });
}

/** Makes the terminal answer every identification with `status`, keeping the real mock otherwise. */
function answerIdentify(status: IdentifyResult['status']) {
  const original = MockTerminalSession.prototype.identifyMember;
  return vi.spyOn(MockTerminalSession.prototype, 'identifyMember').mockImplementation(function (
    this: MockTerminalSession,
    arg,
  ) {
    if (status === 'FOUND') return original.call(this, arg as never);
    return Object.assign(
      Promise.resolve<IdentifyResult>({ status, rewards: [], pointBalance: 0 }),
      {
        id: 'op_nf',
        type: 'identifyMember' as const,
        status: 'succeeded' as const,
        abort: async () => undefined,
        guide: () => undefined,
      },
    );
  });
}

afterEach(() => vi.restoreAllMocks());

describe('the identify control', () => {
  it('lists the PIN pad first and selects it, with no value field', async () => {
    await renderApp({ checkout: true });
    const picker = screen.getByLabelText('Identify by') as HTMLSelectElement;
    expect(Array.from(picker.options).map((option) => option.textContent)).toEqual([
      'Terminal PIN pad',
      'Phone',
      'Email',
      'Account number',
      'Resolved member id',
    ]);
    expect(picker.value).toBe('PIN_PAD');
    expect(screen.queryByRole('textbox', { name: 'Phone' })).toBeNull();
    expect(button('Loyalty Sign-In')).toBeTruthy();

    identifyBy('Phone');
    expect(screen.getByRole('textbox', { name: 'Phone' })).toBeTruthy();
    expect(button('Look Up Member')).toBeTruthy();
    identifyBy('Resolved member id');
    expect(button('Attach Member')).toBeTruthy();
    identifyBy('Terminal PIN pad');
    expect(screen.queryByRole('textbox', { name: 'Resolved member id' })).toBeNull();
  });

  it('runs the existing keyed sign-in prompt from the PIN pad entry', async () => {
    const identify = vi.spyOn(MockTerminalSession.prototype, 'identifyMember');
    await renderApp({ checkout: true });
    fireEvent.click(button('Loyalty Sign-In'));
    await waitFor(() => expect(within(card()).getByTestId('member').textContent).toBe('mbr_8f2a'));
    expect(argOf(identify, 0)).toEqual({ forceEntryModes: ['KEYED'] });
    expect(events()).toContain('Loyalty sign-in on the terminal…');
  });

  it.each([
    ['Phone', '+1 201 555 0123', 'PHONE'],
    ['Email', 'aloy@example.com', 'EMAIL'],
    ['Account number', 'ACCT-778812', 'ACCOUNT_NUMBER'],
  ] as const)('looks the member up by %s and shows the card', async (label, value, type) => {
    const identify = vi.spyOn(MockTerminalSession.prototype, 'identifyMember');
    await renderApp({ checkout: true });
    identifyBy(label);
    enter(label, `  ${value} `);
    fireEvent.click(button('Look Up Member'));
    await waitFor(() => expect(within(card()).getByTestId('member').textContent).toBe('mbr_8f2a'));
    expect(argOf(identify, 0)).toEqual({ resolver: { type, value } });
    expect(events()).toContain(`Looking up the member by ${label.toLowerCase()} on the terminal…`);
    expect(events()).toContain('Member identified: mbr_8f2a, 120 pts, 0 reward(s)');
  });

  it('reports a lookup the terminal found no member for', async () => {
    answerIdentify('NOT_FOUND');
    await renderApp({ checkout: true });
    identifyBy('Phone');
    enter('Phone', '2015550199');
    fireEvent.click(button('Look Up Member'));
    await waitFor(() =>
      expect(events()).toContain('No member found — loyalty steps will be skipped'),
    );
    expect(within(card()).getByText('No member found')).toBeTruthy();
  });

  it('sends nothing for a blank value', async () => {
    const identify = vi.spyOn(MockTerminalSession.prototype, 'identifyMember');
    const { terminal } = await renderApp({ checkout: true });
    const set = vi.spyOn(terminal().member, 'set');
    identifyBy('Email');
    enter('Email', '   ');
    fireEvent.click(button('Look Up Member'));
    identifyBy('Resolved member id');
    fireEvent.click(button('Attach Member'));
    await flush();
    expect(events()).toContain('Enter a email to identify the member');
    expect(events()).toContain('Enter a resolved member id to identify the member');
    expect(identify).not.toHaveBeenCalled();
    expect(set).not.toHaveBeenCalled();
  });

  it('attaches a resolved id and upgrades the card when the terminal confirms it', async () => {
    const identify = vi.spyOn(MockTerminalSession.prototype, 'identifyMember');
    const { terminal } = await renderApp({ checkout: true });
    const set = vi.spyOn(terminal().member, 'set');
    identifyBy('Resolved member id');
    enter('Resolved member id', '98234');
    fireEvent.click(button('Attach Member'));
    await waitFor(() => expect(within(card()).getByTestId('member').textContent).toBe('98234'));
    expect(argOf(set, 0)).toEqual({ id: '98234' });
    expect(identify).not.toHaveBeenCalled();
    expect(events()).toContain('Attached member 98234 — confirming it on the terminal…');

    // the background AccountNumber/File BalanceInquiry answered
    const confirmed: Member = {
      resolved: true,
      memberId: '98234',
      status: 'FOUND',
      loyaltyBrand: 'K-Club',
      rewards: [],
      pointBalance: 500,
    };
    terminal().member.current = confirmed;
    terminal().emitter.emit('member.changed', { member: confirmed });
    await waitFor(() =>
      expect(within(card()).getByTestId('member').textContent).toBe('98234 · K-Club'),
    );
    expect(within(card()).getByText('500 pts · 0 reward(s)')).toBeTruthy();
    expect(events()).toContain('Member attached: 98234 · K-Club, 500 pts, 0 reward(s)');

    // a later confirmation replaces the balance rather than keeping the shown one
    const spent: Member = { ...confirmed, pointBalance: 0 };
    terminal().member.current = spent;
    terminal().emitter.emit('member.changed', { member: spent });
    await waitFor(() => expect(within(card()).getByText('0 pts · 0 reward(s)')).toBeTruthy());
  });

  it('holds other operations until the host has the resolved id', async () => {
    const { terminal } = await renderApp({ checkout: true });
    let accept!: (member: Member) => void;
    vi.spyOn(terminal().member, 'set').mockReturnValue(
      new Promise<Member>((resolve) => {
        accept = resolve;
      }),
    );
    identifyBy('Resolved member id');
    enter('Resolved member id', '98234');
    fireEvent.click(button('Attach Member'));
    fireEvent.click(button('Attach Member'));
    await flush();
    expect(events()).toContain('Another operation is already in progress');
    accept({ resolved: false, memberId: '98234', rewards: [], pointBalance: 0 });
    await waitFor(() =>
      expect(events()).toContain('Attached member 98234 — confirming it on the terminal…'),
    );
    identifyBy('Phone');
    enter('Phone', '2015550123');
    fireEvent.click(button('Look Up Member'));
    await waitFor(() =>
      expect(events()).toContain('Looking up the member by phone on the terminal…'),
    );
  });

  it('logs a declined confirmation and leaves the member attached', async () => {
    const { terminal } = await renderApp({ checkout: true });
    identifyBy('Resolved member id');
    enter('Resolved member id', '98234');
    fireEvent.click(button('Attach Member'));
    await waitFor(() => expect(within(card()).getByTestId('member').textContent).toBe('98234'));

    terminal().emitter.emit('background.error', {
      code: 'NOT_FOUND',
      message: 'the terminal found no member 98234',
    });
    await waitFor(() =>
      expect(events()).toContain(
        'Background operation failed (NOT_FOUND): the terminal found no member 98234',
      ),
    );
    expect(within(card()).getByTestId('member').textContent).toBe('98234');
    expect(terminal().member.current?.memberId).toBe('98234');
  });
});
