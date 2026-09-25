/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.internal;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SdkVersionTest {

  @Test
  void theBuildStampsARealVersionIntoTheResource() {
    String version = SdkVersion.current();
    assertNotEquals(SdkVersion.UNKNOWN, version, "processResources must expand the placeholder");
    assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"), version);
  }
}
