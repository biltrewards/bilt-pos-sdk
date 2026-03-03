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

import com.bilt.pos.nexo.model.MessageHeader;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An encrypted Nexo Sale to POI message.
 *
 * <p>The original JSON payload is AES-encrypted into the {@code NexoBlob}
 * (Base64-encoded), with the unencrypted {@link MessageHeader} and a
 * {@link SecurityTrailer} containing the HMAC and nonce.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SaleToPOISecuredMessage {

    private MessageHeader messageHeader;
    private String nexoBlob;
    private SecurityTrailer securityTrailer;

    @JsonProperty("MessageHeader")
    public MessageHeader getMessageHeader() { return messageHeader; }
    @JsonProperty("MessageHeader")
    public void setMessageHeader(MessageHeader value) { this.messageHeader = value; }

    @JsonProperty("NexoBlob")
    public String getNexoBlob() { return nexoBlob; }
    @JsonProperty("NexoBlob")
    public void setNexoBlob(String value) { this.nexoBlob = value; }

    @JsonProperty("SecurityTrailer")
    public SecurityTrailer getSecurityTrailer() { return securityTrailer; }
    @JsonProperty("SecurityTrailer")
    public void setSecurityTrailer(SecurityTrailer value) { this.securityTrailer = value; }
}
