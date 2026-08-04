package com.bilt.pos.emulator.store

/**
 * Persistence for completed sales, so referenced refunds and voids can run
 * after the originating checkout session (or the whole process) is gone.
 *
 * Implementations may block on I/O — call from a background dispatcher, the
 * way the emulator controller runs all its work. Persistence is best-effort
 * from the caller's point of view: a store failure must never fail the
 * checkout that produced the sale.
 */
interface SaleStore {

    /** Records a completed sale with all its transaction legs. */
    fun recordSale(sale: SaleRecord)

    /** Records a refund issued against the sale [saleId]. */
    fun recordRefund(saleId: String, refund: RefundRecord)

    /** Records a void issued against the sale [saleId]. */
    fun recordVoid(saleId: String, voidRecord: VoidRecord)

    /** The sale with its refund/void history, or null when unknown. */
    fun findSale(saleId: String): StoredSale?

    /** The most recent sales, newest first. */
    fun listSales(limit: Int = 50): List<StoredSale>
}
