package com.Badnng.moe.ui.oobe

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object OobeHomeReadiness {
    private val mutableReady = MutableStateFlow(false)
    private val mutableWelcomeSourceReleaseRequested = MutableStateFlow(false)

    val ready: StateFlow<Boolean> = mutableReady.asStateFlow()
    val welcomeSourceReleaseRequested: StateFlow<Boolean> =
        mutableWelcomeSourceReleaseRequested.asStateFlow()
    val isReady: Boolean
        get() = mutableReady.value

    fun reset() {
        mutableReady.value = false
        mutableWelcomeSourceReleaseRequested.value = false
    }

    fun markReady() {
        mutableReady.value = true
    }

    fun releaseWelcomeSource() {
        mutableWelcomeSourceReleaseRequested.value = true
    }

    fun restoreWelcomeSource() {
        mutableWelcomeSourceReleaseRequested.value = false
    }
}
