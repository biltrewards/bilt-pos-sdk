package com.bilt.pos.session.storedvalue;

import com.bilt.pos.nexo.model.StoredValueAccountTypeEnum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class StoredValueCardTest {

    @Test
    void equalityIncludesCardIdentityAndAttributes() {
        StoredValueCard original = StoredValueCard.number("GC-1")
                .withProvider("svs")
                .withExpiryDate("1228");
        StoredValueCard same = StoredValueCard.number("GC-1")
                .withProvider("svs")
                .withExpiryDate("1228");
        StoredValueCard differentProvider = StoredValueCard.number("GC-1")
                .withProvider("givex")
                .withExpiryDate("1228");
        StoredValueCard differentEntryMode = StoredValueCard.scanned("GC-1")
                .withProvider("svs")
                .withExpiryDate("1228");
        StoredValueCard differentAccountType = StoredValueCard.number("GC-1")
                .withProvider("svs")
                .withAccountType(StoredValueAccountTypeEnum.OTHER)
                .withExpiryDate("1228");

        assertEquals(original, same);
        assertEquals(original.hashCode(), same.hashCode());
        assertNotEquals(original, differentProvider);
        assertNotEquals(original, differentEntryMode);
        assertNotEquals(original, differentAccountType);
    }
}
