package com.Badnng.moe.ui.miuix

internal const val MIUIX_FLOATING_NAV_BAR_STYLE_KEY = "miuix_floating_nav_bar_style"

internal enum class MiuixFloatingNavigationBarStyle(val preferenceValue: String) {
    Default("default"),
    IosLike("ios_like"),
    ;

    companion object {
        fun fromPreference(value: String?): MiuixFloatingNavigationBarStyle =
            entries.firstOrNull { it.preferenceValue == value } ?: Default
    }
}
