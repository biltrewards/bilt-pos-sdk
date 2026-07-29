package com.bilt.pos.emulator

import com.bilt.pos.emulator.catalog.MockProductProvider
import com.bilt.pos.emulator.catalog.Product
import com.bilt.pos.emulator.session.TlsVerifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun hostnamePatternMatchesSingleWildcardLabel() {
        assertTrue(TlsVerifier.matchesPattern("V400m-123.pos.staging.bilt.dev", "*.pos.staging.bilt.dev"))
        assertTrue(TlsVerifier.matchesPattern("V400M-123.POS.STAGING.BILT.DEV", "*.pos.staging.bilt.dev"))
        assertFalse(TlsVerifier.matchesPattern("a.b.pos.staging.bilt.dev", "*.pos.staging.bilt.dev"))
        assertFalse(TlsVerifier.matchesPattern("pos.staging.bilt.dev", "*.pos.staging.bilt.dev"))
        assertFalse(TlsVerifier.matchesPattern("V400m-123.live.pos.bilt.com", "*.pos.staging.bilt.dev"))
        assertTrue(TlsVerifier.matchesPattern("exact.host", "exact.host"))
        assertFalse(TlsVerifier.matchesPattern("other.host", "exact.host"))
    }
}
