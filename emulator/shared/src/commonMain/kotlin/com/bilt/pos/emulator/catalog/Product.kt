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
    /** Decimal price string, e.g. `79.99` — the format `BasketItem.sale` expects. */
    val priceDecimal: String
        get() = minorUnitsToDecimal(priceMinor)

    val priceLabel: String
        get() = "$$priceDecimal"
}

/**
 * Formats an amount given in [minor] currency units (cents) and returns it
 * as a plain decimal string — e.g. `7999` → `"79.99"` — the form the SDK's
 * basket APIs expect and the UI's amount labels use.
 *
 * Assumes a two-decimal (exponent-2) currency, i.e. USD. If the emulator
 * ever handles non-USD currencies, this — and the callers' hardcoded `$` —
 * should move into a Money type owning currency, exponent, and formatting
 * rather than being patched in place (JPY has exponent 0, KWD has 3).
 */
fun minorUnitsToDecimal(minor: Long): String =
    "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"

/** Source of the products offered on the emulator's quick-buy grid. */
interface ProductProvider {
    fun products(): List<Product>
}
