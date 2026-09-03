package com.bilt.pos.session.settlement;

import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.identity.RewardType;
import com.bilt.pos.session.payment.EarnedReward;
import com.bilt.pos.session.payment.RedeemedRebate;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SettlementResultTest {

    @Test
    void toBuilderPreservesValuesAndCopiesMutableLists() {
        Instant timestamp = Instant.parse("2026-07-20T10:00:00Z");
        Basket basket = Basket.builder()
                .cartId("cart-1")
                .grandTotal(new BigDecimal("120.00"))
                .build();
        RedeemedRebate rebate = new RedeemedRebate("1", "SKU-1",
                new BigDecimal("5.00"), "rebate", "promo-1");
        EarnedReward reward = new EarnedReward(RewardType.REWARD, "reward", 1,
                "reward-1");
        SettlementMovement movement = SettlementMovement.builder()
                .step(SettlementStep.CARD_CHARGE)
                .amount(new BigDecimal("95.00"))
                .saleTransactionId("sale-txn-1")
                .poiTransactionId("poi-txn-1")
                .poiTransactionTimestamp(timestamp)
                .build();
        SettlementMovement refundMovement = SettlementMovement.builder()
                .step(SettlementStep.CARD_REFUND)
                .amount(new BigDecimal("10.00"))
                .saleTransactionId("sale-txn-refund")
                .poiTransactionId("poi-txn-refund")
                .poiTransactionTimestamp(timestamp.plusSeconds(1))
                .build();

        List<RedeemedRebate> rebates = new ArrayList<>(List.of(rebate));
        List<EarnedReward> rewards = new ArrayList<>(List.of(reward));
        List<String> promotions = new ArrayList<>(List.of("promo"));
        SettlementResult original = SettlementResult.builder()
                .success(true)
                .finalBasket(basket)
                .authorizedAmount(new BigDecimal("100.00"))
                .storedValueAmountUsed(new BigDecimal("5.00"))
                .cardAmountCharged(new BigDecimal("95.00"))
                .externalPaymentAmount(new BigDecimal("20.00"))
                .approvalCode("approval")
                .acquirerTransactionId("acquirer")
                .paymentBrand("visa")
                .redeemedRebates(rebates)
                .totalRebateAmount(new BigDecimal("5.00"))
                .pointsRedeemed(200)
                .pointsMonetaryValue(new BigDecimal("2.00"))
                .earnedRewards(rewards)
                .totalPointsEarned(10)
                .pointsBalance(500)
                .promotionMessages(promotions)
                .poiTransactionId("poi-txn-1")
                .poiTransactionTimestamp(timestamp)
                .storedValuePoiTransactionId("sv-txn-1")
                .storedValuePoiTransactionTimestamp(timestamp.plusSeconds(2))
                .awardPoiTransactionId("award-txn-1")
                .awardPoiTransactionTimestamp(timestamp.plusSeconds(3))
                .rebatePoiTransactionId("rebate-txn-1")
                .rebatePoiTransactionTimestamp(timestamp.plusSeconds(4))
                .redemptionPoiTransactionId("redemption-txn-1")
                .redemptionPoiTransactionTimestamp(timestamp.plusSeconds(5))
                .cardRefundedAmount(new BigDecimal("1.00"))
                .storedValueRefundedAmount(new BigDecimal("2.00"))
                .externalRefundedAmount(new BigDecimal("3.00"))
                .loyaltyRefundedAmount(new BigDecimal("4.00"))
                .movement(movement)
                .warning("warning")
                .build();

        rebates.clear();
        rewards.clear();
        promotions.clear();

        SettlementResult copy = original.toBuilder()
                .cardRefundedAmount(new BigDecimal("10.00"))
                .movement(refundMovement)
                .warning("second warning")
                .build();

        assertSame(basket, copy.getFinalBasket());
        assertEquals(new BigDecimal("100.00"), copy.getAuthorizedAmount());
        assertEquals(new BigDecimal("5.00"), copy.getStoredValueAmountUsed());
        assertEquals(new BigDecimal("95.00"), copy.getCardAmountCharged());
        assertEquals(new BigDecimal("20.00"), copy.getExternalPaymentAmount());
        assertEquals("approval", copy.getApprovalCode());
        assertEquals("acquirer", copy.getAcquirerTransactionId());
        assertEquals("visa", copy.getPaymentBrand());
        assertEquals(List.of(rebate), copy.getRedeemedRebates());
        assertEquals(new BigDecimal("5.00"), copy.getTotalRebateAmount());
        assertEquals(200, copy.getPointsRedeemed());
        assertEquals(new BigDecimal("2.00"), copy.getPointsMonetaryValue());
        assertEquals(List.of(reward), copy.getEarnedRewards());
        assertEquals(10, copy.getTotalPointsEarned());
        assertEquals(500, copy.getPointsBalance());
        assertEquals(List.of("promo"), copy.getPromotionMessages());
        assertEquals("poi-txn-1", copy.getPoiTransactionId());
        assertEquals(timestamp, copy.getPoiTransactionTimestamp());
        assertEquals("sv-txn-1", copy.getStoredValuePoiTransactionId());
        assertEquals(timestamp.plusSeconds(2), copy.getStoredValuePoiTransactionTimestamp());
        assertEquals("award-txn-1", copy.getAwardPoiTransactionId());
        assertEquals(timestamp.plusSeconds(3), copy.getAwardPoiTransactionTimestamp());
        assertEquals("rebate-txn-1", copy.getRebatePoiTransactionId());
        assertEquals(timestamp.plusSeconds(4), copy.getRebatePoiTransactionTimestamp());
        assertEquals("redemption-txn-1", copy.getRedemptionPoiTransactionId());
        assertEquals(timestamp.plusSeconds(5), copy.getRedemptionPoiTransactionTimestamp());
        assertEquals(new BigDecimal("10.00"), copy.getCardRefundedAmount());
        assertEquals(new BigDecimal("2.00"), copy.getStoredValueRefundedAmount());
        assertEquals(new BigDecimal("3.00"), copy.getExternalRefundedAmount());
        assertEquals(new BigDecimal("4.00"), copy.getLoyaltyRefundedAmount());
        assertEquals(List.of(movement, refundMovement), copy.getMovements());
        assertEquals(List.of("warning", "second warning"), copy.getWarnings());
        assertEquals(List.of(movement), original.getMovements());
        assertEquals(List.of("warning"), original.getWarnings());
    }
}
