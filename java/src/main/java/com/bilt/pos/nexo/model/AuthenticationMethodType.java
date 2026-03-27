/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   This file is auto-generated from the Nexo Sale to POI v3.0 JSON Schema.
 *   Do not modify manually — re-run code generation instead.
 */
package com.bilt.pos.nexo.model;

import java.io.IOException;
import com.fasterxml.jackson.annotation.*;

/**
 * Methods used for customer authentication during the payment transaction. Informs the Sale
 * System how the cardholder was authenticated.
 */
public enum AuthenticationMethodType {
    BYPASS, MANUAL_VERIFICATION, MERCHANT_AUTHENTICATION, OFFLINE_PIN, ON_LINE_PIN, PAPER_SIGNATURE, SECURED_CHANNEL, SECURE_CERTIFICATE, SECURE_NO_CERTIFICATE, SIGNATURE_CAPTURE, UNKNOWN_METHOD;

    @JsonValue
    public String toValue() {
        switch (this) {
            case BYPASS: return "Bypass";
            case MANUAL_VERIFICATION: return "ManualVerification";
            case MERCHANT_AUTHENTICATION: return "MerchantAuthentication";
            case OFFLINE_PIN: return "OfflinePIN";
            case ON_LINE_PIN: return "OnLinePIN";
            case PAPER_SIGNATURE: return "PaperSignature";
            case SECURED_CHANNEL: return "SecuredChannel";
            case SECURE_CERTIFICATE: return "SecureCertificate";
            case SECURE_NO_CERTIFICATE: return "SecureNoCertificate";
            case SIGNATURE_CAPTURE: return "SignatureCapture";
            case UNKNOWN_METHOD: return "UnknownMethod";
        }
        return null;
    }

    @JsonCreator
    public static AuthenticationMethodType forValue(String value) throws IOException {
        if (value.equals("Bypass")) return BYPASS;
        if (value.equals("ManualVerification")) return MANUAL_VERIFICATION;
        if (value.equals("MerchantAuthentication")) return MERCHANT_AUTHENTICATION;
        if (value.equals("OfflinePIN")) return OFFLINE_PIN;
        if (value.equals("OnLinePIN")) return ON_LINE_PIN;
        if (value.equals("PaperSignature")) return PAPER_SIGNATURE;
        if (value.equals("SecuredChannel")) return SECURED_CHANNEL;
        if (value.equals("SecureCertificate")) return SECURE_CERTIFICATE;
        if (value.equals("SecureNoCertificate")) return SECURE_NO_CERTIFICATE;
        if (value.equals("SignatureCapture")) return SIGNATURE_CAPTURE;
        if (value.equals("UnknownMethod")) return UNKNOWN_METHOD;
        throw new IOException("Cannot deserialize AuthenticationMethodType");
    }
}
