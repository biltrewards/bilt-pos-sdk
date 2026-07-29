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
    /** Decimal price string, e.g. `79.99` — the format `BasketItem.of` expects. */
    val priceDecimal: String
        get() = "${priceMinor / 100}.${(priceMinor % 100).toString().padStart(2, '0')}"

    val priceLabel: String
        get() = "$$priceDecimal"
}

/** Source of the products offered on the emulator's quick-buy grid. */
interface ProductProvider {
    fun products(): List<Product>
}
