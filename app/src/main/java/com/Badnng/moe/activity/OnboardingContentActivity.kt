package com.Badnng.moe.activity

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.Badnng.moe.helper.EdgeToEdgeHelper
import com.Badnng.moe.ui.oobe.OobeHomeReadiness
import com.Badnng.moe.ui.screen.OnboardingScreen

class OnboardingContentActivity : ComponentActivity() {
    private var flowFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.applyGestureEdgeToEdge(this)
        setContent {
            OnboardingScreen(
                showWelcome = false,
                onComplete = ::handoffToCompletion,
                onFinalStepRequested = ::handoffToCompletion,
                onExit = ::cancelFlow,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        EdgeToEdgeHelper.applyGestureEdgeToEdge(this)
    }

    private fun handoffToCompletion() {
        if (flowFinished) return
        flowFinished = true
        OobeHomeReadiness.releaseWelcomeSource()
        setResult(RESULT_SHOW_COMPLETION)
        finish()
    }

    private fun cancelFlow() {
        if (flowFinished) return
        flowFinished = true
        setResult(RESULT_CANCELED)
        finishAfterTransition()
    }

    companion object {
        internal const val RESULT_SHOW_COMPLETION = Activity.RESULT_FIRST_USER
    }
}
