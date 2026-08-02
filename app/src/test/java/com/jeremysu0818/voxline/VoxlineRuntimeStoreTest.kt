package com.jeremysu0818.voxline

import com.jeremysu0818.voxline.data.VoxlineRuntimeStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VoxlineRuntimeStoreTest {
    @After
    fun resetStore() {
        VoxlineRuntimeStore.setStopped()
    }

    @Test
    fun cancelPendingTranslations_clearsOnlyPendingState() {
        VoxlineRuntimeStore.commitSourceText("pending", "Hello", isTranslating = true)
        VoxlineRuntimeStore.commitSourceText("complete", "World", isTranslating = false)

        VoxlineRuntimeStore.cancelPendingTranslations()

        val lines = VoxlineRuntimeStore.state.value.lines
        assertEquals(2, lines.size)
        assertFalse(lines.first { it.id == "pending" }.isTranslating)
        assertFalse(lines.first { it.id == "complete" }.isTranslating)
    }

    @Test
    fun discardPartialLines_keepsCommittedVoxlines() {
        VoxlineRuntimeStore.addOrUpdatePartialSourceText("partial", "Hel")
        VoxlineRuntimeStore.commitSourceText("final", "World", isTranslating = false)

        VoxlineRuntimeStore.discardPartialLines()

        val lines = VoxlineRuntimeStore.state.value.lines
        assertEquals(listOf("final"), lines.map { it.id })
    }
}
