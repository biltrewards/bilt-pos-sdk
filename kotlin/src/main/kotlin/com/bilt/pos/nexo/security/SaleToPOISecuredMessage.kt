/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.nexo.security

import com.bilt.pos.nexo.model.ContentInformationType
import com.bilt.pos.nexo.model.EnvelopedData
import com.bilt.pos.nexo.model.MessageHeader
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An encrypted Nexo Sale to POI message using CMS structures.
 *
 * The original JSON payload is AES-encrypted into the [EnvelopedData]
 * (with a per-message session key wrapped by the KEK), the unencrypted
 * [MessageHeader] is preserved for routing, and a [ContentInformationType]
 * security trailer carries the HMAC for integrity verification.
 */
@Serializable
data class SaleToPOISecuredMessage(
    @SerialName("MessageHeader")
    val messageHeader: MessageHeader,
    @SerialName("EnvelopedData")
    val envelopedData: EnvelopedData? = null,
    @SerialName("SecurityTrailer")
    val securityTrailer: ContentInformationType? = null
)
