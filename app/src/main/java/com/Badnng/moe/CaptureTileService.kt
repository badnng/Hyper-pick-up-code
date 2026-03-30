package com.Badnng.moe

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.quicksettings.TileService
import rikka.shizuku.Shizuku

class CaptureTileService : TileService() {
    
    override fun onClick() {
        super.onClick()
        
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val captureMode = prefs.getString("capture_mode", "media_projection")
        val useShizuku = captureMode == "shizuku" && isShizukuReady()
        val useRoot = captureMode == "root" && RootHelper.hasRootAccess()

        // 无论哪种模式，都启动 PermissionActivity，利用 startActivityAndCollapse 强制关闭控制中心
        val intent = Intent(this, PermissionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("use_shizuku", useShizuku)
            putExtra("use_root", useRoot)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }
}
