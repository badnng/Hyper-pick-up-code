package com.Badnng.moe.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import com.Badnng.moe.activity.IdentityChooserActivity
import com.Badnng.moe.activity.MainActivity
import com.Badnng.moe.data.repository.OrderCompletionService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OrderWidgetNavigation {
    const val OPEN_HOME = "open_home_from_widget"
    const val IDENTITY_PLATFORM = "widget_identity_platform"

    internal fun home(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("hypernote-widget://home")
        putExtra(OPEN_HOME, true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    internal fun identity(context: Context, platform: String): Intent =
        Intent(context, IdentityChooserActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            // PendingIntent 身份必须包含平台，不能仅靠 extras 区分两个按钮。
            data = Uri.parse("hypernote-widget://identity/$platform")
            putExtra(IDENTITY_PLATFORM, platform)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    fun haptic(context: Context) {
        if (!context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("haptic_enabled", true)) return
        runCatching {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
            )
        }
    }
}

internal val WidgetOrderId = ActionParameters.Key<String>("order_id")
internal val WidgetFilter = ActionParameters.Key<String>("filter")

class CompleteWidgetOrderAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val orderId = parameters[WidgetOrderId] ?: return
        OrderWidgetNavigation.haptic(context)
        try {
            withContext(Dispatchers.IO) { OrderCompletionService(context).completeOrder(orderId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            widgetMessage(context, "完成失败，请稍后重试")
        }
        // 已被其他入口完成/删除也刷新，绝不乐观隐藏未成功写入的订单。
        OrderWidgetUpdates.refreshAll(context)
    }
}

class FilterWidgetOrdersAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        OrderWidgetNavigation.haptic(context)
        try {
            withContext(Dispatchers.IO) {
                OrderWidgetPreferences(context).setFilter(id, OrderWidgetFilter.fromValue(parameters[WidgetFilter]))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            widgetMessage(context, "分类保存失败，请重试")
        }
        OrderWidgetUpdates.refreshInstance(context, id)
    }
}

class RefreshWidgetOrdersAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        OrderWidgetNavigation.haptic(context)
        OrderWidgetUpdates.refreshInstance(context, GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
    }
}

private suspend fun widgetMessage(context: Context, message: String) = withContext(Dispatchers.Main) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
