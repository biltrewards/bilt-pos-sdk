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

import com.bilt.pos.nexo.model.EntryModeType;

/** How an identifier or card was actually captured. */
public enum EntryMode {

    CONTACTLESS(EntryModeType.CONTACTLESS),
    FILE(EntryModeType.FILE),
    ICC(EntryModeType.ICC),
    KEYED(EntryModeType.KEYED),
    MAG_STRIPE(EntryModeType.MAG_STRIPE),
    MANUAL(EntryModeType.MANUAL),
    MOBILE(EntryModeType.MOBILE),
    RFID(EntryModeType.RFID),
    SCANNED(EntryModeType.SCANNED),
    SYNCHRONOUS_ICC(EntryModeType.SYNCHRONOUS_ICC),
    TAPPED(EntryModeType.TAPPED);

    private final EntryModeType wire;

    EntryMode(EntryModeType wire) {
        this.wire = wire;
    }

    /** The Nexo wire enum this mode maps to. */
    public EntryModeType toWire() {
        return wire;
    }

    /** Maps a wire value; {@code null} for {@code null} input. */
    public static EntryMode fromWire(EntryModeType wire) {
        if (wire == null) {
            return null;
        }
        for (EntryMode mode : values()) {
            if (mode.wire == wire) {
                return mode;
            }
        }
        return null;
    }
}
