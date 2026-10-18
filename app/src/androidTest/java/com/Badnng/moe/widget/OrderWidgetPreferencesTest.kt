package com.Badnng.moe.widget

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderWidgetPreferencesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("test_order_widget_instances", Context.MODE_PRIVATE)
    private val state = OrderWidgetPreferences(prefs)

    @Before fun clearBefore() { prefs.edit().clear().commit() }
    @After fun clearAfter() { prefs.edit().clear().commit() }

    @Test
    fun instancesKeepIndependentFiltersAcrossRecreationAndDeletion() {
        assertEquals(OrderWidgetFilter.ALL, state.filter(1))
        state.setFilter(1, OrderWidgetFilter.FOOD)
        state.setFilter(2, OrderWidgetFilter.EXPRESS)
        val recreated = OrderWidgetPreferences(prefs)
        assertEquals(OrderWidgetFilter.FOOD, recreated.filter(1))
        assertEquals(OrderWidgetFilter.EXPRESS, recreated.filter(2))
        recreated.remove(intArrayOf(1))
        assertEquals(OrderWidgetFilter.ALL, recreated.filter(1))
        assertEquals(OrderWidgetFilter.EXPRESS, recreated.filter(2))
    }

    @Test
    fun restoreCanSwapIdsWithoutOverwritingTheirOriginalFilters() {
        state.setFilter(1, OrderWidgetFilter.FOOD)
        state.setFilter(2, OrderWidgetFilter.EXPRESS)
        state.restore(intArrayOf(1, 2), intArrayOf(2, 1))
        assertEquals(OrderWidgetFilter.EXPRESS, state.filter(1))
        assertEquals(OrderWidgetFilter.FOOD, state.filter(2))
    }

    @Test
    fun navigationPendingIntentsHaveDifferentIdentityAndHomeHasExplicitRoute() {
        val taobao = OrderWidgetNavigation.identity(context, "taobao")
        val pdd = OrderWidgetNavigation.identity(context, "pdd")
        assertFalse(taobao.filterEquals(pdd))
        assertEquals("taobao", taobao.getStringExtra(OrderWidgetNavigation.IDENTITY_PLATFORM))
        assertEquals("pdd", pdd.getStringExtra(OrderWidgetNavigation.IDENTITY_PLATFORM))
        assertTrue(OrderWidgetNavigation.home(context).getBooleanExtra(OrderWidgetNavigation.OPEN_HOME, false))
    }
}
