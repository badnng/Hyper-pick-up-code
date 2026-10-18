package com.Badnng.moe.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderWidgetLayoutTest {
    @Test
    fun squareGridUsesWidthForCardHeightOnTallLauncherCells() {
        // 用户截图：509×563px，设备密度 3；文件夹是约 509×509px。
        val allocated = DpSize((509f / 3).dp, (563f / 3).dp)
        val card = widgetCardSize(allocated)
        assertEquals(allocated.width, card.width)
        assertEquals(allocated.width, card.height)
        assertEquals(18f, (allocated.height - card.height).value, 0.01f)
    }

    @Test
    fun resizingToWideOrTallKeepsTheRequestedSpace() {
        for (size in listOf(DpSize(320.dp, 160.dp), DpSize(240.dp, 320.dp),
            DpSize(180.dp, 250.dp), DpSize(160.dp, 260.dp))) {
            assertEquals(size, widgetCardSize(size))
        }
    }

    @Test
    fun alreadySquareAndMinimumSizesDoNotGainPadding() {
        for (edge in listOf(110.dp, 160.dp, 240.dp)) {
            val size = DpSize(edge, edge)
            assertEquals(size, widgetCardSize(size))
        }
    }
}
