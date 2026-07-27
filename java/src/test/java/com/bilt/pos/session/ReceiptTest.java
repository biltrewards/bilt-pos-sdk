package com.bilt.pos.session;

import com.bilt.pos.receipt.ReceiptDataType;
import com.bilt.pos.receipt.ReceiptHelper;
import com.bilt.pos.receipt.ReceiptType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptTest {

    @Test
    void fromDecodesHtmlAndCopiesFields() {
        ReceiptDataType data = new ReceiptDataType();
        data.setMerchantName("Kirkland Krafts");

        ReceiptType wire = new ReceiptType();
        wire.setHtmlReceipt("<html>receipt</html>".getBytes(StandardCharsets.UTF_8));
        wire.setPlainTextReceipt("RECEIPT");
        wire.setReceiptData(data);

        Receipt receipt = Receipt.from(wire);

        assertEquals("<html>receipt</html>", receipt.getHtml());
        assertEquals("RECEIPT", receipt.getPlainText());
        assertEquals("Kirkland Krafts", receipt.getReceiptData().getMerchantName());
    }

    @Test
    void fromHandlesMissingFields() {
        Receipt receipt = Receipt.from(new ReceiptType());
        assertNull(receipt.getHtml());
        assertNull(receipt.getPlainText());
        assertNull(receipt.getReceiptData());
    }

    @Test
    void fromNullIsNull() {
        assertNull(Receipt.from(null));
    }

    @Test
    void fromBase64RoundTrips() throws Exception {
        ReceiptType wire = new ReceiptType();
        wire.setPlainTextReceipt("TOTAL $12.34");

        Receipt receipt = Receipt.fromBase64(ReceiptHelper.toBase64(wire));

        assertEquals("TOTAL $12.34", receipt.getPlainText());
    }

    @Test
    void fromBase64NullIsNull() throws Exception {
        assertNull(Receipt.fromBase64(null));
    }
}
