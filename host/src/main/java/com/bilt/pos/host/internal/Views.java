/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.host.internal;

import com.bilt.pos.host.TerminalInfo;
import com.bilt.pos.media.AdInteraction;
import com.bilt.pos.media.Offer;
import com.bilt.pos.session.DiagnosisResult;
import com.bilt.pos.session.Receipt;
import com.bilt.pos.session.ReconciliationResult;
import com.bilt.pos.session.RefundResult;
import com.bilt.pos.session.ReversedMovement;
import com.bilt.pos.session.SessionContextSnapshot;
import com.bilt.pos.session.SessionError;
import com.bilt.pos.session.TransactionStatusResult;
import com.bilt.pos.session.VoidResult;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketChange;
import com.bilt.pos.session.basket.BasketDiscount;
import com.bilt.pos.session.basket.BasketLineItem;
import com.bilt.pos.session.identity.CardAcquisitionResult;
import com.bilt.pos.session.identity.IdentifyResult;
import com.bilt.pos.session.identity.Member;
import com.bilt.pos.session.identity.MemberIdResolver;
import com.bilt.pos.session.identity.Reward;
import com.bilt.pos.session.input.MenuSelection;
import com.bilt.pos.session.input.PinResult;
import com.bilt.pos.session.input.Signature;
import com.bilt.pos.session.payment.EarnedReward;
import com.bilt.pos.session.payment.GiftCardPaymentResult;
import com.bilt.pos.session.payment.PointRedemptionResult;
import com.bilt.pos.session.payment.RebateRedemptionResult;
import com.bilt.pos.session.payment.RedeemedRebate;
import com.bilt.pos.session.settlement.AbandonedSettlementRecord;
import com.bilt.pos.session.settlement.CommittedStep;
import com.bilt.pos.session.settlement.RefundAllocation;
import com.bilt.pos.session.settlement.SettlementContext;
import com.bilt.pos.session.settlement.SettlementFailure;
import com.bilt.pos.session.settlement.SettlementMovement;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementResult;
import com.bilt.pos.session.settlement.StoredValueLoad;
import com.bilt.pos.session.settlement.StoredValueLoadRecord;
import com.bilt.pos.session.storedvalue.StoredValueBalance;
import com.bilt.pos.session.storedvalue.StoredValueCard;
import com.bilt.pos.session.storedvalue.StoredValueOperationResult;
import com.bilt.pos.widget.Cta;
import com.bilt.pos.widget.MediaSpec;
import com.bilt.pos.widget.Rendering;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * The SDK's value types in their wire form, as the Session Protocol's component schemas define
 * them. Money is a decimal string, instants are RFC 3339, durations ISO 8601, enums their Java
 * names. Anything the page has no business seeing — a raw PAN, an unmasked phone number — is left
 * out or masked here and nowhere else.
 */
public final class Views {

  private Views() {}

  // ─── Basket ───

  public static ObjectNode basket(Basket basket) {
    ObjectNode node = Json.object();
    Json.putText(node, "cartId", basket.getCartId());
    ObjectNode transaction = node.putObject("saleTransactionId");
    transaction.put("transactionId", basket.getSaleTransactionID().getTransactionID());
    transaction.put("timestamp", basket.getSaleTransactionID().getTimeStamp());
    ArrayNode items = node.putArray("items");
    for (BasketLineItem line : basket.getItems()) {
      items.add(line(line));
    }
    Json.putMoney(node, "taxTotal", basket.getTaxTotal());
    Json.putMoney(node, "originalTotal", basket.getOriginalTotal());
    Json.putMoney(node, "discountTotal", basket.getDiscountTotal());
    Json.putMoney(node, "subtotal", basket.getSubtotal());
    Json.putMoney(node, "grandTotal", basket.getGrandTotal());
    Json.putMoney(node, "rebateTotal", basket.getRebateTotal());
    Json.putMoney(node, "pointDiscountTotal", basket.getPointDiscountTotal());
    Json.putMoney(node, "storedValueTotal", basket.getStoredValueTotal());
    Json.putMoney(node, "cardPaymentTotal", basket.getCardPaymentTotal());
    Json.putMoney(node, "externalPaymentTotal", basket.getExternalPaymentTotal());
    Json.putInstant(node, "updatedAt", basket.getUpdatedAt());
    return node;
  }

  public static ObjectNode line(BasketLineItem line) {
    ObjectNode node = Json.object();
    node.put("itemId", line.getItemId());
    Json.putText(node, "reference", line.getReference());
    node.put("sku", line.getSku());
    node.put("description", line.getDescription() == null ? "" : line.getDescription());
    Json.putText(node, "category", line.getCategory());
    node.put("quantity", line.getQuantity());
    Json.putMoney(node, "unitPrice", line.getUnitPrice());
    node.set("discounts", discounts(line.getDiscounts()));
    Json.putMoney(node, "discountTotal", line.getDiscountTotal());
    Json.putMoney(node, "subtotal", line.getSubtotal());
    node.put("type", line.getType().name());
    Json.putMoney(node, "originalTotal", line.getOriginalTotal());
    Json.putMoney(node, "rebateAmount", line.getRebateAmount());
    Json.putText(node, "rebateLabel", line.getRebateLabel());
    Json.putMoney(node, "adjustedTotal", line.getAdjustedTotal());
    Json.putMoney(node, "taxRate", line.getTaxRate());
    Json.putMoney(node, "taxAmount", line.getTaxAmount());
    node.set("metadata", stringMap(line.getMetadata()));
    return node;
  }

  public static ArrayNode discounts(List<BasketDiscount> discounts) {
    ArrayNode array = Json.array();
    for (BasketDiscount discount : discounts) {
      ObjectNode node = array.addObject();
      Json.putText(node, "reference", discount.getReference());
      node.put("label", discount.getLabel());
      node.put("amount", Json.money(discount.getAmount()));
    }
    return array;
  }

  public static ObjectNode basketChange(BasketChange change) {
    ObjectNode node = Json.object();
    node.set("previous", basket(change.previous()));
    node.set("current", basket(change.current()));
    node.put("source", change.source().name());
    node.set("added", lines(change.added()));
    node.set("removed", lines(change.removed()));
    node.set("quantityChanged", lineChanges(change.quantityChanged()));
    node.set("priceChanged", lineChanges(change.priceChanged()));
    node.set("discountsChanged", lineChanges(change.discountsChanged()));
    node.set("taxChanged", lineChanges(change.taxChanged()));
    node.set("detailsChanged", lineChanges(change.detailsChanged()));
    node.put("taxTotalChanged", change.taxTotalChanged());
    return node;
  }

  private static ArrayNode lines(List<BasketLineItem> lines) {
    ArrayNode array = Json.array();
    for (BasketLineItem line : lines) {
      array.add(line(line));
    }
    return array;
  }

  private static ArrayNode lineChanges(List<BasketChange.LineChange> changes) {
    ArrayNode array = Json.array();
    for (BasketChange.LineChange change : changes) {
      ObjectNode node = array.addObject();
      node.set("before", line(change.before()));
      node.set("after", line(change.after()));
    }
    return array;
  }

  // ─── Member and context ───

  /** The member view, or JSON {@code null} when the session has none. */
  public static JsonNode member(Member member) {
    if (member == null) {
      return Json.nullNode();
    }
    ObjectNode node = Json.object();
    node.put("resolved", member.isResolved());
    if (member.isResolved()) {
      node.put("memberId", member.memberId());
      if (member.status() != null) {
        node.put("status", member.status().name());
      }
      Json.putText(node, "loyaltyBrand", member.loyaltyBrand());
    } else {
      node.set("resolver", resolver(member.resolver()));
    }
    node.set("rewards", rewards(member.rewards()));
    node.put("pointBalance", member.pointBalance());
    return node;
  }

  private static ObjectNode resolver(MemberIdResolver resolver) {
    ObjectNode node = Json.object();
    node.put("type", resolver.type().name());
    Json.putText(node, "customType", resolver.customType());
    node.put(
        "value",
        resolver.type() == MemberIdResolver.Type.ACCOUNT_ID
            ? resolver.value()
            : mask(resolver.value()));
    node.put("keyedByCashier", resolver.keyedByCashier());
    return node;
  }

  /** All but the last four characters, as the protocol masks identifiers. */
  static String mask(String value) {
    int visible = Math.min(4, value.length());
    StringBuilder masked = new StringBuilder(value.length());
    for (int i = 0; i < value.length() - visible; i++) {
      masked.append('*');
    }
    return masked.append(value, value.length() - visible, value.length()).toString();
  }

  public static ObjectNode identifyResult(IdentifyResult result) {
    ObjectNode node = Json.object();
    node.put("status", result.getStatus().name());
    Json.putText(node, "memberId", result.getMemberId());
    Json.putText(node, "loyaltyBrand", result.getLoyaltyBrand());
    node.set("rewards", rewards(result.getRewards()));
    node.put("pointBalance", result.getPointBalance());
    return node;
  }

  private static ArrayNode rewards(List<Reward> rewards) {
    ArrayNode array = Json.array();
    for (Reward reward : rewards) {
      ObjectNode node = array.addObject();
      node.put("rewardRef", reward.getRewardRef() == null ? "" : reward.getRewardRef());
      if (reward.getType() != null) {
        node.put("type", reward.getType().name());
      }
      Json.putText(node, "description", reward.getDescription());
      Json.putInstant(node, "expirationDate", reward.getExpirationDate());
    }
    return array;
  }

  public static ObjectNode context(SessionContextSnapshot context) {
    ObjectNode node = Json.object();
    node.put("phase", context.phase().name());
    node.set("attributes", stringMap(context.attributes()));
    Json.putText(node, "saleId", context.saleId());
    Json.putText(node, "currency", context.currency());
    Json.putText(node, "storeLocation", context.storeLocation());
    Json.putText(node, "poiId", context.poiId());
    return node;
  }

  private static ObjectNode stringMap(Map<String, String> map) {
    ObjectNode node = Json.object();
    map.forEach(node::put);
    return node;
  }

  // ─── Errors ───

  public static ObjectNode error(SessionError error) {
    ObjectNode node = Json.object();
    node.put("code", error.getCode().name());
    node.put("message", error.getMessage() == null ? error.getCode().name() : error.getMessage());
    Json.putText(node, "nexoErrorCondition", error.getNexoErrorCondition());
    if (!error.getReversedMovements().isEmpty()) {
      ArrayNode reversed = node.putArray("reversedMovements");
      for (ReversedMovement movement : error.getReversedMovements()) {
        ObjectNode m = reversed.addObject();
        m.put("step", movement.getStep().name());
        Json.putText(m, "poiTransactionId", movement.getPoiTransactionId());
      }
    }
    return node;
  }

  // ─── Settlement ───

  public static ObjectNode settlementResult(SettlementResult result) {
    ObjectNode node = Json.object();
    node.put("success", result.isSuccess());
    node.set("finalBasket", basket(result.getFinalBasket()));
    node.put("authorizedAmount", moneyOrZero(result.getAuthorizedAmount()));
    node.put("storedValueAmountUsed", moneyOrZero(result.getStoredValueAmountUsed()));
    node.put("storedValueLoadedAmount", moneyOrZero(result.getStoredValueLoadedAmount()));
    node.put("cardAmountCharged", moneyOrZero(result.getCardAmountCharged()));
    node.put("externalPaymentAmount", moneyOrZero(result.getExternalPaymentAmount()));
    Json.putText(node, "approvalCode", result.getApprovalCode());
    Json.putText(node, "acquirerTransactionId", result.getAcquirerTransactionId());
    Json.putText(node, "paymentBrand", result.getPaymentBrand());
    node.set("redeemedRebates", redeemedRebates(result.getRedeemedRebates()));
    node.put("totalRebateAmount", moneyOrZero(result.getTotalRebateAmount()));
    node.put("pointsRedeemed", result.getPointsRedeemed());
    node.put("pointsMonetaryValue", moneyOrZero(result.getPointsMonetaryValue()));
    ArrayNode earned = node.putArray("earnedRewards");
    for (EarnedReward reward : result.getEarnedRewards()) {
      ObjectNode r = earned.addObject();
      if (reward.getType() != null) {
        r.put("type", reward.getType().name());
      }
      Json.putText(r, "description", reward.getDescription());
      r.put("quantity", reward.getQuantity());
      Json.putText(r, "rewardRef", reward.getRewardRef());
    }
    node.put("totalPointsEarned", result.getTotalPointsEarned());
    node.put("pointsBalance", result.getPointsBalance());
    ArrayNode messages = node.putArray("promotionMessages");
    result.getPromotionMessages().forEach(messages::add);
    putReceipt(node, "customerReceipt", result.getCustomerReceipt());
    putReceipt(node, "merchantReceipt", result.getMerchantReceipt());
    Json.putText(node, "poiTransactionId", result.getPoiTransactionId());
    Json.putInstant(node, "poiTransactionTimestamp", result.getPoiTransactionTimestamp());
    Json.putText(node, "storedValuePoiTransactionId", result.getStoredValuePoiTransactionId());
    Json.putInstant(
        node, "storedValuePoiTransactionTimestamp", result.getStoredValuePoiTransactionTimestamp());
    Json.putText(node, "awardPoiTransactionId", result.getAwardPoiTransactionId());
    Json.putInstant(node, "awardPoiTransactionTimestamp", result.getAwardPoiTransactionTimestamp());
    Json.putText(node, "rebatePoiTransactionId", result.getRebatePoiTransactionId());
    Json.putInstant(
        node, "rebatePoiTransactionTimestamp", result.getRebatePoiTransactionTimestamp());
    Json.putText(node, "redemptionPoiTransactionId", result.getRedemptionPoiTransactionId());
    Json.putInstant(
        node, "redemptionPoiTransactionTimestamp", result.getRedemptionPoiTransactionTimestamp());
    node.put("cardRefundedAmount", moneyOrZero(result.getCardRefundedAmount()));
    node.put("storedValueRefundedAmount", moneyOrZero(result.getStoredValueRefundedAmount()));
    node.put("externalRefundedAmount", moneyOrZero(result.getExternalRefundedAmount()));
    node.put("loyaltyRefundedAmount", moneyOrZero(result.getLoyaltyRefundedAmount()));
    node.set("movements", movements(result.getMovements()));
    ArrayNode warnings = node.putArray("warnings");
    result.getWarnings().forEach(warnings::add);
    return node;
  }

  private static String moneyOrZero(java.math.BigDecimal amount) {
    return amount == null ? "0" : amount.toPlainString();
  }

  private static ArrayNode redeemedRebates(List<RedeemedRebate> rebates) {
    ArrayNode array = Json.array();
    for (RedeemedRebate rebate : rebates) {
      ObjectNode node = array.addObject();
      Json.putText(node, "itemId", rebate.getItemId());
      Json.putText(node, "sku", rebate.getSku());
      node.put("amount", moneyOrZero(rebate.getAmount()));
      Json.putText(node, "label", rebate.getLabel());
      Json.putText(node, "promotionRef", rebate.getPromotionRef());
    }
    return array;
  }

  public static ArrayNode movements(List<SettlementMovement> movements) {
    ArrayNode array = Json.array();
    for (SettlementMovement movement : movements) {
      array.add(movement(movement));
    }
    return array;
  }

  public static ObjectNode movement(SettlementMovement movement) {
    ObjectNode node = Json.object();
    node.put("step", movement.getStep().name());
    ObjectNode target = node.putObject("target");
    if (movement.getTarget() != null) {
      target.put("type", movement.getTarget().getType().name());
      Json.putText(target, "basketReference", movement.getTarget().getBasketReference());
    } else {
      target.put("type", "SALES");
    }
    node.put("amount", moneyOrZero(movement.getAmount()));
    Json.putText(node, "saleTransactionId", movement.getSaleTransactionId());
    Json.putText(node, "poiTransactionId", movement.getPoiTransactionId());
    Json.putInstant(node, "poiTransactionTimestamp", movement.getPoiTransactionTimestamp());
    Json.putText(node, "memberId", movement.getMemberId());
    if (movement.getPoints() != null) {
      node.put("points", movement.getPoints());
    }
    if (movement.getPointBalance() != null) {
      node.put("pointBalance", movement.getPointBalance());
    }
    Json.putText(node, "externalTenderType", movement.getExternalTenderType());
    Json.putText(node, "externalReference", movement.getExternalReference());
    return node;
  }

  public static ObjectNode settlementFailure(SettlementFailure failure) {
    ObjectNode node = Json.object();
    if (failure.getStep() != null) {
      node.put("step", failure.getStep().name());
    }
    node.set("error", error(failure.getError()));
    node.put("amountDue", moneyOrZero(failure.getAmountDue()));
    node.set("committedMovements", movements(failure.getCommittedMovements()));
    node.put("outcomeCertainty", failure.getOutcomeCertainty().name());
    Json.putText(node, "messageCategory", failure.getMessageCategory());
    Json.putText(node, "serviceId", failure.getServiceId());
    return node;
  }

  public static ObjectNode settlementOptions(SettlementOptions options) {
    ObjectNode node = Json.object();
    node.put("disableRebates", options.isDisableRebates());
    node.put("disablePoints", options.isDisablePoints());
    node.put("disableAward", options.isDisableAward());
    Json.putMoney(node, "cashback", options.getCashback());
    if (options.getPaymentProcessingDisplay() != null) {
      node.set(
          "paymentProcessingDisplay",
          Json.MAPPER.valueToTree(options.getPaymentProcessingDisplay()));
    }
    ArrayNode refunds = node.putArray("refunds");
    for (RefundAllocation allocation : options.getRefunds()) {
      ObjectNode r = refunds.addObject();
      r.put("type", allocation.getType().name());
      r.put("amount", moneyOrZero(allocation.getAmount()));
      Json.putText(r, "originalPoiTransactionId", allocation.getOriginalPoiTransactionId());
      Json.putInstant(
          r, "originalPoiTransactionTimestamp", allocation.getOriginalPoiTransactionTimestamp());
      if (allocation.getStoredValueCard() != null) {
        r.set("storedValueCard", storedValueCard(allocation.getStoredValueCard()));
      }
      Json.putText(r, "memberId", allocation.getMemberId());
    }
    ArrayNode fulfillments = node.putArray("fulfillments");
    for (StoredValueLoad load : options.getFulfillments()) {
      ObjectNode f = fulfillments.addObject();
      f.put("basketReference", load.getBasketReference());
      f.put("type", load.getType().name());
      f.set("card", storedValueCard(load.getCard()));
    }
    node.put("settlementType", options.getSettlementType().name());
    return node;
  }

  public static ObjectNode storedValueCard(StoredValueCard card) {
    ObjectNode node = Json.object();
    Json.putText(node, "storedValueId", card.getStoredValueId());
    if (card.getIdentificationType() != null) {
      node.put("identificationType", card.getIdentificationType().name());
    }
    if (card.getEntryMode() != null) {
      node.put("entryMode", card.getEntryMode().name());
    }
    if (card.getAccountType() != null) {
      node.put("accountType", card.getAccountType().name());
    }
    Json.putText(node, "provider", card.getProvider());
    Json.putText(node, "expiryDate", card.getExpiryDate());
    return node;
  }

  public static ObjectNode abandonedSettlement(AbandonedSettlementRecord record) {
    ObjectNode node = Json.object();
    node.put("settlementId", record.getSettlementId());
    node.put("abandonedAt", record.getAbandonedAt().toString());
    node.set("basket", basket(record.getBasket()));
    node.set("options", settlementOptions(record.getOptions()));
    Json.putText(node, "memberId", record.getMemberId());
    node.set("failure", settlementFailure(record.getFailure()));
    node.put("outstandingAmount", moneyOrZero(record.getOutstandingAmount()));
    node.set("committedMovements", movements(record.getCommittedMovements()));
    return node;
  }

  public static ObjectNode settlementContext(SettlementContext context) {
    ObjectNode node = Json.object();
    node.put("step", context.getStep().name());
    node.set("currentBasket", basket(context.getCurrentBasket()));
    node.put("currentTotal", moneyOrZero(context.getCurrentTotal()));
    node.put("defaultTransactionId", context.getDefaultTransactionId());
    ArrayNode prior = node.putArray("priorSteps");
    for (CommittedStep step : context.getPriorSteps()) {
      ObjectNode s = prior.addObject();
      s.put("step", step.getStep().name());
      Json.putText(s, "saleTransactionId", step.getSaleTransactionId());
      Json.putText(s, "poiTransactionId", step.getPoiTransactionId());
      Json.putInstant(s, "poiTransactionTimestamp", step.getPoiTransactionTimestamp());
      s.put("success", step.isSuccess());
    }
    return node;
  }

  public static ObjectNode rebatesRedeemed(RebateRedemptionResult result) {
    ObjectNode node = Json.object();
    node.set("rebates", redeemedRebates(result.getRebates()));
    node.put("totalRebateAmount", moneyOrZero(result.getTotalRebateAmount()));
    node.put("previousTotal", moneyOrZero(result.getPreviousTotal()));
    node.put("suggestedTotal", moneyOrZero(result.getSuggestedTotal()));
    node.set("updatedBasket", basket(result.getUpdatedBasket()));
    return node;
  }

  public static ObjectNode pointsRedeemed(PointRedemptionResult result) {
    ObjectNode node = Json.object();
    node.put("pointsUsed", result.getPointsUsed());
    node.put("monetaryValue", moneyOrZero(result.getMonetaryValue()));
    node.put("previousTotal", moneyOrZero(result.getPreviousTotal()));
    node.put("suggestedTotal", moneyOrZero(result.getSuggestedTotal()));
    node.put("remainingPointBalance", result.getRemainingPointBalance());
    return node;
  }

  public static ObjectNode giftCardPayment(GiftCardPaymentResult result) {
    ObjectNode node = Json.object();
    node.put("amountCharged", moneyOrZero(result.getAmountCharged()));
    Json.putMoney(node, "remainingCardBalance", result.getRemainingCardBalance());
    node.put("previousTotal", moneyOrZero(result.getPreviousTotal()));
    node.put("suggestedTotal", moneyOrZero(result.getSuggestedTotal()));
    return node;
  }

  // ─── Reversals and other results ───

  public static ObjectNode refundResult(RefundResult result) {
    ObjectNode node = Json.object();
    node.put("success", result.isSuccess());
    Json.putMoney(node, "refundedAmount", result.getRefundedAmount());
    Json.putText(node, "approvalCode", result.getApprovalCode());
    Json.putText(node, "poiTransactionId", result.getPoiTransactionId());
    Json.putInstant(node, "poiTransactionTimestamp", result.getPoiTransactionTimestamp());
    putReceipt(node, "customerReceipt", result.getCustomerReceipt());
    putReceipt(node, "merchantReceipt", result.getMerchantReceipt());
    node.put("pointsReversed", result.getPointsReversed());
    node.put("remainingPointBalance", result.getRemainingPointBalance());
    return node;
  }

  public static ObjectNode voidResult(VoidResult result) {
    ObjectNode node = Json.object();
    node.put("success", result.isSuccess());
    Json.putMoney(node, "reversedAmount", result.getReversedAmount());
    Json.putText(node, "poiTransactionId", result.getPoiTransactionId());
    Json.putInstant(node, "poiTransactionTimestamp", result.getPoiTransactionTimestamp());
    putReceipt(node, "customerReceipt", result.getCustomerReceipt());
    putReceipt(node, "merchantReceipt", result.getMerchantReceipt());
    node.put("pointsReversed", result.getPointsReversed());
    node.put("remainingPointBalance", result.getRemainingPointBalance());
    return node;
  }

  private static void putReceipt(ObjectNode node, String field, Receipt receipt) {
    if (receipt == null) {
      return;
    }
    ObjectNode r = node.putObject(field);
    Json.putText(r, "html", receipt.getHtml());
    Json.putText(r, "plainText", receipt.getPlainText());
    putNexo(r, "receiptData", receipt.getReceiptData());
  }

  public static ObjectNode storedValueOperation(StoredValueOperationResult result) {
    ObjectNode node = Json.object();
    if (result.getTransactionType() != null) {
      node.put("transactionType", result.getTransactionType().name());
    }
    Json.putMoney(node, "amount", result.getAmount());
    Json.putMoney(node, "currentBalance", result.getCurrentBalance());
    Json.putText(node, "currency", result.getCurrency());
    Json.putText(node, "poiTransactionId", result.getPoiTransactionId());
    Json.putInstant(node, "poiTransactionTimestamp", result.getPoiTransactionTimestamp());
    Json.putText(node, "hostTransactionId", result.getHostTransactionId());
    return node;
  }

  public static ObjectNode storedValueBalance(StoredValueBalance balance) {
    ObjectNode node = Json.object();
    node.put("balance", moneyOrZero(balance.getBalance()));
    Json.putText(node, "currency", balance.getCurrency());
    return node;
  }

  /** The acquired card without its raw PAN, which never leaves the host. */
  public static ObjectNode cardAcquisition(CardAcquisitionResult result) {
    ObjectNode node = Json.object();
    Json.putText(node, "maskedPan", result.getMaskedPan());
    Json.putText(node, "truncatedPan", result.getTruncatedPan());
    Json.putText(node, "paymentBrand", result.getPaymentBrand());
    if (result.getEntryMode() != null) {
      node.put("entryMode", result.getEntryMode().name());
    }
    Json.putText(node, "cardToken", result.getCardToken());
    Json.putText(node, "expiryDate", result.getExpiryDate());
    node.set("additionalData", stringMap(result.getAdditionalData()));
    return node;
  }

  public static JsonNode text(String value) {
    return value == null ? Json.nullNode() : Json.MAPPER.getNodeFactory().textNode(value);
  }

  public static JsonNode money(java.math.BigDecimal value) {
    return value == null
        ? Json.nullNode()
        : Json.MAPPER.getNodeFactory().textNode(value.toPlainString());
  }

  public static JsonNode bool(Boolean value) {
    return Json.MAPPER.getNodeFactory().booleanNode(Boolean.TRUE.equals(value));
  }

  public static ObjectNode menuSelection(MenuSelection selection) {
    ObjectNode node = Json.object();
    ArrayNode indices = node.putArray("indices");
    selection.getIndices().forEach(indices::add);
    ArrayNode values = node.putArray("values");
    selection.getValues().forEach(values::add);
    return node;
  }

  public static ObjectNode signature(Signature signature) {
    ObjectNode node = Json.object();
    node.put(
        "imageData",
        signature.getImageData() == null
            ? ""
            : Base64.getEncoder().encodeToString(signature.getImageData()));
    node.put("format", signature.getFormat() == null ? "" : signature.getFormat());
    node.put("width", signature.getWidth());
    node.put("height", signature.getHeight());
    Json.putInstant(node, "capturedAt", signature.getCapturedAt());
    return node;
  }

  public static ObjectNode pinResult(PinResult result) {
    ObjectNode node = Json.object();
    node.put("mode", result.getMode() == null ? "PIN_ENTER" : result.getMode().name());
    node.put("verified", result.isVerified());
    putNexo(node, "cardholderPin", result.getCardholderPin());
    return node;
  }

  public static ObjectNode transactionStatus(TransactionStatusResult result) {
    ObjectNode node = Json.object();
    node.put("found", result.isFound());
    Json.putText(node, "messageCategory", result.getMessageCategory());
    putNexo(node, "paymentResponse", result.getPaymentResponse());
    putNexo(node, "loyaltyResponse", result.getLoyaltyResponse());
    putNexo(node, "storedValueResponse", result.getStoredValueResponse());
    putNexo(node, "reversalResponse", result.getReversalResponse());
    return node;
  }

  public static ObjectNode diagnosis(DiagnosisResult result) {
    ObjectNode node = Json.object();
    putNexo(node, "poiStatus", result.getPoiStatus());
    ArrayNode hosts = node.putArray("hostStatuses");
    if (result.getHostStatuses() != null) {
      result.getHostStatuses().forEach(status -> hosts.add(Json.MAPPER.valueToTree(status)));
    }
    return node;
  }

  public static ObjectNode reconciliation(ReconciliationResult result) {
    ObjectNode node = Json.object();
    Json.putText(node, "poiReconciliationId", result.getPoiReconciliationId());
    ArrayNode totals = node.putArray("transactionTotals");
    if (result.getTransactionTotals() != null) {
      result.getTransactionTotals().forEach(total -> totals.add(Json.MAPPER.valueToTree(total)));
    }
    return node;
  }

  /** Nexo structures the SDK's own results carry cross as the SDK's JSON model, unchanged. */
  private static void putNexo(ObjectNode node, String field, Object model) {
    if (model != null) {
      node.set(field, Json.MAPPER.valueToTree(model));
    }
  }

  public static ObjectNode terminal(TerminalInfo terminal) {
    ObjectNode node = Json.object();
    node.put("poiId", terminal.poiId());
    Json.putText(node, "model", terminal.model());
    if (terminal.reachable() != null) {
      node.put("reachable", terminal.reachable());
    }
    return node;
  }

  // ─── Widgets ───

  public static ObjectNode rendering(Rendering rendering) {
    ObjectNode node = Json.object();
    node.put("creativeId", rendering.getCreativeId());
    node.put("placement", rendering.getPlacement());
    node.set("media", media(rendering.getMedia()));
    node.put("headline", rendering.getHeadline());
    Json.putText(node, "body", rendering.getBody());
    if (rendering.getCta() != null) {
      node.set("cta", cta(rendering.getCta()));
    }
    if (rendering.getSecondary() != null) {
      node.set("secondary", cta(rendering.getSecondary()));
    }
    node.put("ttl", rendering.getTtl().toString());
    ObjectNode tracking = node.putObject("tracking");
    for (Map.Entry<String, URI> beacon : rendering.getTracking().entrySet()) {
      tracking.put(beacon.getKey(), beacon.getValue().toString());
    }
    return node;
  }

  private static ObjectNode media(MediaSpec media) {
    ObjectNode node = Json.object();
    node.put("type", media.getType().name());
    node.put("url", media.getUrl().toString());
    if (media.getDuration() != null) {
      node.put("duration", media.getDuration().toString());
    }
    if (media.getPoster() != null) {
      node.put("poster", media.getPoster().toString());
    }
    return node;
  }

  private static ObjectNode cta(Cta cta) {
    ObjectNode node = Json.object();
    node.put("label", cta.getLabel());
    node.put("action", cta.getAction().name());
    node.put("token", cta.getToken());
    return node;
  }

  public static ObjectNode offer(Offer offer) {
    ObjectNode node = Json.object();
    node.put("id", offer.getId());
    node.put("scope", offer.getScope().name());
    Json.putText(node, "sku", offer.getSku());
    Json.putMoney(node, "amount", offer.getAmount());
    Json.putMoney(node, "percentage", offer.getPercentage());
    Json.putInstant(node, "expiry", offer.getExpiry());
    node.put("creativeId", offer.getCreativeId());
    return node;
  }

  public static ObjectNode interaction(AdInteraction interaction) {
    ObjectNode node = Json.object();
    node.put("creativeId", interaction.getCreativeId());
    node.put("placement", interaction.getPlacement().getId());
    node.put("kind", interaction.getKind().name());
    node.put("timestamp", interaction.getTimestamp().toString());
    if (interaction.getAction() != null) {
      node.put("action", interaction.getAction().name());
    }
    return node;
  }

  /** The {@code StoredValueLoadRecord} rows of a settlement result or original sale. */
  static ArrayNode storedValueLoads(List<StoredValueLoadRecord> loads) {
    ArrayNode array = Json.array();
    for (StoredValueLoadRecord load : loads) {
      ObjectNode l = array.addObject();
      Json.putText(l, "basketReference", load.getBasketReference());
      l.put("amount", moneyOrZero(load.getAmount()));
      Json.putText(l, "poiTransactionId", load.getPoiTransactionId());
      Json.putInstant(l, "poiTransactionTimestamp", load.getPoiTransactionTimestamp());
    }
    return array;
  }
}
