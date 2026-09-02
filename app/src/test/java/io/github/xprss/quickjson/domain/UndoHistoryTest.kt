package io.github.xprss.quickjson.domain

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class UndoHistoryTest {
    @Test
    fun undoRedoTracksDrafts() {
        val history = UndoHistory("{}")
        history.push("{invalid")
        history.push("{\"a\":1}")
        assertTrue(history.canUndo)
        assertEquals("{invalid", history.undo())
        assertEquals("{}", history.undo())
        assertFalse(history.canUndo)
        assertEquals("{invalid", history.redo())
        assertTrue(history.canRedo)
    }
}
