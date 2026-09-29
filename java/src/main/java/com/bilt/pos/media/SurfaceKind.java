/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.media;

/**
 * How a register draws a given {@link Placement}, reported to the ad platform in {@link
 * Capabilities} so it can pick creatives the surface can actually do justice to.
 *
 * <p>The kinds mirror the surface tiers described in {@code com.bilt.pos.widget}: what matters to
 * the platform is whether the surface plays video, runs HTML, has a tap target at all, and whether
 * interactions come back through the SDK or through the platform's own channel.
 */
public enum SurfaceKind {

  /** A native adapter from a platform module draws the rendering with UI-toolkit widgets. */
  NATIVE,

  /** A {@code WebSurface} subclass drives Bilt's hosted renderer page inside a browser control. */
  WEB,

  /** The payment terminal's own screen, driven through the Nexo display messages. */
  TERMINAL,

  /**
   * The register draws the creative itself from the rendering's content, in its own design system.
   */
  HANDOFF,

  /**
   * A hosted Bilt page in a URL slot the register cannot script; interactions arrive through the
   * platform's event channel rather than a local sink.
   */
  HOSTED
}
