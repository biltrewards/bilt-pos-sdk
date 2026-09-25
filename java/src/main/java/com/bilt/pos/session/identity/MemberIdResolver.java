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
 * How a {@link Member} that is not yet resolved is to be looked up: the kind of identifier the POS
 * has on file, its value, and whether the cashier typed it in. Created through {@link
 * Member#idResolver()}; the session resolves it in the background when the member is attached.
 *
 * <p>The value is sensitive for {@link Type#PHONE} and {@link Type#EMAIL}, so {@link #toString()}
 * masks all but its last few characters.
 */
public final class MemberIdResolver {

  /** The kind of identifier being resolved. */
  public enum Type {
    /** A loyalty account identifier. */
    ACCOUNT_ID,
    /** A phone number. */
    PHONE,
    /** An email address. */
    EMAIL,
    /** A retailer-specific identifier; {@link #customType()} names which. */
    CUSTOM
  }

  private final Type type;
  private final String customType;
  private final String value;
  private final boolean keyedByCashier;

  private MemberIdResolver(Type type, String customType, String value, boolean keyedByCashier) {
    this.type = type;
    this.customType = customType;
    this.value = value;
    this.keyedByCashier = keyedByCashier;
  }

  static MemberIdResolver of(Type type, String customType, String value) {
    Objects.requireNonNull(type, "type");
    if (type == Type.CUSTOM) {
      requireText(customType, "type");
    }
    requireText(value, "value");
    return new MemberIdResolver(type, type == Type.CUSTOM ? customType : null, value, false);
  }

  private static void requireText(String text, String name) {
    Objects.requireNonNull(text, name);
    if (text.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
  }

  MemberIdResolver withKeyedByCashier() {
    return new MemberIdResolver(type, customType, value, true);
  }

  public Type type() {
    return type;
  }

  /** The retailer-specific identifier kind for {@link Type#CUSTOM}; {@code null} otherwise. */
  public String customType() {
    return customType;
  }

  public String value() {
    return value;
  }

  /**
   * {@code true} when the cashier typed the identifier rather than the POS loading it from a
   * profile on file; a terminal lookup sends it as {@code EntryMode=Keyed} instead of {@code File}.
   */
  public boolean keyedByCashier() {
    return keyedByCashier;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof MemberIdResolver)) {
      return false;
    }
    MemberIdResolver that = (MemberIdResolver) other;
    return type == that.type
        && Objects.equals(customType, that.customType)
        && value.equals(that.value)
        && keyedByCashier == that.keyedByCashier;
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, customType, value, keyedByCashier);
  }

  @Override
  public String toString() {
    StringBuilder text = new StringBuilder(type.name());
    if (customType != null) {
      text.append('(').append(customType).append(')');
    }
    text.append(' ').append(type == Type.PHONE || type == Type.EMAIL ? redact(value) : value);
    if (keyedByCashier) {
      text.append(", keyed by cashier");
    }
    return text.toString();
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
