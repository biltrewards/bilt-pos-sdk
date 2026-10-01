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

/** One pass returned in a {@link VasData} read. */
public final class VasService {

  private final String serviceId;
  private final String serviceType;
  private final String statusWord;
  private final String encryptedData;
  private final String cipherTimestamp;

  public VasService(
      String serviceId,
      String serviceType,
      String statusWord,
      String encryptedData,
      String cipherTimestamp) {
    this.serviceId = serviceId;
    this.serviceType = serviceType;
    this.statusWord = statusWord;
    this.encryptedData = encryptedData;
    this.cipherTimestamp = cipherTimestamp;
  }

  /** Pass type identifier, e.g. {@code "pass.com.biltrewards.loyalty"}. */
  public String getServiceId() {
    return serviceId;
  }

  /** Service type the terminal reports, e.g. {@code "Coupon1"}. */
  public String getServiceType() {
    return serviceType;
  }

  /** ISO 7816 status word of the pass read, e.g. {@code "9000"}. */
  public String getStatusWord() {
    return statusWord;
  }

  /** Encrypted pass payload as hex, or {@code null}. */
  public String getEncryptedData() {
    return encryptedData;
  }

  /** Timestamp used in the pass encryption, as hex, or {@code null}. */
  public String getCipherTimestamp() {
    return cipherTimestamp;
  }
}
