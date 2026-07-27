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

import com.bilt.pos.nexo.model.ForceEntryModeType;

/**
 * Restricts how the terminal captures an identifier or card.
 *
 * <p>Use {@link #KEYED} for typed email/phone entry, {@link #SCANNED} for a
 * loyalty card barcode.</p>
 */
public enum ForceEntryMode {

    CHECK_READER(ForceEntryModeType.CHECK_READER),
    CONTACTLESS(ForceEntryModeType.CONTACTLESS),
    FILE(ForceEntryModeType.FILE),
    ICC(ForceEntryModeType.ICC),
    KEYED(ForceEntryModeType.KEYED),
    MAG_STRIPE(ForceEntryModeType.MAG_STRIPE),
    MANUAL(ForceEntryModeType.MANUAL),
    RFID(ForceEntryModeType.RFID),
    SCANNED(ForceEntryModeType.SCANNED),
    SYNCHRONOUS_ICC(ForceEntryModeType.SYNCHRONOUS_ICC),
    TAPPED(ForceEntryModeType.TAPPED);

    private final ForceEntryModeType wire;

    ForceEntryMode(ForceEntryModeType wire) {
        this.wire = wire;
    }

    /** The Nexo wire enum this mode maps to. */
    public ForceEntryModeType toWire() {
        return wire;
    }
}
