package com.bilt.pos.emulator.session

import com.bilt.pos.emulator.catalog.Product
import java.math.BigDecimal

/**
 * The emulator's tax policy: New Jersey sales tax, 6.625%, with the two big
 * NJ exemptions that map onto the mock catalog's categories — unprepared food
 * (Grocery) and clothing (Apparel) are not taxed.
 */
object NjSalesTax {

    private val RATE = BigDecimal("0.06625")
    private val EXEMPT_CATEGORIES = setOf("Grocery", "Apparel")

    /** Tax rate for [product] as the fraction `BasketItem.taxRate` expects, or null if exempt. */
    fun rateFor(product: Product): BigDecimal? =
        if (product.category in EXEMPT_CATEGORIES) null else RATE
}
