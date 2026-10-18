package com.Badnng.moe.widget

import android.app.PendingIntent
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.Badnng.moe.helper.NotificationScheduler
import com.Badnng.moe.receiver.ScheduledNotificationReceiver
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetReminderCancellationTest {
    @Test
    fun completionCancellationFindsTheOriginallyScheduledPendingIntent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val requestCode = "widget-reminder-cancel-regression-test".hashCode()
        val intent = Intent(context, ScheduledNotificationReceiver::class.java).apply {
            action = ScheduledNotificationReceiver.ACTION_SCHEDULED_NOTIFICATION
            putExtra("order_id", "test-only")
        }
        // 只创建 token，不设置实际闹钟，也不需要精确闹钟权限。
        val original = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE)
        try {
            assertNotNull(original)
            NotificationScheduler.cancel(context, requestCode)
            assertNull(PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
        } finally {
            original.cancel()
        }
    }
}
