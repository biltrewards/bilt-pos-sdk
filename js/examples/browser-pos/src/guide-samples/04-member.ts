// Samples for "Identify the member".
import type { IdentifyResult, Member, TerminalShopperSession } from '@bilt/pos-sdk';

declare function showMember(member: Member | null): void;

export async function knownShopper(session: TerminalShopperSession): Promise<void> {
  // A member id attaches at once.
  await session.member.set({ id: 'mbr_8f2a' });
  // A resolver attaches as pending; the host looks it up and announces the result.
  await session.member.set({
    resolver: { type: 'PHONE', value: '+12015550123', keyedByCashier: true },
  });
  // Sign out.
  await session.member.clear();
}

export function followMember(session: TerminalShopperSession): () => void {
  return session.on('member.changed', ({ member }) => showMember(member));
}

export async function identifyOnTerminal(session: TerminalShopperSession): Promise<boolean> {
  const result: IdentifyResult = await session.identifyMember();
  switch (result.status) {
    case 'FOUND':
      return true; // attached to the session; member.changed has fired
    case 'NOT_FOUND':
    case 'SUSPENDED':
    case 'CANCELLED':
    case 'ERROR':
      return false; // guest checkout; only real failures reject
  }
}

export function lookupWithoutPrompt(session: TerminalShopperSession): Promise<IdentifyResult> {
  return session.identifyMember({ resolver: { type: 'ACCOUNT_ID', value: 'ACC-77' } });
}
