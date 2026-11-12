package com.bilt.pos.host.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bilt.pos.session.settlement.SettlementLoyalty;
import com.bilt.pos.session.settlement.SettlementOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class SettlementLoyaltyJsonTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static SettlementLoyalty parse(String options) throws Exception {
    return Parsers.settlementOptions(MAPPER.readTree(options)).getLoyalty();
  }

  @Test
  void omittedLoyaltyIsExplicitWithEveryStep() throws Exception {
    assertEquals(SettlementLoyalty.explicit(), parse("{}"));
    assertEquals(SettlementLoyalty.explicit(), parse("{\"loyalty\":{\"handling\":\"EXPLICIT\"}}"));
  }

  @Test
  void parsesEachHandling() throws Exception {
    assertEquals(
        SettlementLoyalty.explicit(EnumSet.of(SettlementLoyalty.Step.AWARD)),
        parse("{\"loyalty\":{\"handling\":\"EXPLICIT\",\"steps\":[\"AWARD\"]}}"));
    assertEquals(SettlementLoyalty.forbidden(), parse("{\"loyalty\":{\"steps\":[]}}"));
    assertEquals(SettlementLoyalty.implicit(), parse("{\"loyalty\":{\"handling\":\"IMPLICIT\"}}"));
    assertEquals(
        SettlementLoyalty.forbidden(), parse("{\"loyalty\":{\"handling\":\"FORBIDDEN\"}}"));
  }

  @Test
  void rejectsStepsWithoutExplicitHandlingAndUnknownSteps() {
    assertThrows(
        HostError.class,
        () -> parse("{\"loyalty\":{\"handling\":\"IMPLICIT\",\"steps\":[\"AWARD\"]}}"));
    assertThrows(HostError.class, () -> parse("{\"loyalty\":{\"steps\":[\"CASHBACK\"]}}"));
  }

  @Test
  void viewsRoundTrip() throws Exception {
    for (SettlementLoyalty loyalty :
        new SettlementLoyalty[] {
          SettlementLoyalty.explicit(EnumSet.of(SettlementLoyalty.Step.REBATES)),
          SettlementLoyalty.implicit(),
          SettlementLoyalty.forbidden()
        }) {
      JsonNode view = Views.settlementOptions(SettlementOptions.builder().loyalty(loyalty).build());
      assertEquals(loyalty, Parsers.settlementOptions(view).getLoyalty());
    }
  }
}
