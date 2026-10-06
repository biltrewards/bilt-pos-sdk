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

import com.bilt.pos.display.DisplayPayload;
import com.bilt.pos.nexo.model.DocumentQualifierEnum;
import com.bilt.pos.nexo.model.EntryModeType;
import com.bilt.pos.nexo.model.IdentificationTypeEnum;
import com.bilt.pos.nexo.model.MessageCategoryType;
import com.bilt.pos.nexo.model.PaymentTypeEnum;
import com.bilt.pos.nexo.model.StoredValueAccountTypeEnum;
import com.bilt.pos.nexo.model.TransactionIdentificationType;
import com.bilt.pos.session.CheckoutPhase;
import com.bilt.pos.session.PrintPayload;
import com.bilt.pos.session.TransactionStatusOptions;
import com.bilt.pos.session.basket.Basket;
import com.bilt.pos.session.basket.BasketDiscount;
import com.bilt.pos.session.basket.BasketItem;
import com.bilt.pos.session.basket.BasketItemType;
import com.bilt.pos.session.basket.BasketLineItem;
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
import com.bilt.pos.session.settlement.SettlementRecovery;
import com.bilt.pos.session.settlement.SettlementType;
import com.bilt.pos.session.settlement.StoredValueLoad;
import com.bilt.pos.session.settlement.StoredValueLoadRecord;
import com.bilt.pos.session.storedvalue.StoredValueCard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Request bodies turned into the SDK's option and value objects, following the protocol's component
 * schemas. Each parser validates what the SDK would reject anyway and reports it as a 400 before
 * any work starts, so a malformed basket line never reaches the session.
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

  /** A whole {@code Basket} snapshot, for {@code PUT .../basket} and {@code updateDisplay}. */
  public static Basket basket(JsonNode node) {
    if (node == null || !node.isObject()) {
      throw HostError.badRequest("snapshot must be a Basket object");
    }
    ArrayNode items = Json.array(node, "items");
    if (items == null) {
      throw HostError.badRequest("snapshot.items is required");
    }
    List<BasketLineItem> lines = new ArrayList<>();
    for (JsonNode item : items) {
      BasketLineItem.Builder line =
          BasketLineItem.builder()
              .itemId(Json.text(item, "itemId"))
              .reference(Json.text(item, "reference"))
              .sku(Json.requireText(item, "sku"))
              .description(Json.text(item, "description"))
              .category(Json.text(item, "category"))
              .unitPrice(Json.requireDecimal(item, "unitPrice"))
              .subtotal(Json.decimal(item, "subtotal"))
              .originalTotal(Json.decimal(item, "originalTotal"))
              .rebateLabel(Json.text(item, "rebateLabel"))
              .adjustedTotal(Json.decimal(item, "adjustedTotal"))
              .taxRate(Json.decimal(item, "taxRate"))
              .taxAmount(Json.decimal(item, "taxAmount"));
      Integer quantity = Json.integer(item, "quantity");
      line.quantity(quantity == null ? 1 : quantity);
      BasketItemType type = Json.enumValue(item, "type", BasketItemType.class);
      line.type(type == null ? BasketItemType.SALE : type);
      ArrayNode discounts = Json.array(item, "discounts");
      if (discounts != null) {
        line.discounts(discounts(discounts));
      }
      BigDecimal discountTotal = Json.decimal(item, "discountTotal");
      line.discountTotal(discountTotal == null ? BigDecimal.ZERO : discountTotal);
      BigDecimal rebateAmount = Json.decimal(item, "rebateAmount");
      line.rebateAmount(rebateAmount == null ? BigDecimal.ZERO : rebateAmount);
      Map<String, String> metadata = Json.stringMap(item, "metadata");
      if (metadata != null) {
        line.metadata(metadata);
      }
      lines.add(line.build());
    }
    Basket.Builder builder = Basket.builder().items(lines).cartId(Json.text(node, "cartId"));
    JsonNode transaction = node.get("saleTransactionId");
    if (transaction != null && transaction.isObject()) {
      builder.saleTransactionID(
          TransactionIdentificationType.builder()
              .transactionID(Json.requireText(transaction, "transactionId"))
              .timeStamp(Json.text(transaction, "timestamp"))
              .build());
    }
    builder.taxTotal(Json.decimal(node, "taxTotal"));
    putTotal(node, "originalTotal", builder::originalTotal);
    putTotal(node, "discountTotal", builder::discountTotal);
    builder.subtotal(Json.decimal(node, "subtotal"));
    putTotal(node, "grandTotal", builder::grandTotal);
    putTotal(node, "rebateTotal", builder::rebateTotal);
    putTotal(node, "pointDiscountTotal", builder::pointDiscountTotal);
    putTotal(node, "storedValueTotal", builder::storedValueTotal);
    putTotal(node, "cardPaymentTotal", builder::cardPaymentTotal);
    putTotal(node, "externalPaymentTotal", builder::externalPaymentTotal);
    Instant updatedAt = Json.instant(node, "updatedAt");
    if (updatedAt != null) {
      builder.updatedAt(updatedAt);
    }
    return builder.build();
  }

  private static void putTotal(JsonNode node, String field, Consumer<BigDecimal> setter) {
    BigDecimal value = Json.decimal(node, field);
    if (value != null) {
      setter.accept(value);
    }
  }

  /**
   * One entry of {@code POST .../basket/mutations}, an {@code UPPER_SNAKE} op with its arguments.
   */
  public static Consumer<BasketMutation> mutation(JsonNode node) {
    String op = Json.requireText(node, "op");
    switch (op) {
      case "ADD_ITEM":
        {
          BasketItem item = basketItem(node.get("item"));
          String itemId = Json.text(node, "itemId");
          return itemId == null ? m -> m.addItem(item) : m -> m.addItem(item, itemId);
        }
      case "REMOVE_ITEM":
        return addressed(
            node, (m, itemId) -> m.removeItem(itemId), (m, sku) -> m.removeItemBySku(sku));
      case "UPDATE_ITEM_QUANTITY":
        {
          int quantity = requireInt(node, "quantity");
          if (quantity < 0) {
            throw HostError.badRequest("quantity must not be negative");
          }
          return quantity == 0
              ? addressed(
                  node, (m, itemId) -> m.removeItem(itemId), (m, sku) -> m.removeItemBySku(sku))
              : addressed(
                  node,
                  (m, itemId) -> m.updateItemQuantity(itemId, quantity),
                  (m, sku) -> m.updateItemQuantityBySku(sku, quantity));
        }
      case "SET_DISCOUNTS":
        {
          List<BasketDiscount> discounts = discounts(requireArray(node, "discounts"));
          return addressed(
              node,
              (m, itemId) -> m.setDiscounts(itemId, discounts),
              (m, sku) -> m.setDiscountsBySku(sku, discounts));
        }
      case "SET_TAX_RATE":
        {
          BigDecimal rate = Json.requireDecimal(node, "amount");
          return addressed(
              node,
              (m, itemId) -> m.setTaxRate(itemId, rate),
              (m, sku) -> m.setTaxRateBySku(sku, rate));
        }
      case "SET_TAX_AMOUNT":
        {
          BigDecimal amount = Json.requireDecimal(node, "amount");
          return addressed(
              node,
              (m, itemId) -> m.setTaxAmount(itemId, amount),
              (m, sku) -> m.setTaxAmountBySku(sku, amount));
        }
      case "SET_TAX_TOTAL":
        {
          BigDecimal amount = Json.decimal(node, "amount");
          return m -> m.setTaxTotal(amount);
        }
      default:
        throw HostError.badRequest("unknown basket mutation op '" + op + "'");
    }
  }

  private interface Addressed {
    void apply(BasketMutation mutation, String address);
  }

  private static Consumer<BasketMutation> addressed(
      JsonNode node, Addressed byId, Addressed bySku) {
    String itemId = Json.text(node, "itemId");
    String sku = Json.text(node, "sku");
    if ((itemId == null) == (sku == null)) {
      throw HostError.badRequest("exactly one of itemId or sku is required");
    }
    return itemId != null ? m -> byId.apply(m, itemId) : m -> bySku.apply(m, sku);
  }

  /**
   * {@code PATCH .../basket/items/{itemId}}: quantity (0 removes), discounts, tax rate or amount.
   */
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
    if (taxRate != null && taxAmount != null) {
      throw HostError.badRequest("taxRate and taxAmount are mutually exclusive");
    }
    if (quantity != null && quantity < 0) {
      throw HostError.badRequest("quantity must not be negative");
    }
    return m -> {
      if (quantity != null && quantity == 0) {
        m.removeItem(itemId);
        return;
      }
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

  /** {@code MemberInput}: {@code { id }} or {@code { resolver }}; {@code null} for no member. */
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
    return pendingMember(resolver);
  }

  /** A {@code MemberIdResolver} as a member pending resolution. */
  public static Member pendingMember(JsonNode resolver) {
    if (resolver == null || !resolver.isObject()) {
      throw HostError.badRequest("resolver must be an object");
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

  // ─── Durations ───

  /** An ISO 8601 duration field such as {@code PT30S}. */
  public static Duration duration(JsonNode node, String field) {
    String text = Json.text(node, field);
    if (text == null) {
      return null;
    }
    try {
      Duration duration = Duration.parse(text);
      if (duration.isNegative()) {
        throw HostError.badRequest(field + " must not be negative");
      }
      return duration;
    } catch (java.time.format.DateTimeParseException e) {
      throw HostError.badRequest(field + " is not an ISO 8601 duration: " + text);
    }
  }

  // ─── Identity and input options ───

  public static IdentifyOptions identifyOptions(JsonNode node) {
    if (node == null || node.isNull()) {
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
    builder.timeout(duration(node, "timeout"));
    return builder.build();
  }

  public static CardAcquisitionOptions cardAcquisitionOptions(JsonNode node) {
    if (node == null || node.isNull()) {
      return CardAcquisitionOptions.defaults();
    }
    CardAcquisitionOptions.Builder builder = CardAcquisitionOptions.builder();
    List<String> modes = Json.strings(node, "forceEntryModes");
    if (modes != null) {
      builder.forceEntryModes(forceEntryModes(modes));
    }
    builder.paymentType(Json.enumValue(node, "paymentType", PaymentTypeEnum.class));
    builder.timeout(duration(node, "timeout"));
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
    if (node == null || node.isNull()) {
      return InputOptions.defaults();
    }
    return InputOptions.builder()
        .maxLength(Json.integer(node, "maxLength"))
        .minLength(Json.integer(node, "minLength"))
        .timeout(duration(node, "timeout"))
        .additionalText(Json.text(node, "additionalText"))
        .additionalText2(Json.text(node, "additionalText2"))
        .build();
  }

  public static ConfirmationOptions confirmationOptions(JsonNode node) {
    if (node == null || node.isNull()) {
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
    Duration timeout = duration(node, "timeout");
    return timeout == null ? options : options.withTimeout(timeout);
  }

  public static MenuOptions menuOptions(JsonNode node) {
    if (node == null || node.isNull()) {
      return MenuOptions.defaults();
    }
    return MenuOptions.builder()
        .multiSelect(Json.bool(node, "multiSelect", false))
        .additionalText(Json.text(node, "additionalText"))
        .timeout(duration(node, "timeout"))
        .build();
  }

  public static PinOptions pinOptions(JsonNode node) {
    if (node == null || node.isNull()) {
      return PinOptions.defaults();
    }
    return PinOptions.builder()
        .timeout(duration(node, "timeout"))
        .keyReference(Json.text(node, "keyReference"))
        .pinVerificationMethod(Json.text(node, "pinVerificationMethod"))
        .build();
  }

  // ─── Stored value ───

  /**
   * The structured {@code StoredValueCard}: {@code identificationType} and {@code entryMode} pick
   * the SDK factory ({@code PAN/KEYED} is a typed number, {@code BAR_CODE/SCANNED} a barcode,
   * {@code PAN/MAG_STRIPE} without an id a swipe); the SDK offers no other combination.
   */
  public static StoredValueCard storedValueCard(JsonNode node, String field) {
    if (node == null || node.isNull()) {
      throw HostError.badRequest(field + " is required");
    }
    if (!node.isObject()) {
      throw HostError.badRequest(field + " must be an object");
    }
    IdentificationTypeEnum identification =
        Json.enumValue(node, "identificationType", IdentificationTypeEnum.class);
    EntryModeType entryMode = Json.enumValue(node, "entryMode", EntryModeType.class);
    String id = Json.text(node, "storedValueId");
    if (identification == null || entryMode == null) {
      throw HostError.badRequest(field + " needs identificationType and entryMode");
    }
    StoredValueCard card;
    if (identification == IdentificationTypeEnum.PAN && entryMode == EntryModeType.KEYED) {
      if (id == null) {
        throw HostError.badRequest(field + ".storedValueId is required for a keyed card");
      }
      card = StoredValueCard.number(id);
    } else if (identification == IdentificationTypeEnum.BAR_CODE
        && entryMode == EntryModeType.SCANNED) {
      if (id == null) {
        throw HostError.badRequest(field + ".storedValueId is required for a scanned card");
      }
      card = StoredValueCard.scanned(id);
    } else if (identification == IdentificationTypeEnum.PAN
        && entryMode == EntryModeType.MAG_STRIPE
        && id == null) {
      card = StoredValueCard.swiped();
    } else {
      throw HostError.badRequest(
          field
              + " must be PAN/KEYED with an id, BAR_CODE/SCANNED with an id, or PAN/MAG_STRIPE"
              + " without one");
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
    if (node == null || node.isNull()) {
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
    if (Json.has(node, "paymentProcessingDisplay")) {
      builder.paymentProcessingDisplay(
          displayPayload(node.get("paymentProcessingDisplay"), "paymentProcessingDisplay"));
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
    BigDecimal amount = Json.decimal(node, "amount");
    RefundAllocation.Builder builder =
        RefundAllocation.builder()
            .type(type)
            .amount(amount == null ? BigDecimal.ZERO : amount)
            .originalPoiTransactionId(Json.text(node, "originalPoiTransactionId"))
            .originalPoiTransactionTimestamp(Json.instant(node, "originalPoiTransactionTimestamp"))
            .memberId(Json.text(node, "memberId"));
    if (Json.has(node, "storedValueCard")) {
      builder.storedValueCard(storedValueCard(node.get("storedValueCard"), "storedValueCard"));
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

  /** {@code SettlementRecovery}: {@code { action, externalPayment? }}. */
  public static SettlementRecovery recovery(JsonNode node) {
    if (node == null || !node.isObject()) {
      throw HostError.badRequest("recovery must be an object with an action");
    }
    String action = Json.requireText(node, "action");
    switch (action.toUpperCase(java.util.Locale.ROOT)) {
      case "RETRY":
        return SettlementRecovery.retry();
      case "SKIP":
        return SettlementRecovery.skip();
      case "ABORT":
        return SettlementRecovery.abort();
      case "ABANDON":
        return SettlementRecovery.abandon();
      case "EXTERNAL":
        return SettlementRecovery.external(externalPayment(node.get("externalPayment")));
      default:
        throw HostError.badRequest(
            "recovery.action must be RETRY, SKIP, EXTERNAL, ABORT or ABANDON, not '"
                + action
                + "'");
    }
  }

  public static ExternalPayment externalPayment(JsonNode node) {
    if (node == null || !node.isObject()) {
      throw HostError.badRequest("externalPayment is required for EXTERNAL");
    }
    BigDecimal amount = Json.requireDecimal(node, "amount");
    String tenderType = Json.requireText(node, "tenderType");
    String reference = Json.text(node, "reference");
    try {
      return ExternalPayment.of(tenderType, amount, reference);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw HostError.badRequest("invalid external payment: " + e.getMessage());
    }
  }

  public static TransactionStatusOptions transactionStatusOptions(JsonNode node) {
    if (node == null || node.isNull()) {
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

  // ─── Display and device operations ───

  /**
   * The structured display model, read with Jackson's bean conventions from the same field names
   * the display schema uses.
   */
  public static DisplayPayload displayPayload(JsonNode node, String field) {
    if (node == null || node.isNull()) {
      throw HostError.badRequest(field + " is required");
    }
    try {
      DisplayPayload payload = Json.MAPPER.treeToValue(node, DisplayPayload.class);
      if (payload.getLayout() == null || payload.getLayout().isEmpty()) {
        throw HostError.badRequest(field + ".layout is required");
      }
      return payload;
    } catch (JsonProcessingException | IllegalArgumentException e) {
      throw HostError.badRequest(field + " is not a display payload: " + e.getMessage());
    }
  }

  public static PrintPayload printPayload(JsonNode node) {
    String content = Json.requireText(node, "content");
    String format = Json.requireText(node, "format");
    PrintPayload payload;
    if (format.equalsIgnoreCase("TEXT")) {
      payload = PrintPayload.text(content);
    } else if (format.equalsIgnoreCase("XHTML")) {
      payload = PrintPayload.xhtml(content);
    } else {
      throw HostError.badRequest("format must be TEXT or XHTML");
    }
    DocumentQualifierEnum qualifier =
        Json.enumValue(node, "documentQualifier", DocumentQualifierEnum.class);
    return qualifier == null ? payload : payload.withDocumentQualifier(qualifier);
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
