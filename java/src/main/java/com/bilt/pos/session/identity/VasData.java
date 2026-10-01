/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.identity;

import java.util.Collections;
import java.util.List;

/**
 * Value Added Services (VAS) data read from a mobile wallet pass (e.g. an Apple Wallet loyalty
 * pass) during terminal-prompted identification.
 *
 * <p>The terminal forwards what its payment SDK reports, unmodified: the pass payload stays
 * encrypted and is meant to be decrypted by whoever holds the merchant's VAS private key. When the
 * terminal could not break the report into fields, only {@link #getRaw()} is set.
 */
public final class VasData {

  private final String source;
  private final String merchantId;
  private final List<VasService> services;
  private final String raw;

  public VasData(String source, String merchantId, List<VasService> services, String raw) {
    this.source = source;
    this.merchantId = merchantId;
    this.services =
        services == null ? Collections.emptyList() : Collections.unmodifiableList(services);
    this.raw = raw;
  }

  /** Wallet that supplied the pass, e.g. {@code "ApplePay"}, or {@code null}. */
  public String getSource() {
    return source;
  }

  /** VAS merchant ID the terminal requested the pass with, or {@code null}. */
  public String getMerchantId() {
    return merchantId;
  }

  /** One entry per pass returned. Never {@code null}. */
  public List<VasService> getServices() {
    return services;
  }

  /** The terminal's unparsed VAS report, or {@code null} when it was broken into fields. */
  public String getRaw() {
    return raw;
  }
}
