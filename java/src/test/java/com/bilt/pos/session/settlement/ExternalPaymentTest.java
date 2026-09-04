package com.bilt.pos.session.settlement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalPaymentTest {

    @Test
    void monetaryEqualityIgnoresScale() {
        ExternalPayment first = ExternalPayment.cash(new BigDecimal("25.00"), "DRAWER-42");
        ExternalPayment second = ExternalPayment.cash(new BigDecimal("25.0"), "DRAWER-42");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());

        Set<ExternalPayment> payments = new HashSet<>();
        payments.add(first);
        assertTrue(payments.contains(second));
    }
}
