/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.nexo.security;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Authentication and key metadata attached to an encrypted Nexo message.
 *
 * <p>Contains the HMAC for integrity verification, the random nonce used
 * during IV derivation, and key identification metadata.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityTrailer {

    private int cryptoVersion;
    private String keyIdentifier;
    private int keyVersion;
    private byte[] nonce;
    private byte[] hmac;

    @JsonProperty("CryptoVersion")
    public int getCryptoVersion() { return cryptoVersion; }
    @JsonProperty("CryptoVersion")
    public void setCryptoVersion(int value) { this.cryptoVersion = value; }

    @JsonProperty("KeyIdentifier")
    public String getKeyIdentifier() { return keyIdentifier; }
    @JsonProperty("KeyIdentifier")
    public void setKeyIdentifier(String value) { this.keyIdentifier = value; }

    @JsonProperty("KeyVersion")
    public int getKeyVersion() { return keyVersion; }
    @JsonProperty("KeyVersion")
    public void setKeyVersion(int value) { this.keyVersion = value; }

    @JsonProperty("Nonce")
    public byte[] getNonce() { return nonce; }
    @JsonProperty("Nonce")
    public void setNonce(byte[] value) { this.nonce = value; }

    @JsonProperty("Hmac")
    public byte[] getHmac() { return hmac; }
    @JsonProperty("Hmac")
    public void setHmac(byte[] value) { this.hmac = value; }
}
