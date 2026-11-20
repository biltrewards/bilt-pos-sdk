package com.bilt.pos.session.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bilt.pos.receipt.ReceiptType;
import com.bilt.pos.session.Receipt;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CardPaymentResultTest {

  @Test
  void builderKeepsEveryValue() {
    Instant timestamp = Instant.parse("2026-07-20T10:00:00Z");
    Receipt customer = Receipt.from(new ReceiptType());
    Receipt merchant = Receipt.from(new ReceiptType());

    CardPaymentResult payment =
        CardPaymentResult.builder()
            .amount(new BigDecimal("20.00"))
            .poiTransactionId("POI-1")
            .poiTransactionTimestamp(timestamp)
            .approvalCode("APPR01")
            .acquirerTransactionId("ACQ-1")
            .paymentBrand("Visa")
            .maskedPan("411111******1111")
            .customerReceipt(customer)
            .merchantReceipt(merchant)
            .partialApproval(true)
            .build();

    assertEquals(new BigDecimal("20.00"), payment.getAmount());
    assertEquals("POI-1", payment.getPoiTransactionId());
    assertEquals(timestamp, payment.getPoiTransactionTimestamp());
    assertEquals("APPR01", payment.getApprovalCode());
    assertEquals("ACQ-1", payment.getAcquirerTransactionId());
    assertEquals("Visa", payment.getPaymentBrand());
    assertEquals("411111******1111", payment.getMaskedPan());
    assertSame(customer, payment.getCustomerReceipt());
    assertSame(merchant, payment.getMerchantReceipt());
    assertTrue(payment.isPartialApproval());
  }

  @Test
  void onlyTheAmountIsRequired() {
    CardPaymentResult payment = CardPaymentResult.builder().amount(BigDecimal.ONE).build();

    assertNull(payment.getPoiTransactionId());
    assertNull(payment.getMaskedPan());
    assertFalse(payment.isPartialApproval());
    assertThrows(NullPointerException.class, () -> CardPaymentResult.builder().build());
  }
}
