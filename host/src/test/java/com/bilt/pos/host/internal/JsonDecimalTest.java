package com.bilt.pos.host.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Request amounts are bounded so a tiny string cannot ask the SDK to expand a huge exponent. */
class JsonDecimalTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static JsonNode body(String json) throws Exception {
    return MAPPER.readTree(json);
  }

  @Test
  void ordinaryAmountsAndRatesPass() throws Exception {
    assertEquals(new BigDecimal("89.50"), Json.decimal(body("{\"a\":\"89.50\"}"), "a"));
    assertEquals(new BigDecimal("0.0825"), Json.decimal(body("{\"a\":0.0825}"), "a"));
    assertEquals(new BigDecimal("1E+3"), Json.decimal(body("{\"a\":\"1E+3\"}"), "a"));
    assertEquals(new BigDecimal("-12.00"), Json.decimal(body("{\"a\":\"-12.00\"}"), "a"));
  }

  @Test
  void hugeExponentsAreRefusedAsStringsAndNumbers() throws Exception {
    for (String json :
        new String[] {
          "{\"a\":\"1E+99999999\"}",
          "{\"a\":\"0E+99999999\"}",
          "{\"a\":\"1E-99999999\"}",
          "{\"a\":1E+99999999}",
          "{\"a\":\"1234567890123456789\"}"
        }) {
      JsonNode node = body(json);
      assertEquals(
          400, assertThrows(HostError.class, () -> Json.decimal(node, "a"), json).status(), json);
    }
  }
}
