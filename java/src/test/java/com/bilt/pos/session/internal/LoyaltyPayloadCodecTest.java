package com.bilt.pos.session.internal;

import static org.junit.jupiter.api.Assertions.*;

import com.bilt.pos.session.identity.ShopperContext;
import com.bilt.pos.session.identity.ShopperKind;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class LoyaltyPayloadCodecTest {

  private static String base64(String json) {
    return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void shopperContextMemberParsesEveryField() {
    String json =
        "{\"shopperContext\":{\"shopper\":{"
            + "\"kind\":\"MEMBER\",\"walletIdentityId\":\"wallet-1\","
            + "\"neighborhoodId\":\"nbh-7\",\"firstName\":\"Aloy\",\"lastName\":\"Pelta\","
            + "\"pointsBalance\":42000,\"tierName\":\"Gold\"}}}";

    ShopperContext context = LoyaltyPayloadCodec.parseShopperContext(base64(json));

    assertNotNull(context);
    assertEquals(ShopperKind.MEMBER, context.getKind());
    assertEquals("wallet-1", context.getWalletIdentityId());
    assertEquals("nbh-7", context.getNeighborhoodId());
    assertEquals("Aloy", context.getFirstName());
    assertEquals("Pelta", context.getLastName());
    assertEquals(42000, context.getPointsBalance());
    assertEquals("Gold", context.getTierName());
  }

  @Test
  void shopperContextAnonymousWithSparseFieldsLeavesThemNull() {
    String json =
        "{\"shopperContext\":{\"shopper\":{"
            + "\"kind\":\"ANONYMOUS\",\"walletIdentityId\":\"wallet-2\"}}}";

    ShopperContext context = LoyaltyPayloadCodec.parseShopperContext(base64(json));

    assertNotNull(context);
    assertEquals(ShopperKind.ANONYMOUS, context.getKind());
    assertEquals("wallet-2", context.getWalletIdentityId());
    assertNull(context.getNeighborhoodId());
    assertNull(context.getFirstName());
    assertNull(context.getLastName());
    assertNull(context.getPointsBalance());
    assertNull(context.getTierName());
  }

  @Test
  void payloadWithoutShopperContextYieldsNull() {
    assertNull(LoyaltyPayloadCodec.parseShopperContext(base64("{\"rewards\":[],\"vas\":{}}")));
    assertNull(LoyaltyPayloadCodec.parseShopperContext((String) null));
    assertNull(LoyaltyPayloadCodec.parseShopperContext(""));
    assertNull(LoyaltyPayloadCodec.parseShopperContext(base64("{}")));
  }

  @Test
  void malformedShopperContextYieldsNull() {
    assertNull(LoyaltyPayloadCodec.parseShopperContext(base64("{\"shopperContext\":\"hi\"}")));
    assertNull(
        LoyaltyPayloadCodec.parseShopperContext(base64("{\"shopperContext\":{\"shopper\":[]}}")));
    // Base64 that does not decode to JSON
    assertNull(LoyaltyPayloadCodec.parseShopperContext("aGVsbG8gd29ybGQ="));
  }

  @Test
  void malformedPointsBalanceIsIgnoredButOtherFieldsParse() {
    String json =
        "{\"shopperContext\":{\"shopper\":{"
            + "\"kind\":\"MEMBER\",\"firstName\":\"Aloy\",\"pointsBalance\":\"lots\"}}}";

    ShopperContext context = LoyaltyPayloadCodec.parseShopperContext(base64(json));

    assertNotNull(context);
    assertEquals(ShopperKind.MEMBER, context.getKind());
    assertEquals("Aloy", context.getFirstName());
    assertNull(context.getPointsBalance());
  }

  @Test
  void unknownKindBecomesUnknown() {
    String json = "{\"shopperContext\":{\"shopper\":{\"kind\":\"GUEST\"}}}";

    ShopperContext context = LoyaltyPayloadCodec.parseShopperContext(base64(json));

    assertNotNull(context);
    assertEquals(ShopperKind.UNKNOWN, context.getKind());
  }
}
