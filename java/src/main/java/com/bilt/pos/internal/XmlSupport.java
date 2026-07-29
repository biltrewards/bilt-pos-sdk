/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 *
 *   Internal XML marshalling support shared by the display and receipt helpers.
 */
package com.bilt.pos.internal;

import com.ctc.wstx.stax.WstxInputFactory;
import com.ctc.wstx.stax.WstxOutputFactory;
import org.codehaus.stax2.XMLOutputFactory2;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationIntrospector;

/**
 * Jackson-based XML marshalling for the generated jakarta.xml.bind-annotated
 * models. Replaces the JAXB runtime, which does not run on Android; Jackson
 * reads the same annotations via {@link JakartaXmlBindAnnotationIntrospector},
 * so the generated model classes stay untouched.
 *
 * <p><strong>Internal API</strong> — not part of the SDK's public surface.
 */
public final class XmlSupport {

    /** Matches the declaration the JAXB implementation used to emit (double quotes, no standalone). */
    public static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    private XmlSupport() {
    }

    /**
     * Builds an {@link XmlMapper} configured to mirror the previous JAXB
     * marshalling behavior: jakarta annotations drive names/attributes/order,
     * nulls are omitted, empty beans marshal as empty elements
     * ({@code <confirmation/>}), and unknown elements are ignored on read.
     */
    public static XmlMapper newMapper() {
        WstxOutputFactory outputFactory = new WstxOutputFactory();
        // JAXB wrote childless elements self-closed; terminals expect that shape
        outputFactory.setProperty(XMLOutputFactory2.P_AUTOMATIC_EMPTY_ELEMENTS, true);
        XmlFactory factory = XmlFactory.builder()
                .inputFactory(new WstxInputFactory())
                .outputFactory(outputFactory)
                .build();
        XmlMapper mapper = XmlMapper.builder(factory)
                .defaultUseWrapper(false)
                // single-line base64 for byte[] fields, as the JAXB runtime emitted
                .defaultBase64Variant(Base64Variants.MIME_NO_LINEFEEDS)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        mapper.setAnnotationIntrospector(new JakartaXmlBindAnnotationIntrospector(mapper.getTypeFactory()));
        return mapper;
    }

    /**
     * Normalizes marshalled XML to the single-default-namespace shape the
     * terminals expect (and the JAXB-based helpers produced): all namespace
     * prefixes and declarations are stripped, the given default namespace is
     * declared on the root element, and the XML declaration is prepended.
     *
     * @param xml         the XML document as written by Jackson (no declaration)
     * @param rootElement local name of the root element
     * @param namespace   default namespace to declare on the root
     * @return the normalized document, starting with {@link #XML_DECLARATION}
     */
    public static String withDefaultNamespace(String xml, String rootElement, String namespace) {
        xml = xml.replaceAll("(</?)[A-Za-z_][\\w.-]*:", "$1");
        xml = xml.replaceAll(" xmlns(:[\\w.-]+)?=\"[^\"]*\"", "");
        // lookahead so e.g. <receipt> matches but <receiptData> does not
        xml = xml.replaceFirst("<" + rootElement + "(?=[ />])", "<" + rootElement + " xmlns=\"" + namespace + "\"");
        return XML_DECLARATION + xml;
    }
}
