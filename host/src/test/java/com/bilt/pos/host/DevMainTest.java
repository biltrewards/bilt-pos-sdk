package com.bilt.pos.host;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The dev host answers CORS for every origin unless its config file narrows them. */
class DevMainTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void everyOriginIsAllowedByDefault() throws Exception {
    assertEquals(List.of("*"), DevMain.allowedOrigins(MAPPER.readTree("{}")));
  }

  @Test
  void theConfigFileCanNarrowTheOrigins() throws Exception {
    assertEquals(
        List.of("https://pos.example.com"),
        DevMain.allowedOrigins(MAPPER.readTree("{\"allowedOrigins\":[\"https://pos.example.com\"]}")));
  }
}
