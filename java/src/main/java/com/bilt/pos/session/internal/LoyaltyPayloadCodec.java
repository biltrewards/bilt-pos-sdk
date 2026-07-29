/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Internal API — subject to change without notice.
 */
package com.bilt.pos.session.internal;

import com.bilt.pos.session.identity.Reward;
import com.bilt.pos.session.identity.RewardType;
import com.bilt.pos.session.payment.EarnedReward;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Codec for the custom Bilt loyalty JSON riding Base64-encoded in Nexo
 * {@code Response.AdditionalResponse} and {@code SaleData.SaleToPOIData}
 * fields.
 *
 * <p>Parsing is lenient: a malformed payload yields empty data and a WARN
 * log, never an exception — loyalty metadata must not break a checkout.</p>
 */
public final class LoyaltyPayloadCodec {

    private static final Logger LOGGER = Logger.getLogger(LoyaltyPayloadCodec.class.getName());

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private LoyaltyPayloadCodec() {
    }

    /**
     * Parses the {@code {"rewards":[...],"rewardCount":n}} payload of an
     * identification response. Returns an empty list when the payload is
     * missing or malformed.
     */
    public static List<Reward> parseRewards(String base64) {
        JsonNode root = decode(base64);
        if (root == null || !root.has("rewards")) {
            return Collections.emptyList();
        }
        List<Reward> rewards = new ArrayList<>();
        for (JsonNode node : root.get("rewards")) {
            rewards.add(new Reward(
                    text(node, "rewardRef"),
                    RewardType.fromWire(text(node, "type")),
                    text(node, "name"),
                    instant(text(node, "expirationDate"))));
        }
        return rewards;
    }

    /**
     * Parses the earned-rewards list an award response may carry in its
     * {@code AdditionalResponse} payload — {@code {"earnedRewards":[...]}},
     * with the identification payload's {@code {"rewards":[...]}} accepted
     * as a fallback shape. Returns an empty list when the payload is
     * missing or malformed.
     */
    public static List<EarnedReward> parseEarnedRewards(String base64) {
        JsonNode root = decode(base64);
        JsonNode list = root == null ? null
                : root.has("earnedRewards") ? root.get("earnedRewards") : root.get("rewards");
        if (list == null) {
            return Collections.emptyList();
        }
        List<EarnedReward> earned = new ArrayList<>();
        for (JsonNode node : list) {
            String description = text(node, "description");
            if (description == null) {
                description = text(node, "name");
            }
            earned.add(new EarnedReward(
                    RewardType.fromWire(text(node, "type")),
                    description,
                    node.path("quantity").asInt(1),
                    text(node, "rewardRef")));
        }
        return earned;
    }

    /**
     * The {@code rewardRefs} payload for a member's rewards. A redemption
     * and its refund must carry the same refs — both derive them here.
     */
    public static String memberRewardRefs(List<Reward> rewards) {
        List<String> refs = new ArrayList<>();
        for (Reward reward : rewards) {
            if (reward.getRewardRef() != null) {
                refs.add(reward.getRewardRef());
            }
        }
        return encodeRewardRefs(refs);
    }

    /** Encodes {@code {"rewardRefs":[...]}} for redemption requests. */
    public static String encodeRewardRefs(List<String> rewardRefs) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode refs = root.putArray("rewardRefs");
        rewardRefs.forEach(refs::add);
        return encode(root);
    }

    /** Encodes {@code {"redeemedRewardRefs":[...]}} for award requests. */
    public static String encodeRedeemedRewardRefs(List<String> rewardRefs) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode refs = root.putArray("redeemedRewardRefs");
        rewardRefs.forEach(refs::add);
        return encode(root);
    }

    /**
     * Parses an arbitrary Base64 JSON object into a flat map. Nested values
     * are kept as their JSON text. Returns an empty map when missing or
     * malformed.
     */
    public static Map<String, String> parseFields(String base64) {
        JsonNode root = decode(base64);
        if (root == null || !root.isObject()) {
            return Collections.emptyMap();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        root.fields().forEachRemaining(entry -> fields.put(entry.getKey(),
                entry.getValue().isValueNode()
                        ? entry.getValue().asText() : entry.getValue().toString()));
        return fields;
    }

    /** Reads an integer field from a Base64 JSON object, or the default. */
    public static int parseIntField(String base64, String field, int defaultValue) {
        JsonNode root = decode(base64);
        if (root == null || !root.has(field) || !root.get(field).canConvertToInt()) {
            return defaultValue;
        }
        return root.get(field).asInt();
    }

    private static JsonNode decode(String base64) {
        if (base64 == null || base64.isEmpty() || !plausiblyBase64(base64)) {
            // AdditionalResponse also legitimately carries the form-encoded
            // convention (promotionalMessage=..., currentBalance=...); that
            // shape is not an error and must not attempt a decode, which
            // would log a warning on every response that uses it
            return null;
        }
        try {
            byte[] json = Base64.getDecoder().decode(base64);
            return MAPPER.readTree(json);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "unparseable loyalty payload", e);
            return null;
        }
    }

    /** Base64 alphabet throughout, with {@code =} only as trailing padding. */
    private static boolean plausiblyBase64(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean base64Char = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/';
            if (!base64Char && !(c == '=' && i >= value.length() - 2)) {
                return false;
            }
        }
        return true;
    }

    private static String encode(ObjectNode node) {
        return Base64.getEncoder().encodeToString(
                node.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
