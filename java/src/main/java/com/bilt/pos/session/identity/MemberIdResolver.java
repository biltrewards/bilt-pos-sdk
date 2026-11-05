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

import java.util.Objects;

/**
 * An identifier to look up in the background. All identifier values are masked in {@link
 * #toString()}.
 */
public final class MemberIdResolver {
  private final LookupType type;
  private final String value;

  private MemberIdResolver(LookupType type, String value) {
    this.type = type;
    this.value = value;
  }

  static MemberIdResolver of(LookupType type, String value) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
    if (value.isEmpty()) {
      throw new IllegalArgumentException("value must not be empty");
    }
    return new MemberIdResolver(type, value);
  }

  public LookupType type() {
    return type;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof MemberIdResolver)) {
      return false;
    }
    MemberIdResolver that = (MemberIdResolver) other;
    return type == that.type && value.equals(that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, value);
  }

  @Override
  public String toString() {
    return type.name() + " " + redact(value);
  }

  /** Masks all but the last four characters (two for short values, none for very short ones). */
  static String redact(String value) {
    int visible = value.length() >= 8 ? 4 : value.length() >= 4 ? 2 : 0;
    StringBuilder masked = new StringBuilder(value.length());
    for (int i = 0; i < value.length() - visible; i++) {
      masked.append('*');
    }
    return masked.append(value, value.length() - visible, value.length()).toString();
  }
}
