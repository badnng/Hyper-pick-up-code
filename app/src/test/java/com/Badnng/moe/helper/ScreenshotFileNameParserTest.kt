package com.Badnng.moe.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenshotFileNameParserTest {
    @Test
    fun extractsHyperOsScreenshotPackageName() {
        assertEquals(
            "com.tencent.mm",
            ScreenshotFileNameParser.extractPackageName(
                "Screenshot_2026-07-25-13-10-00-123_com.tencent.mm.jpg",
            ),
        )
    }

    @Test
    fun keepsUppercaseCharactersUsedByExistingApplicationIds() {
        assertEquals(
            "com.Badnng.moe",
            ScreenshotFileNameParser.extractPackageName(
                "Screenshot_2026-07-25-13-10-00-123_com.Badnng.moe.png",
            ),
        )
    }

    @Test
    fun ignoresKnownEditSuffixes() {
        assertEquals(
            "com.lucky.luckyclient",
            ScreenshotFileNameParser.extractPackageName(
                "Screenshot_2026-07-25-13-10-00-123_com.lucky.luckyclient_edited_1.webp",
            ),
        )
    }

    @Test
    fun doesNotGuessPackageFromOrdinaryImageName() {
        assertNull(ScreenshotFileNameParser.extractPackageName("order_com.example.shop.jpg"))
        assertNull(ScreenshotFileNameParser.extractPackageName("Screenshot_20260725-131000.png"))
    }
}
