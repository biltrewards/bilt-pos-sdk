package com.bilt.pos.session.settlement;

import java.util.Objects;

/** Card authorization policy; NEXO calls pre-authorization the Reservation profile. */
public final class Authorization {
  public enum Mode {
    SALE,
    PRE_AUTH,
    PRE_AUTH_UPDATE,
    PRE_AUTH_COMPLETE
  }

  private final Mode mode;
  private final String saleReferenceId;
  private final ReservationReference reference;
  private final String requestedValidityDate;
  private final boolean displayCart;

  private Authorization(Builder b) {
    mode = b.mode;
    saleReferenceId = b.saleReferenceId;
    reference = b.reference;
    requestedValidityDate = b.requestedValidityDate;
    displayCart = b.displayCart == null ? mode != Mode.PRE_AUTH : b.displayCart;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Authorization sale() {
    return builder().build();
  }

  public static Authorization preAuth(String saleReferenceId) {
    return builder().mode(Mode.PRE_AUTH).saleReferenceId(saleReferenceId).build();
  }

  public static Authorization update(ReservationReference reference) {
    return builder().mode(Mode.PRE_AUTH_UPDATE).reference(reference).build();
  }

  public static Authorization complete(ReservationReference reference) {
    return builder().mode(Mode.PRE_AUTH_COMPLETE).reference(reference).build();
  }

  public Mode getMode() {
    return mode;
  }

  public String getSaleReferenceId() {
    return saleReferenceId;
  }

  public ReservationReference getReference() {
    return reference;
  }

  public String getRequestedValidityDate() {
    return requestedValidityDate;
  }

  public boolean isDisplayCart() {
    return displayCart;
  }

  public static final class Builder {
    private Mode mode = Mode.SALE;
    private String saleReferenceId;
    private ReservationReference reference;
    private String requestedValidityDate;
    private Boolean displayCart;

    private Builder() {}

    public Builder mode(Mode mode) {
      this.mode = Objects.requireNonNull(mode, "mode");
      return this;
    }

    public Builder saleReferenceId(String id) {
      saleReferenceId = id;
      return this;
    }

    public Builder reference(ReservationReference reference) {
      this.reference = reference;
      return this;
    }

    public Builder requestedValidityDate(String date) {
      requestedValidityDate = date;
      return this;
    }

    public Builder displayCart(boolean display) {
      displayCart = display;
      return this;
    }

    public Authorization build() {
      return new Authorization(this);
    }
  }
}
