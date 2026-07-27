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

import java.util.Collections;
import java.util.List;

/**
 * The entry (or entries, for multi-select menus) the customer picked.
 *
 * <p>Indices are zero-based positions into the entry list passed to
 * {@code requestMenuEntry}. For single-select menus {@link #getIndex()} and
 * {@link #getValue()} carry the selection; the list accessors always contain
 * every selected entry.</p>
 */
public final class MenuSelection {

    private final List<Integer> indices;
    private final List<String> values;

    public MenuSelection(List<Integer> indices, List<String> values) {
        this.indices = Collections.unmodifiableList(indices);
        this.values = Collections.unmodifiableList(values);
        if (indices.isEmpty()) {
            throw new IllegalArgumentException("a menu selection needs at least one entry");
        }
    }

    /** First selected index (zero-based). */
    public int getIndex() {
        return indices.get(0);
    }

    /** First selected entry text. */
    public String getValue() {
        return values.get(0);
    }

    /** All selected indices, for multi-select menus. */
    public List<Integer> getIndices() {
        return indices;
    }

    /** All selected entry texts, for multi-select menus. */
    public List<String> getValues() {
        return values;
    }
}
