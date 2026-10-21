package com.Badnng.moe.service

import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import com.Badnng.moe.R
import com.Badnng.moe.activity.PermissionActivity

class CaptureTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.icon = Icon.createWithResource(this, R.drawable.note_qs)
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val intent = PermissionActivity.createCaptureIntent(this)

        // Android 14+ 使用 PendingIntent 方式启动
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
