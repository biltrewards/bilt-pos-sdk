import type { ReactNode } from 'react';
import { BasketPanel } from '../components/BasketPanel';
import { CartDraft } from '../components/CartDraft';
import { CatalogPanel } from '../components/CatalogPanel';
import { GiftCardPanel } from '../components/GiftCardPanel';
import { MemberPanel } from '../components/MemberPanel';
import { SettlementPanel } from '../components/SettlementPanel';
import { useLane } from '../lane/LaneProvider';

/**
 * The register's main screen: scan, basket, loyalty, stored value and settlement. Everything
 * below the session is the same in both modes except settlement and stored value, which need
 * the terminal. The basket locks while money moves and once it has settled.
 */
export function SalePane(): ReactNode {
  const { session, settlement } = useLane();
  if (!session) return null;
  const locked =
    settlement.status === 'running' ||
    settlement.status === 'awaitingReply' ||
    settlement.status === 'succeeded';

  return (
    <div className="columns">
      <div className="column">
        <CatalogPanel disabled={locked} />
        <CartDraft disabled={locked} />
      </div>
      <div className="column">
        <BasketPanel locked={locked} />
        <GiftCardPanel locked={locked} />
      </div>
      <div className="column">
        <MemberPanel locked={locked} />
        <SettlementPanel />
      </div>
    </div>
  );
}
