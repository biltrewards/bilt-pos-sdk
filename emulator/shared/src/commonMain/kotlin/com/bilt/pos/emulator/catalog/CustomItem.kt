package com.bilt.pos.emulator.catalog

/**
 * Keypad-entered basket lines — an amount typed on the Sale tab's Keypad
 * pane rather than picked off the product grid, for ringing up a price the
 * mock catalog does not carry.
 *
 * The lines are ordinary sale items; only their SKU marks them, so the
 * basket projection can tell the UI which lines the keypad may re-price.
 */
object CustomItem {

    /** SKU prefix of a keypad line. */
    const val SKU_PREFIX: String = "CUSTOM-"

    /** Description every keypad line carries in the basket and on receipts. */
    const val DESCRIPTION: String = "Custom amount"

    /** Category reported to the SDK — its own, so it is never mistaken for
     *  a catalog category (and stays outside the tax exemptions). */
    const val CATEGORY: String = "Custom"

    /** How much of the basket id goes into a SKU: enough to separate the
     *  baskets one register rings in practice, short enough to read in a
     *  log line. */
    private const val BASKET_SEGMENT = 8

    fun isCustomSku(sku: String): Boolean = sku.startsWith(SKU_PREFIX)

    /**
     * The next free keypad SKU for the basket [basketId], which already
     * holds [skus] — one past the highest counter in use, so a line
     * removed and re-rung never collides with a live one.
     *
     * The basket id is part of the SKU because a keypad SKU outlives its
     * basket: a return is rung under the SKU its sale was recorded with,
     * and the basket keys return lines by SKU. A bare counter would give
     * every checkout's first keypad line the same SKU, so returning two
     * such sales into one basket would refuse the second (the engine
     * rejects a same-SKU upsert at a different price) or silently merge
     * two sales into one credit line when the prices happened to match.
     */
    fun nextSku(basketId: String, skus: Iterable<String>): String {
        val prefix = prefixFor(basketId)
        val highest = skus
            .filter { it.startsWith(prefix) }
            .mapNotNull { it.removePrefix(prefix).toIntOrNull() }
            .maxOrNull() ?: 0
        return "$prefix${highest + 1}"
    }

    /** Alphanumerics only: the id is a UUID today, but a SKU travels to
     *  the terminal and onto receipts, so it should not carry separators
     *  that a downstream format might treat as structure. */
    private fun prefixFor(basketId: String): String =
        SKU_PREFIX + basketId.filter { it.isLetterOrDigit() }.take(BASKET_SEGMENT) + "-"
}
