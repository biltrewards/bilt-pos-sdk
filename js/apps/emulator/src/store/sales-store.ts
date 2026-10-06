import { useEffect, useState } from 'react';
import type { RefundRecord, SaleRecord, StoredSale, VoidRecord } from './sale-record';

/**
 * Persistence for completed sales, so referenced refunds and voids can run after the checkout
 * session (or the page) that produced them is gone: the desktop emulator's `SaleStore` over
 * IndexedDB. Writes are best-effort from the caller's point of view: a store failure must never
 * fail the checkout that produced the sale, so callers log and carry on.
 */
export interface SaleStore {
  recordSale(sale: SaleRecord): Promise<void>;
  recordRefund(refund: RefundRecord): Promise<void>;
  recordVoid(record: VoidRecord): Promise<void>;
  findSale(saleId: string): Promise<StoredSale | null>;
  /** The most recent sales, newest first. */
  listSales(limit?: number): Promise<readonly StoredSale[]>;
  /** Called after every write; the Refunds pane refreshes on it. */
  subscribe(listener: () => void): () => void;
}

const DB_NAME = 'bilt-pos-emulator';
const DB_VERSION = 1;

type Store = 'sales' | 'refunds' | 'voids';

function request<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error('IndexedDB request failed'));
  });
}

function openDatabase(factory: IDBFactory, name: string): Promise<IDBDatabase> {
  const open = factory.open(name, DB_VERSION);
  open.onupgradeneeded = () => {
    const db = open.result;
    if (!db.objectStoreNames.contains('sales')) {
      db.createObjectStore('sales', { keyPath: 'id' }).createIndex('completedAt', 'completedAt');
    }
    if (!db.objectStoreNames.contains('refunds')) {
      db.createObjectStore('refunds', { autoIncrement: true }).createIndex('saleId', 'saleId');
    }
    if (!db.objectStoreNames.contains('voids')) {
      db.createObjectStore('voids', { keyPath: 'saleId' });
    }
  };
  return request(open);
}

export class IndexedDbSaleStore implements SaleStore {
  private db: Promise<IDBDatabase> | null = null;
  private readonly listeners = new Set<() => void>();

  constructor(
    private readonly factory: IDBFactory = indexedDB,
    private readonly name: string = DB_NAME,
  ) {}

  private database(): Promise<IDBDatabase> {
    this.db ??= openDatabase(this.factory, this.name);
    return this.db;
  }

  private async write(store: Store, run: (os: IDBObjectStore) => IDBRequest): Promise<void> {
    const db = await this.database();
    const tx = db.transaction(store, 'readwrite');
    await request(run(tx.objectStore(store)));
    await new Promise<void>((resolve, reject) => {
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error ?? new Error('IndexedDB transaction failed'));
      tx.onabort = () => reject(tx.error ?? new Error('IndexedDB transaction aborted'));
    });
    for (const listener of this.listeners) listener();
  }

  recordSale(sale: SaleRecord): Promise<void> {
    return this.write('sales', (os) => os.put(sale));
  }

  recordRefund(refund: RefundRecord): Promise<void> {
    return this.write('refunds', (os) => os.add(refund));
  }

  recordVoid(record: VoidRecord): Promise<void> {
    return this.write('voids', (os) => os.put(record));
  }

  private async fold(db: IDBDatabase, sale: SaleRecord): Promise<StoredSale> {
    const tx = db.transaction(['refunds', 'voids'], 'readonly');
    const refunds = await request(
      tx.objectStore('refunds').index('saleId').getAll(sale.id) as IDBRequest<RefundRecord[]>,
    );
    const voided = await request(
      tx.objectStore('voids').get(sale.id) as IDBRequest<VoidRecord | undefined>,
    );
    return { sale, refunds, voided: voided ?? null };
  }

  async findSale(saleId: string): Promise<StoredSale | null> {
    const db = await this.database();
    const sale = await request(
      db.transaction('sales').objectStore('sales').get(saleId) as IDBRequest<
        SaleRecord | undefined
      >,
    );
    return sale ? this.fold(db, sale) : null;
  }

  async listSales(limit = 50): Promise<readonly StoredSale[]> {
    const db = await this.database();
    const sales = await request(
      db.transaction('sales').objectStore('sales').index('completedAt').getAll() as IDBRequest<
        SaleRecord[]
      >,
    );
    const newest = sales.reverse().slice(0, limit);
    return Promise.all(newest.map((sale) => this.fold(db, sale)));
  }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }
}

/** The stored sales, newest first, refreshed after every write to the store. */
export function useStoredSales(store: SaleStore): {
  readonly sales: readonly StoredSale[];
  readonly error: Error | null;
} {
  const [state, setState] = useState<{ sales: readonly StoredSale[]; error: Error | null }>({
    sales: [],
    error: null,
  });
  useEffect(() => {
    let cancelled = false;
    const load = () =>
      store.listSales().then(
        (sales) => {
          if (!cancelled) setState({ sales, error: null });
        },
        (cause: unknown) => {
          if (!cancelled) {
            setState((previous) => ({
              sales: previous.sales,
              error: cause instanceof Error ? cause : new Error(String(cause)),
            }));
          }
        },
      );
    void load();
    const unsubscribe = store.subscribe(() => void load());
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [store]);
  return state;
}
