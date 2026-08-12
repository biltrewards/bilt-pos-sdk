/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session;

import com.bilt.pos.nexo.model.DocumentQualifierEnum;

import java.util.Objects;

/**
 * Content for {@link Terminal#print(PrintPayload)}.
 *
 * <p>Create with {@link #text(String)} for plain text or
 * {@link #xhtml(String)} for a Base64-encoded XHTML document, optionally
 * changing the document kind with {@link #withDocumentQualifier}.</p>
 */
public final class PrintPayload {

    /** Content encoding of the payload. */
    public enum Format { TEXT, XHTML }

    private final Format format;
    private final String content;
    private final DocumentQualifierEnum documentQualifier;

    private PrintPayload(Format format, String content, DocumentQualifierEnum documentQualifier) {
        this.format = format;
        this.content = content;
        this.documentQualifier = documentQualifier;
    }

    /** Plain-text content, printed as a customer receipt by default. */
    public static PrintPayload text(String text) {
        Objects.requireNonNull(text, "text");
        return new PrintPayload(Format.TEXT, text, DocumentQualifierEnum.CUSTOMER_RECEIPT);
    }

    /** Base64-encoded XHTML content, printed as a customer receipt by default. */
    public static PrintPayload xhtml(String base64Xhtml) {
        Objects.requireNonNull(base64Xhtml, "base64Xhtml");
        return new PrintPayload(Format.XHTML, base64Xhtml, DocumentQualifierEnum.CUSTOMER_RECEIPT);
    }

    /** Returns a copy of this payload with a different document qualifier. */
    public PrintPayload withDocumentQualifier(DocumentQualifierEnum qualifier) {
        Objects.requireNonNull(qualifier, "qualifier");
        return new PrintPayload(format, content, qualifier);
    }

    public Format getFormat() {
        return format;
    }

    public String getContent() {
        return content;
    }

    public DocumentQualifierEnum getDocumentQualifier() {
        return documentQualifier;
    }
}
