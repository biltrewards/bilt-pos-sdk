package com.bilt.pos.emulator

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class LogSelectionTest {

    @Test
    fun selectionDoesNotMoveWhenLinesAreUnchanged() {
        val lines = listOf("oldest", "middle", "newest")
        val selection = TextRange(3, 12)

        assertEquals(selection, shiftLogSelection(lines, lines, selection))
    }

    @Test
    fun selectionShiftsWhenTwoLinesArePrepended() {
        val old = listOf("oldest", "middle", "latest")
        val new = old + listOf("new line", "newest line")
        val selection = TextRange(2, 9)
        val delta = "new line".length + "newest line".length + 2

        assertEquals(
            TextRange(selection.start + delta, selection.end + delta),
            shiftLogSelection(old, new, selection),
        )
    }

    @Test
    fun selectionInsideTrimmedTailIsCoercedToNewTextEnd() {
        val old = listOf("trimmed oldest", "middle", "latest")
        val new = old.drop(1) + "newest"
        val oldTextLength = old.sumOf { it.length } + old.size - 1
        val newTextLength = new.sumOf { it.length } + new.size - 1
        val selection = TextRange(oldTextLength - 3, oldTextLength)

        assertEquals(
            TextRange(newTextLength, newTextLength),
            shiftLogSelection(old, new, selection),
        )
    }

    @Test
    fun distinctDuplicateTextDoesNotHideTheRetainedLine() {
        val retained = String(charArrayOf('s', 'a', 'm', 'e'))
        val duplicate = String(charArrayOf('s', 'a', 'm', 'e'))
        val old = listOf("oldest", retained)
        val new = old + duplicate
        val selection = TextRange(0, 4)

        assertEquals(
            TextRange(5, 9),
            shiftLogSelection(old, new, selection),
        )
    }

    @Test
    fun unrelatedOrEmptyOldLinesResetSelection() {
        val selection = TextRange(2, 8)
        val old =
            listOf(String(charArrayOf('o', 'l', 'd')), String(charArrayOf('l', 'i', 'n', 'e', 's')))
        val unrelated =
            listOf(
                String(charArrayOf('n', 'e', 'w')),
                String(charArrayOf('l', 'i', 'n', 'e', 's')),
            )

        assertEquals(
            TextRange.Zero,
            shiftLogSelection(old, unrelated, selection),
        )
        assertEquals(TextRange.Zero, shiftLogSelection(emptyList(), listOf("new"), selection))
    }
}
