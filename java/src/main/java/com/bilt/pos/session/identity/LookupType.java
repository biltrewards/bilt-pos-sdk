package com.bilt.pos.session.identity;

/** The customer-known identifier used to look up a loyalty member. */
public enum LookupType {
  /** A phone number. */
  PHONE,
  /** An email address. */
  EMAIL,
  /**
   * Any other customer-known handle: username, loyalty card number, retailer account number, etc.
   */
  ACCOUNT_NUMBER
}
