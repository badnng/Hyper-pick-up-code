package com.Badnng.moe.ui.oobe

internal class OobeBlurPalette(
    val colors: IntArray,
    val modes: IntArray,
)

internal object OobeBlurPalettes {
    // Miuix example About page: "Miuix for Compose" dark logo blend.
    val MiuixDarkLogo = OobeBlurPalette(
        colors = intArrayOf(
            0xE6A1A1A1.toInt(),
            0x4DE6E6E6,
            0xFF1AF500.toInt(),
        ),
        modes = intArrayOf(18, 100, 106),
    )

    // Miuix example About page dark glass card blend.
    val MiuixDarkGlass = OobeBlurPalette(
        colors = intArrayOf(0x4DA9A9A9, 0x1A9C9C9C),
        modes = intArrayOf(28, 120),
    )

    val HyperCeilerLightLogo = OobeBlurPalette(
        colors = intArrayOf(-867546550, -11579569, -15011328),
        modes = intArrayOf(19, 100, 106),
    )

    val HyperCeilerLightWelcomeButton = OobeBlurPalette(
        colors = intArrayOf(-13750738, -15011328),
        modes = intArrayOf(100, 106),
    )

    val HyperCeilerLightCompleteButton = OobeBlurPalette(
        colors = intArrayOf(-12763843, -15021056),
        modes = intArrayOf(100, 106),
    )

    val HyperCeilerLightStateText = OobeBlurPalette(
        colors = intArrayOf(-869915098, -1724697805),
        modes = intArrayOf(19, 3),
    )
}
