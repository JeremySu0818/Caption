package com.jeremysu0818.caption

import com.jeremysu0818.caption.data.CaptionRuntimeStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CaptionRuntimeStoreTest {
    @After
    fun resetStore() {
        CaptionRuntimeStore.setStopped()
    }

    @Test
    fun cancelPendingTranslations_clearsOnlyPendingState() {
        CaptionRuntimeStore.commitSourceText("pending", "Hello", isTranslating = true)
        CaptionRuntimeStore.commitSourceText("complete", "World", isTranslating = false)

        CaptionRuntimeStore.cancelPendingTranslations()

        val lines = CaptionRuntimeStore.state.value.lines
        assertEquals(2, lines.size)
        assertFalse(lines.first { it.id == "pending" }.isTranslating)
        assertFalse(lines.first { it.id == "complete" }.isTranslating)
    }

    @Test
    fun discardPartialLines_keepsCommittedCaptions() {
        CaptionRuntimeStore.addOrUpdatePartialSourceText("partial", "Hel")
        CaptionRuntimeStore.commitSourceText("final", "World", isTranslating = false)

        CaptionRuntimeStore.discardPartialLines()

        val lines = CaptionRuntimeStore.state.value.lines
        assertEquals(listOf("final"), lines.map { it.id })
    }
}
