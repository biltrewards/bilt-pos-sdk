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

import com.bilt.pos.nexo.model.DocumentQualifierEnum;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.PaymentTypeEnum;
import com.bilt.pos.nexo.model.StoredValueAccountTypeEnum;
import com.bilt.pos.session.CheckoutPhase;
import com.bilt.pos.session.PrintPayload;
import com.bilt.pos.session.TransactionStatusOptions;
import com.bilt.pos.session.basket.BasketDiscount;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.basket.BasketItemType;
import com.bilt.pos.session.basket.BasketMutation;
import com.bilt.pos.session.identity.CardAcquisitionOptions;
import com.bilt.pos.session.identity.ForceEntryMode;
import com.bilt.pos.session.identity.IdentifyOptions;
import com.bilt.pos.session.identity.Member;
import com.bilt.pos.session.identity.MemberIdResolver;
import com.bilt.pos.session.input.ConfirmationOptions;
import com.bilt.pos.session.input.InputOptions;
import com.bilt.pos.session.input.MenuOptions;
import com.bilt.pos.session.input.PinOptions;
import com.bilt.pos.session.settlement.ExternalPayment;
import com.bilt.pos.session.settlement.OriginalSaleRecord;
import com.bilt.pos.session.settlement.RefundAllocation;
import com.bilt.pos.session.settlement.RefundAllocationType;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.bilt.pos.session.settlement.SettlementType;
import com.bilt.pos.session.settlement.StoredValueLoad;
import com.bilt.pos.session.settlement.StoredValueLoadRecord;
import com.bilt.pos.session.storedvalue.StoredValueCard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Request bodies turned into the SDK's option and value objects. Each parser validates what the
 * SDK would reject anyway and reports it as a 400 before any work starts, so a malformed basket
 * line never reaches the session.
 */
public final class Parsers {

  private Parsers() {}

  // ─── Basket ───

  public static BasketItem basketItem(JsonNode node) {
    if (node == null || !node.isObject()) {
      throw HostError.badRequest("item must be an object");
    }
    BasketItem.Builder builder =
        BasketItem.builder()
            .reference(Json.text(node, "reference"))
            .sku(Json.requireText(node, "sku"))
            .description(Json.requireText(node, "description"))
            .unitPrice(Json.requireDecimal(node, "unitPrice"))
            .category(Json.text(node, "category"))
            .taxRate(Json.decimal(node, "taxRate"))
            .taxAmount(Json.decimal(node, "taxAmount"));
    Integer quantity = Json.integer(node, "quantity");
    if (quantity != null) {
      builder.quantity(quantity);
    }
    BasketItemType type = Json.enumValue(node, "type", BasketItemType.class);
    if (type != null) {
      builder.type(type);
    }
    Map<String, String> metadata = Json.stringMap(node, "metadata");
    if (metadata != null) {
      builder.metadata(metadata);
    }
    ArrayNode discounts = Json.array(node, "discounts");
    if (discounts != null) {
      builder.discounts(discounts(discounts));
    }
    try {
      return builder.build();
    } catch (IllegalArgumentException | NullPointerException e) {
      throw HostError.badRequest("invalid basket item: " + e.getMessage());
    }
  }

  public static List<BasketItem> basketItems(ArrayNode array) {
    List<BasketItem> items = new ArrayList<>();
    for (JsonNode node : array) {
      items.add(basketItem(node));
    }
    return items;
  }

  public static List<BasketDiscount> discounts(ArrayNode array) {
    List<BasketDiscount> discounts = new ArrayList<>();
    for (JsonNode node : array) {
      String label = Json.requireText(node, "label");
      BigDecimal amount = Json.requireDecimal(node, "amount");
      String reference = Json.text(node, "reference");
      try {
        discounts.add(
            reference == null
                ? BasketDiscount.manual(label, amount)
                : BasketDiscount.offer(reference, label, amount));
      } catch (IllegalArgumentException e) {
        throw HostError.badRequest("invalid discount: " + e.getMessage());
      }
    }
    return discounts;
  }

  /**
   * One entry of {@code POST .../basket/mutations}: {@code { op, ...arguments }} with the op named
   * after the {@code BasketMutation} method it calls.
   */
  public static Consumer<BasketMutation> mutation(JsonNode node) {
    String op = Json.requireText(node, "op");
    switch (op) {
      case "addItem":
        {
          BasketItem item = basketItem(node.get("item"));
          String itemId = Json.text(node, "itemId");
          return itemId == null ? m -> m.addItem(item) : m -> m.addItem(item, itemId);
        }
      case "removeItem":
        {
          String itemId = Json.requireText(node, "itemId");
          return m -> m.removeItem(itemId);
        }
      case "removeItemBySku":
        {
          String sku = Json.requireText(node, "sku");
          return m -> m.removeItemBySku(sku);
        }
      case "updateItemQuantity":
        {
          String itemId = Json.requireText(node, "itemId");
          int quantity = requireInt(node, "quantity");
          return m -> m.updateItemQuantity(itemId, quantity);
        }
      case "updateItemQuantityBySku":
        {
          String sku = Json.requireText(node, "sku");
          int quantity = requireInt(node, "quantity");
          return m -> m.updateItemQuantityBySku(sku, quantity);
        }
      case "setDiscounts":
        {
          String itemId = Json.requireText(node, "itemId");
          List<BasketDiscount> discounts = discounts(requireArray(node, "discounts"));
          return m -> m.setDiscounts(itemId, discounts);
        }
      case "setDiscountsBySku":
        {
          String sku = Json.requireText(node, "sku");
          List<BasketDiscount> discounts = discounts(requireArray(node, "discounts"));
          return m -> m.setDiscountsBySku(sku, discounts);
        }
      case "setTaxRate":
        {
          String itemId = Json.requireText(node, "itemId");
          BigDecimal rate = Json.requireDecimal(node, "rate");
          return m -> m.setTaxRate(itemId, rate);
        }
      case "setTaxRateBySku":
        {
          String sku = Json.requireText(node, "sku");
          BigDecimal rate = Json.requireDecimal(node, "rate");
          return m -> m.setTaxRateBySku(sku, rate);
        }
      case "setTaxAmount":
        {
          String itemId = Json.requireText(node, "itemId");
          BigDecimal amount = Json.requireDecimal(node, "amount");
          return m -> m.setTaxAmount(itemId, amount);
        }
      case "setTaxAmountBySku":
        {
          String sku = Json.requireText(node, "sku");
          BigDecimal amount = Json.requireDecimal(node, "amount");
          return m -> m.setTaxAmountBySku(sku, amount);
        }
      case "setTaxTotal":
        {
          BigDecimal amount = Json.decimal(node, "amount");
          return m -> m.setTaxTotal(amount);
        }
      default:
        throw HostError.badRequest("unknown basket mutation op '" + op + "'");
    }
  }

  /** {@code PATCH .../basket/items/{itemId}}: quantity, discounts, tax rate or amount. */
  public static Consumer<BasketMutation> itemPatch(String itemId, JsonNode node) {
    Integer quantity = Json.integer(node, "quantity");
    ArrayNode discountNodes = Json.array(node, "discounts");
    List<BasketDiscount> discounts = discountNodes == null ? null : discounts(discountNodes);
    BigDecimal taxRate = Json.decimal(node, "taxRate");
    BigDecimal taxAmount = Json.decimal(node, "taxAmount");
    if (quantity == null && discounts == null && taxRate == null && taxAmount == null) {
      throw HostError.badRequest(
          "the patch must set at least one of quantity, discounts, taxRate or taxAmount");
    }
    return m -> {
      if (quantity != null) {
        m.updateItemQuantity(itemId, quantity);
      }
      if (discounts != null) {
        m.setDiscounts(itemId, discounts);
      }
      if (taxRate != null) {
        m.setTaxRate(itemId, taxRate);
      }
      if (taxAmount != null) {
        m.setTaxAmount(itemId, taxAmount);
      }
    };
  }

  // ─── Member ───

  /** {@code { id }} for a resolved member or {@code { resolver: {...} }} for one to look up. */
  public static Member member(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (!node.isObject()) {
      throw HostError.badRequest("member must be an object");
    }
    String id = Json.text(node, "id");
    JsonNode resolver = node.get("resolver");
    if (id != null && resolver != null && !resolver.isNull()) {
      throw HostError.badRequest("member takes either id or resolver, not both");
    }
    if (id != null) {
      try {
        return Member.id(id);
      } catch (IllegalArgumentException e) {
        throw HostError.badRequest(e.getMessage());
      }
    }
    if (resolver == null || resolver.isNull()) {
      throw HostError.badRequest("member requires id or resolver");
    }
    MemberIdResolver.Type type = Json.enumValue(resolver, "type", MemberIdResolver.Type.class);
    if (type == null) {
      throw HostError.badRequest("resolver.type is required");
    }
    String value = Json.requireText(resolver, "value");
    Member pending;
    try {
      switch (type) {
        case ACCOUNT_ID:
          pending = Member.idResolver().accountId(value);
          break;
        case PHONE:
          pending = Member.idResolver().phone(value);
          break;
        case EMAIL:
          pending = Member.idResolver().email(value);
          break;
        default:
          pending = Member.idResolver().custom(Json.requireText(resolver, "customType"), value);
      }
    } catch (IllegalArgumentException | NullPointerException e) {
      throw HostError.badRequest("invalid resolver: " + e.getMessage());
    }
    return Json.bool(resolver, "keyedByCashier", false) ? pending.keyedByCashier() : pending;
  }

  public static CheckoutPhase phase(JsonNode node) {
    return Json.enumValue(node, "phase", CheckoutPhase.class);
  }

  // ─── Identity and input options ───

  public static IdentifyOptions identifyOptions(JsonNode node) {
    if (node == null) {
      return IdentifyOptions.defaults();
    }
    IdentifyOptions.Builder builder = IdentifyOptions.builder();
    List<String> modes = Json.strings(node, "forceEntryModes");
    if (modes != null) {
      builder.forceEntryModes(forceEntryModes(modes));
    }
    List<String> brands = Json.strings(node, "allowedLoyaltyBrands");
    if (brands != null) {
      brands.forEach(builder::allowedLoyaltyBrand);
    }
    Boolean requireMember = Json.bool(node, "requireMember");
    if (requireMember != null) {
      builder.requireMember(requireMember);
    }
    builder.timeout(Json.millis(node, "timeoutMs"));
    return builder.build();
  }

  public static CardAcquisitionOptions cardAcquisitionOptions(JsonNode node) {
    if (node == null) {
      return CardAcquisitionOptions.defaults();
    }
    CardAcquisitionOptions.Builder builder = CardAcquisitionOptions.builder();
    List<String> modes = Json.strings(node, "forceEntryModes");
    if (modes != null) {
      builder.forceEntryModes(forceEntryModes(modes));
    }
    builder.paymentType(Json.enumValue(node, "paymentType", PaymentTypeEnum.class));
    builder.timeout(Json.millis(node, "timeoutMs"));
    return builder.build();
  }

  private static List<ForceEntryMode> forceEntryModes(List<String> names) {
    List<ForceEntryMode> modes = new ArrayList<>();
    for (String name : names) {
      modes.add(Json.enumValue(name, "forceEntryModes", ForceEntryMode.class));
    }
    return modes;
  }

  public static InputOptions inputOptions(JsonNode node) {
    if (node == null) {
      return InputOptions.defaults();
    }
    return InputOptions.builder()
        .maxLength(Json.integer(node, "maxLength"))
        .minLength(Json.integer(node, "minLength"))
        .timeout(Json.millis(node, "timeoutMs"))
        .additionalText(Json.text(node, "additionalText"))
        .additionalText2(Json.text(node, "additionalText2"))
        .build();
  }

  public static ConfirmationOptions confirmationOptions(JsonNode node) {
    if (node == null) {
      return ConfirmationOptions.defaults();
    }
    String confirm = Json.text(node, "confirmButton");
    String cancel = Json.text(node, "cancelButton");
    ConfirmationOptions options;
    if (confirm == null && cancel == null) {
      options = ConfirmationOptions.defaults();
    } else if (confirm == null) {
      throw HostError.badRequest("cancelButton requires confirmButton");
    } else {
      options =
          cancel == null
              ? ConfirmationOptions.withButtons(confirm)
              : ConfirmationOptions.withButtons(confirm, cancel);
    }
    Duration timeout = Json.millis(node, "timeoutMs");
    return timeout == null ? options : options.withTimeout(timeout);
  }

  public static MenuOptions menuOptions(JsonNode node) {
    if (node == null) {
      return MenuOptions.defaults();
    }
    return MenuOptions.builder()
        .multiSelect(Json.bool(node, "multiSelect", false))
        .additionalText(Json.text(node, "additionalText"))
        .timeout(Json.millis(node, "timeoutMs"))
        .build();
  }

  public static PinOptions pinOptions(JsonNode node) {
    if (node == null) {
      return PinOptions.defaults();
    }
    return PinOptions.builder()
        .timeout(Json.millis(node, "timeoutMs"))
        .keyReference(Json.text(node, "keyReference"))
        .pinVerificationMethod(Json.text(node, "pinVerificationMethod"))
        .build();
  }

  // ─── Stored value ───

  /** {@code { number }}, {@code { barcode }} or {@code { swiped: true }}, plus provider details. */
  public static StoredValueCard storedValueCard(JsonNode node, String field) {
    if (node == null || node.isNull()) {
      throw HostError.badRequest(field + " is required");
    }
    if (!node.isObject()) {
      throw HostError.badRequest(field + " must be an object");
    }
    String number = Json.text(node, "number");
    String barcode = Json.text(node, "barcode");
    StoredValueCard card;
    if (number != null) {
      card = StoredValueCard.number(number);
    } else if (barcode != null) {
      card = StoredValueCard.scanned(barcode);
    } else if (Json.bool(node, "swiped", false)) {
      card = StoredValueCard.swiped();
    } else {
      throw HostError.badRequest(field + " needs number, barcode or swiped");
    }
    String provider = Json.text(node, "provider");
    if (provider != null) {
      card = card.withProvider(provider);
    }
    StoredValueAccountTypeEnum accountType =
        Json.enumValue(node, "accountType", StoredValueAccountTypeEnum.class);
    if (accountType != null) {
      card = card.withAccountType(accountType);
    }
    String expiry = Json.text(node, "expiryDate");
    return expiry == null ? card : card.withExpiryDate(expiry);
  }

  // ─── Settlement and reversal ───

  public static SettlementOptions settlementOptions(JsonNode node) {
    if (node == null) {
      return SettlementOptions.defaults();
    }
    SettlementOptions.Builder builder =
        SettlementOptions.builder()
            .disableRebates(Json.bool(node, "disableRebates", false))
            .disablePoints(Json.bool(node, "disablePoints", false))
            .disableAward(Json.bool(node, "disableAward", false));
    BigDecimal cashback = Json.decimal(node, "cashback");
    if (cashback != null) {
      try {
        builder.cashback(cashback);
      } catch (IllegalArgumentException e) {
        throw HostError.badRequest(e.getMessage());
      }
    }
    SettlementType type = Json.enumValue(node, "settlementType", SettlementType.class);
    if (type != null) {
      builder.settlementType(type);
    }
    ArrayNode refunds = Json.array(node, "refunds");
    if (refunds != null) {
      for (JsonNode refund : refunds) {
        builder.addRefund(refundAllocation(refund));
      }
    }
    ArrayNode fulfillments = Json.array(node, "fulfillments");
    if (fulfillments != null) {
      for (JsonNode fulfillment : fulfillments) {
        builder.addFulfillment(storedValueLoad(fulfillment));
      }
    }
    return builder.build();
  }

  public static RefundAllocation refundAllocation(JsonNode node) {
    RefundAllocationType type = Json.enumValue(node, "type", RefundAllocationType.class);
    if (type == null) {
      throw HostError.badRequest("refund allocation type is required");
    }
    RefundAllocation.Builder builder =
        RefundAllocation.builder()
            .type(type)
            .amount(Json.decimal(node, "amount"))
            .originalPoiTransactionId(Json.text(node, "originalPoiTransactionId"))
            .originalPoiTransactionTimestamp(Json.instant(node, "originalPoiTransactionTimestamp"))
            .memberId(Json.text(node, "memberId"));
    if (Json.has(node, "card")) {
      builder.storedValueCard(storedValueCard(node.get("card"), "card"));
    }
    try {
      return builder.build();
    } catch (IllegalArgumentException | NullPointerException | IllegalStateException e) {
      throw HostError.badRequest("invalid refund allocation: " + e.getMessage());
    }
  }

  private static StoredValueLoad storedValueLoad(JsonNode node) {
    StoredValueLoad.Type type = Json.enumValue(node, "type", StoredValueLoad.Type.class);
    if (type == null) {
      throw HostError.badRequest("fulfillment type is required");
    }
    String reference = Json.requireText(node, "basketReference");
    StoredValueCard card = storedValueCard(node.get("card"), "card");
    return type == StoredValueLoad.Type.ACTIVATE
        ? StoredValueLoad.activate(reference, card)
        : StoredValueLoad.reload(reference, card);
  }

  public static OriginalSaleRecord originalSale(JsonNode node) {
    if (node == null || !node.isObject()) {
      throw HostError.badRequest("originalSale must be an object");
    }
    OriginalSaleRecord.Builder builder =
        OriginalSaleRecord.builder()
            .cardPoiTransactionId(Json.text(node, "cardPoiTransactionId"))
            .cardPoiTransactionTimestamp(Json.instant(node, "cardPoiTransactionTimestamp"))
            .storedValuePoiTransactionId(Json.text(node, "storedValuePoiTransactionId"))
            .storedValuePoiTransactionTimestamp(
                Json.instant(node, "storedValuePoiTransactionTimestamp"))
            .rebatePoiTransactionId(Json.text(node, "rebatePoiTransactionId"))
            .rebatePoiTransactionTimestamp(Json.instant(node, "rebatePoiTransactionTimestamp"))
            .redemptionPoiTransactionId(Json.text(node, "redemptionPoiTransactionId"))
            .redemptionPoiTransactionTimestamp(
                Json.instant(node, "redemptionPoiTransactionTimestamp"))
            .awardPoiTransactionId(Json.text(node, "awardPoiTransactionId"))
            .awardPoiTransactionTimestamp(Json.instant(node, "awardPoiTransactionTimestamp"))
            .memberId(Json.text(node, "memberId"));
    ArrayNode loads = Json.array(node, "storedValueLoads");
    if (loads != null) {
      for (JsonNode load : loads) {
        builder.addStoredValueLoad(
            StoredValueLoadRecord.builder()
                .basketReference(Json.text(load, "basketReference"))
                .amount(Json.decimal(load, "amount"))
                .poiTransactionId(Json.text(load, "poiTransactionId"))
                .poiTransactionTimestamp(Json.instant(load, "poiTransactionTimestamp"))
                .build());
      }
    }
    return builder.build();
  }

  public static ExternalPayment externalPayment(JsonNode node) {
    if (node == null || !node.isObject()) {
      throw HostError.badRequest("external must be an object");
    }
    BigDecimal amount = Json.requireDecimal(node, "amount");
    String tenderType = Json.text(node, "tenderType");
    String reference = Json.text(node, "reference");
    try {
      return tenderType == null
          ? ExternalPayment.cash(amount, reference)
          : ExternalPayment.of(tenderType, amount, reference);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw HostError.badRequest("invalid external payment: " + e.getMessage());
    }
  }

  public static TransactionStatusOptions transactionStatusOptions(JsonNode node) {
    if (node == null) {
      return TransactionStatusOptions.defaults();
    }
    TransactionStatusOptions.Builder builder = TransactionStatusOptions.builder();
    MessageCategoryType category =
        Json.enumValue(node, "originalCategory", MessageCategoryType.class);
    if (category != null) {
      builder.originalCategory(category);
    }
    builder.receiptReprint(Json.bool(node, "receiptReprint", false));
    List<String> qualifiers = Json.strings(node, "documentQualifiers");
    if (qualifiers != null) {
      List<DocumentQualifierEnum> parsed = new ArrayList<>();
      for (String qualifier : qualifiers) {
        parsed.add(Json.enumValue(qualifier, "documentQualifiers", DocumentQualifierEnum.class));
      }
      builder.documentQualifiers(parsed);
    }
    return builder.build();
  }

  // ─── Device operations ───

  public static PrintPayload printPayload(JsonNode node) {
    String content = Json.requireText(node, "content");
    String format = Json.text(node, "format");
    if (format == null || format.equalsIgnoreCase("text")) {
      return PrintPayload.text(content);
    }
    if (format.equalsIgnoreCase("xhtml")) {
      return PrintPayload.xhtml(content);
    }
    throw HostError.badRequest("format must be text or xhtml");
  }

  // ─── Helpers ───

  private static int requireInt(JsonNode node, String field) {
    Integer value = Json.integer(node, field);
    if (value == null) {
      throw HostError.badRequest(field + " is required");
    }
    return value;
  }

  private static ArrayNode requireArray(JsonNode node, String field) {
    ArrayNode array = Json.array(node, field);
    if (array == null) {
      throw HostError.badRequest(field + " is required");
    }
    return array;
  }
}
