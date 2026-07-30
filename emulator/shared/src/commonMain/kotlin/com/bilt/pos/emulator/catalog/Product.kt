package com.bilt.pos.emulator.catalog

/**
 * A sellable product the emulator can ring up.
 *
 * @property priceMinor unit price in minor currency units (cents) — kept as a
 *   Long so this stays platform-neutral; the session layer converts to a
 *   decimal string for the SDK.
 */
data class Product(
    val sku: String,
    val name: String,
    val priceMinor: Long,
    val category: String,
) {
    /**
     * Decimal price string, e.g. `79.99` — the format `BasketItem.of` expects.
     *
     * Assumes a two-decimal (exponent-2) currency, i.e. USD. If the emulator
     * ever handles non-USD currencies, this — and [priceLabel]'s hardcoded
     * `$` — should move into a Money type owning currency, exponent, and
     * formatting rather than being patched in place (JPY has exponent 0,
     * KWD has 3).
     */
    val priceDecimal: String
        get() = "${priceMinor / 100}.${(priceMinor % 100).toString().padStart(2, '0')}"

    val priceLabel: String
        get() = "$$priceDecimal"
}

/** Source of the products offered on the emulator's quick-buy grid. */
interface ProductProvider {
    fun products(): List<Product>
}
