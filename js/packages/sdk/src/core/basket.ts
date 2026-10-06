import type { Basket, BasketDiscount, BasketItem, BasketMutation, Money } from '@bilt/pos-protocol';
import type { BasketCommand, Engine } from '../internal';
import type { BasketMutationBuilder, SessionBasket } from '../session';
import { newIdempotencyKey } from './ids';

/** Collects a `mutate` batch as protocol `BasketMutation`s. */
class MutationCollector implements BasketMutationBuilder {
  readonly mutations: BasketMutation[] = [];

  private push(mutation: BasketMutation): this {
    this.mutations.push(mutation);
    return this;
  }

  addItem(item: BasketItem, itemId?: string): this {
    return this.push(
      itemId === undefined ? { op: 'ADD_ITEM', item } : { op: 'ADD_ITEM', item, itemId },
    );
  }
  removeItem(itemId: string): this {
    return this.push({ op: 'REMOVE_ITEM', itemId });
  }
  removeItemBySku(sku: string): this {
    return this.push({ op: 'REMOVE_ITEM', sku });
  }
  updateItemQuantity(itemId: string, quantity: number): this {
    return this.push({ op: 'UPDATE_ITEM_QUANTITY', itemId, quantity });
  }
  updateItemQuantityBySku(sku: string, quantity: number): this {
    return this.push({ op: 'UPDATE_ITEM_QUANTITY', sku, quantity });
  }
  setDiscounts(itemId: string, discounts: readonly BasketDiscount[]): this {
    return this.push({ op: 'SET_DISCOUNTS', itemId, discounts: [...discounts] });
  }
  setDiscountsBySku(sku: string, discounts: readonly BasketDiscount[]): this {
    return this.push({ op: 'SET_DISCOUNTS', sku, discounts: [...discounts] });
  }
  setTaxRate(itemId: string, rate: Money): this {
    return this.push({ op: 'SET_TAX_RATE', itemId, amount: rate });
  }
  setTaxRateBySku(sku: string, rate: Money): this {
    return this.push({ op: 'SET_TAX_RATE', sku, amount: rate });
  }
  setTaxAmount(itemId: string, amount: Money): this {
    return this.push({ op: 'SET_TAX_AMOUNT', itemId, amount });
  }
  setTaxAmountBySku(sku: string, amount: Money): this {
    return this.push({ op: 'SET_TAX_AMOUNT', sku, amount });
  }
  setTaxTotal(amount: Money | null): this {
    return this.push({ op: 'SET_TAX_TOTAL', amount });
  }
}

/**
 * The basket facade over the engine. `current` follows `basket.changed` events (the session
 * updates it) and every write also installs the snapshot the engine returned, so a register
 * that awaits a write sees it reflected even before the event is processed.
 *
 * The `BySku` variants are addressed by SKU on the host, through a one-step `mutations` batch,
 * so the host's own rule for SKUs that exist in several line types applies; such a change
 * reports its `basket.changed` with source `BATCH`.
 */
export class SessionBasketImpl implements SessionBasket {
  current: Basket;

  constructor(
    private readonly engine: Engine,
    private readonly sessionId: string,
    initial: Basket,
  ) {
    this.current = initial;
  }

  private async run(command: BasketCommand): Promise<Basket> {
    const basket = await this.engine.basket(this.sessionId, command, {
      idempotencyKey: newIdempotencyKey(),
    });
    this.install(basket);
    return basket;
  }

  /** Installs a snapshot unless a newer one is already in place. */
  install(basket: Basket): void {
    if (basket.updatedAt >= this.current.updatedAt) this.current = basket;
  }

  private single(mutation: BasketMutation): Promise<Basket> {
    return this.run({ kind: 'mutate', mutations: [mutation] });
  }

  async refresh(): Promise<Basket> {
    const basket = await this.engine.basket(this.sessionId, { kind: 'get' });
    this.install(basket);
    return basket;
  }

  addItem(item: BasketItem, itemId?: string): Promise<Basket> {
    return this.run(itemId === undefined ? { kind: 'add', item } : { kind: 'add', item, itemId });
  }
  removeItem(itemId: string): Promise<Basket> {
    return this.run({ kind: 'remove', itemId });
  }
  removeItemBySku(sku: string): Promise<Basket> {
    return this.single({ op: 'REMOVE_ITEM', sku });
  }
  updateItemQuantity(itemId: string, quantity: number): Promise<Basket> {
    return this.run({ kind: 'patch', itemId, quantity });
  }
  updateItemQuantityBySku(sku: string, quantity: number): Promise<Basket> {
    return this.single({ op: 'UPDATE_ITEM_QUANTITY', sku, quantity });
  }
  setDiscounts(itemId: string, discounts: readonly BasketDiscount[]): Promise<Basket> {
    return this.run({ kind: 'patch', itemId, discounts: [...discounts] });
  }
  setDiscountsBySku(sku: string, discounts: readonly BasketDiscount[]): Promise<Basket> {
    return this.single({ op: 'SET_DISCOUNTS', sku, discounts: [...discounts] });
  }
  setTaxRate(itemId: string, rate: Money): Promise<Basket> {
    return this.run({ kind: 'patch', itemId, taxRate: rate });
  }
  setTaxRateBySku(sku: string, rate: Money): Promise<Basket> {
    return this.single({ op: 'SET_TAX_RATE', sku, amount: rate });
  }
  setTaxAmount(itemId: string, amount: Money): Promise<Basket> {
    return this.run({ kind: 'patch', itemId, taxAmount: amount });
  }
  setTaxAmountBySku(sku: string, amount: Money): Promise<Basket> {
    return this.single({ op: 'SET_TAX_AMOUNT', sku, amount });
  }
  setTaxTotal(amount: Money | null): Promise<Basket> {
    return this.run({ kind: 'taxTotal', amount });
  }

  mutate(build: (mutation: BasketMutationBuilder) => void): Promise<Basket> {
    const collector = new MutationCollector();
    build(collector);
    if (collector.mutations.length === 0) return Promise.resolve(this.current);
    return this.run({ kind: 'mutate', mutations: collector.mutations });
  }

  replace(content: Basket | readonly BasketItem[]): Promise<Basket> {
    return Array.isArray(content)
      ? this.run({ kind: 'replace', items: content as readonly BasketItem[] })
      : this.run({ kind: 'replace', snapshot: content as Basket });
  }

  clear(): Promise<Basket> {
    return this.run({ kind: 'clear' });
  }
}
