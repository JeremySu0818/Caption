package com.jeremysu0818.caption

import com.jeremysu0818.caption.translation.TranslationOutputConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationOutputConverterTest {
    private val converter = TranslationOutputConverter()

    @Test
    fun convertsChineseToRequestedWritingSystem() {
        assertEquals("漢字轉換", converter.convert("汉字转换", "zh-TW"))
        assertEquals("汉字转换", converter.convert("漢字轉換", "zh-CN"))
    }

    @Test
    fun acceptsScriptAliasesAndLeavesOtherTargetsUntouched() {
        assertEquals("繁體中文", converter.convert("繁体中文", "zh-Hant"))
        assertEquals("简体中文", converter.convert("簡體中文", "zh-Hans"))
        assertEquals("汉字转换", converter.convert("汉字转换", "en"))
    }
}
