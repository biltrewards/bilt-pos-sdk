package com.bilt.pos.emulator

import com.bilt.pos.emulator.catalog.CustomItem
import com.bilt.pos.emulator.catalog.MockProductProvider
import com.bilt.pos.emulator.catalog.Product
import com.bilt.pos.emulator.session.NjSalesTax
import com.bilt.pos.emulator.session.TlsVerifier
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmulatorLogicTest {

    @Test
    fun priceDecimalFormatsMinorUnits() {
        assertEquals("0.75", Product("S", "Gum", 75, "Grocery").priceDecimal)
        assertEquals("1.05", Product("S", "Water", 105, "Grocery").priceDecimal)
        assertEquals("549.99", Product("S", "TV", 54999, "Electronics").priceDecimal)
        assertEquals("5.00", Product("S", "Flat", 500, "Misc").priceDecimal)
    }

    @Test
    fun mockCatalogSpansSubDollarToFiveHundredPlus() {
        val products = MockProductProvider.products()
        assertTrue(products.size >= 20, "expected at least 20 products, got ${products.size}")
        assertTrue(products.any { it.priceMinor < 100 }, "expected a sub-dollar product")
        assertTrue(products.any { it.priceMinor > 50_000 }, "expected a $500+ product")
        assertEquals(products.size, products.map { it.sku }.toSet().size, "SKUs must be unique")
    }

    @Test
    fun njSalesTaxExemptsGroceryAndApparel() {
        assertNull(NjSalesTax.rateFor(Product("S", "Banana", 35, "Grocery")))
        assertNull(NjSalesTax.rateFor(Product("S", "T-Shirt", 2499, "Apparel")))
        assertEquals(
            BigDecimal("0.06625"),
            NjSalesTax.rateFor(Product("S", "Tablet", 32999, "Electronics")),
        )
        assertEquals(
            BigDecimal("0.06625"),
            NjSalesTax.rateFor(Product("S", "Desk Lamp", 3499, "Home")),
        )
    }

    @Test
    fun customSkusCountUpPastTheHighestInUse() {
        val cart = "3f9c2a7e-5d41-4b8a-9c16-8e2f70d1b634"
        assertEquals("CUSTOM-3f9c2a7e-1", CustomItem.nextSku(cart, emptyList()))
        assertEquals(
            "CUSTOM-3f9c2a7e-1",
            CustomItem.nextSku(cart, listOf("SKU-0001", "SKU-0002")),
        )
        assertEquals(
            "CUSTOM-3f9c2a7e-4",
            CustomItem.nextSku(
                cart,
                listOf("CUSTOM-3f9c2a7e-1", "SKU-0001", "CUSTOM-3f9c2a7e-3"),
            ),
            "a removed -2 must not be handed out again while -3 lives",
        )
    }

    /**
     * A keypad SKU outlives its basket: a return is rung under the SKU its
     * sale was recorded with, and the basket keys return lines by SKU. Two
     * baskets must therefore never mint the same one, or returning both
     * sales into one basket refuses the second (same SKU, different price)
     * or merges two sales into one credit line (same SKU, same price).
     */
    @Test
    fun customSkusDifferBetweenBaskets() {
        val first = CustomItem.nextSku("3f9c2a7e-5d41-4b8a-9c16-8e2f70d1b634", emptyList())
        val second = CustomItem.nextSku("9d1f4c2b-7a53-4e08-b1d9-2c6e91f0a487", emptyList())
        assertNotEquals(second, first, "two baskets minted the same keypad SKU")
        assertTrue(CustomItem.isCustomSku(first) && CustomItem.isCustomSku(second))
    }

    @Test
    fun customSkusCarryNoSeparatorsFromTheBasketId() {
        // the id is a UUID today, but a SKU travels to the terminal and
        // onto receipts — only the prefix's own dashes may appear
        val sku = CustomItem.nextSku("3f9c-2a7e-5d41", emptyList())
        assertEquals("CUSTOM-3f9c2a7e-1", sku)
    }

    @Test
    fun onlyCustomSkusAreEditable() {
        assertTrue(CustomItem.isCustomSku("CUSTOM-2"))
        assertFalse(CustomItem.isCustomSku("SKU-0002"))
    }

    @Test
    fun keypadAccumulatesDigitsAsCents() {
        val entry = KeypadEntry()
        listOf(1, 2, 3).forEach(entry::append)
        assertEquals(123L, entry.minor)
        entry.backspace()
        assertEquals(12L, entry.minor)
    }

    @Test
    fun keypadStopsAtItsCeilingInsteadOfWrapping() {
        val entry = KeypadEntry()
        repeat(12) { entry.append(9) }
        assertEquals(99_999_999L, entry.minor)
    }

    @Test
    fun keypadAdoptsAndReleasesAnEditedLine() {
        val entry = KeypadEntry()
        entry.edit("CUSTOM-1", 2400L)
        assertEquals("CUSTOM-1", entry.editingSku)
        assertEquals(2400L, entry.minor)
        // a released entry composes a new custom item from zero rather
        // than re-pricing the one it had adopted
        entry.reset()
        assertNull(entry.editingSku)
        assertEquals(0L, entry.minor)
    }
}
