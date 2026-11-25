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

import com.bilt.pos.nexo.model.Response;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Reads {@code biltRewardsAmount}, the part of a payment, refund or reversal the terminal paid or
 * returned with Bilt rewards (implicit loyalty), from the response's {@code AdditionalResponse}.
 * nexo has no field for rewards spent inside a payment, so the terminal reports them under this key
 * of its usual URL-encoded {@code key=value&key=value} string; the response's amount still includes
 * them. A response without the key is all card, as before terminals sent it.
 */
public final class BiltRewards {

  private static final Logger LOGGER = Logger.getLogger(BiltRewards.class.getName());

  /** The {@code AdditionalResponse} key. */
  public static final String KEY = "biltRewardsAmount";

  private BiltRewards() {}

  /**
   * The rewards part of {@code amount}, or {@code null} when the response reports none. A value
   * that is not a whole-cent decimal above zero and at most {@code amount} cannot be trusted to
   * split the money: it is ignored, so the movement counts as card money as before the key, and
   * {@code warn} is told why.
   *
   * @param amount what the response moved, rewards included, or {@code null} when unknown
   */
  public static BigDecimal parse(Response response, BigDecimal amount, Consumer<String> warn) {
    String raw = value(response == null ? null : response.getAdditionalResponse());
    if (raw == null) {
      return null;
    }
    BigDecimal rewards;
    try {
      rewards = new BigDecimal(raw.trim());
    } catch (NumberFormatException e) {
      return ignore(raw, "it is not a decimal amount", warn);
    }
    if (rewards.signum() < 0) {
      return ignore(raw, "it is negative", warn);
    }
    if (rewards.stripTrailingZeros().scale() > 2) {
      return ignore(raw, "it is not in whole cents", warn);
    }
    if (amount != null && rewards.compareTo(amount) > 0) {
      return ignore(raw, "it is more than the response's amount " + amount, warn);
    }
    return rewards.signum() == 0 ? null : rewards.setScale(2);
  }

  /** {@link #parse(Response, BigDecimal, Consumer)}, logging a value it ignores. */
  public static BigDecimal parse(Response response, BigDecimal amount) {
    return parse(response, amount, warning -> {});
  }

  private static BigDecimal ignore(String raw, String why, Consumer<String> warn) {
    String warning =
        "the terminal's " + KEY + "=" + raw + " was ignored and counted as card money: " + why;
    LOGGER.warning(warning);
    warn.accept(warning);
    return null;
  }

  private static String value(String additionalResponse) {
    if (additionalResponse == null || additionalResponse.isEmpty()) {
      return null;
    }
    for (String pair : additionalResponse.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0 && pair.substring(0, eq).equals(KEY)) {
        try {
          return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
          return pair.substring(eq + 1);
        }
      }
    }
    return null;
  }
}
