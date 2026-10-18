package com.Badnng.moe.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.Badnng.moe.ui.theme.ColorGenerator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@RunWith(AndroidJUnit4::class)
class OrderWidgetDesignTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun miuixUsesNativePaletteInsteadOfMaterialSeedAndTintedChips() {
        val native = lightColorScheme()
        val palette = miuixWidgetPalette(native)
        val material = ColorGenerator.seedToColorScheme(0xFF6750A4.toInt(), false)
        assertEquals(native.primary, palette.accent)
        assertNotEquals(material.primary, palette.accent)
        assertNotEquals(material.primaryContainer, palette.selected)
        assertEquals(native.surfaceContainer, palette.selected)
        assertEquals(native.surface, palette.filterTrack)
        assertEquals(native.secondaryVariant, palette.button)
    }

    @Test
    fun explicitAppearanceOverridesHostAndSystemModeFollowsHost() {
        val light = miuixWidgetPalette(lightColorScheme())
        val dark = miuixWidgetPalette(darkColorScheme(), pureBlack = true)
        fun host(night: Int) = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        })
        val dayHost = host(Configuration.UI_MODE_NIGHT_NO)
        val nightHost = host(Configuration.UI_MODE_NIGHT_YES)
        assertEquals(Color.Black, OrderWidgetAppearance.fromPalettes(true, "dark", light, dark).background.getColor(dayHost))
        assertEquals(light.background, OrderWidgetAppearance.fromPalettes(true, "light", light, dark).background.getColor(nightHost))
        val system = OrderWidgetAppearance.fromPalettes(true, "system", light, dark)
        assertEquals(light.background, system.background.getColor(dayHost))
        assertEquals(Color.Black, system.background.getColor(nightHost))
    }

    @Test
    fun providersPreserveDefaultsAndAllowResizingInBothAxes() {
        // 默认格数不变，所有预设均可双向缩放到已经验证过的两行尺寸下限。
        val providers = AppWidgetManager.getInstance(context).installedProviders.associateBy { it.provider }
        for ((receiver, cells, minSize) in listOf(
            Triple(SquareOrderWidgetReceiver::class.java, 2 to 2, 110f to 110f),
            Triple(WideOrderWidgetReceiver::class.java, 4 to 2, 250f to 110f),
            Triple(TallOrderWidgetReceiver::class.java, 3 to 4, 180f to 250f),
        )) {
            val info = checkNotNull(providers[ComponentName(context, receiver)]) { "小组件 Provider 未注册" }
            val density = context.resources.displayMetrics.density
            assertEquals(cells.first, info.targetCellWidth)
            assertEquals(cells.second, info.targetCellHeight)
            assertEquals(minSize.first, info.minWidth / density, 1f)
            assertEquals(minSize.second, info.minHeight / density, 1f)
            assertEquals(110f, info.minResizeWidth / density, 1f)
            assertEquals(110f, info.minResizeHeight / density, 1f)
            assertEquals(AppWidgetProviderInfo.RESIZE_HORIZONTAL or AppWidgetProviderInfo.RESIZE_VERTICAL,
                info.resizeMode)
        }
    }
}
