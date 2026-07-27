/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.session.input;

import java.time.Instant;

/** A signature captured on the terminal. */
public final class Signature {

    private final byte[] imageData;
    private final String format;
    private final int width;
    private final int height;
    private final Instant capturedAt;

    public Signature(byte[] imageData, String format, int width, int height, Instant capturedAt) {
        this.imageData = imageData;
        this.format = format;
        this.width = width;
        this.height = height;
        this.capturedAt = capturedAt;
    }

    /** Parses a PNG image, reading its dimensions from the IHDR header. */
    public static Signature fromPng(byte[] imageData, Instant capturedAt) {
        int width = 0;
        int height = 0;
        // PNG: 8-byte signature, 4-byte length, "IHDR", then width/height as big-endian ints
        if (imageData != null && imageData.length >= 24
                && imageData[12] == 'I' && imageData[13] == 'H'
                && imageData[14] == 'D' && imageData[15] == 'R') {
            width = readInt(imageData, 16);
            height = readInt(imageData, 20);
        }
        return new Signature(imageData, "PNG", width, height, capturedAt);
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    /** Raw image bytes. */
    public byte[] getImageData() {
        return imageData;
    }

    /** Image format, e.g. {@code "PNG"}. */
    public String getFormat() {
        return format;
    }

    /** Pixel width, or {@code 0} when unknown. */
    public int getWidth() {
        return width;
    }

    /** Pixel height, or {@code 0} when unknown. */
    public int getHeight() {
        return height;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }
}
