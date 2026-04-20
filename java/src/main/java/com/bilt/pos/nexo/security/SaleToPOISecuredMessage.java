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

import com.bilt.pos.nexo.model.ContentInformationType;
import com.bilt.pos.nexo.model.EnvelopedData;
import com.bilt.pos.nexo.model.MessageHeader;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An encrypted Nexo Sale to POI message using CMS structures.
 *
 * <p>The original JSON payload is AES-encrypted into the {@link EnvelopedData}
 * (with a per-message session key wrapped by the KEK), the unencrypted
 * {@link MessageHeader} is preserved for routing, and a {@link ContentInformationType}
 * security trailer carries the HMAC for integrity verification.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SaleToPOISecuredMessage {

    private MessageHeader messageHeader;
    private EnvelopedData envelopedData;
    private ContentInformationType securityTrailer;

    @JsonProperty("MessageHeader")
    public MessageHeader getMessageHeader() { return messageHeader; }
    @JsonProperty("MessageHeader")
    public void setMessageHeader(MessageHeader value) { this.messageHeader = value; }

    @JsonProperty("EnvelopedData")
    public EnvelopedData getEnvelopedData() { return envelopedData; }
    @JsonProperty("EnvelopedData")
    public void setEnvelopedData(EnvelopedData value) { this.envelopedData = value; }

    @JsonProperty("SecurityTrailer")
    public ContentInformationType getSecurityTrailer() { return securityTrailer; }
    @JsonProperty("SecurityTrailer")
    public void setSecurityTrailer(ContentInformationType value) { this.securityTrailer = value; }
}
