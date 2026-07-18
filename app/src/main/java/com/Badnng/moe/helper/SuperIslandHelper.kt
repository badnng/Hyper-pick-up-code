package com.Badnng.moe.helper

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.Badnng.moe.R
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.data.db.OrderGroup
import java.lang.reflect.Method

object SuperIslandHelper {

    // ==================== 设备检测 ====================
    private var cachedSupported: Boolean? = null
    private var cachedProtocol: Int? = null

    fun isSupportIsland(): Boolean = try {
        val method = Class.forName("android.os.SystemProperties")
            .getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
        method.invoke(null, "persist.sys.feature.island", false) as? Boolean ?: false
    } catch (_: Exception) { false }

    fun getFocusProtocol(context: Context): Int = try {
        Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
    } catch (_: Exception) { 0 }

    fun hasFocusPermission(context: Context): Boolean = try {
        val uri = android.net.Uri.parse("content://miui.statusbar.notification.public")
        val args = Bundle().apply { putString("package", context.packageName) }
        context.contentResolver.call(uri, "canShowFocus", null, args)
            ?.getBoolean("canShowFocus", false) ?: false
    } catch (_: Exception) { false }

    fun isDeviceSupported(context: Context): Boolean {
        if (cachedSupported == null) {
            cachedProtocol = getFocusProtocol(context)
            cachedSupported = isSupportIsland() && (cachedProtocol!! >= 3) && hasFocusPermission(context)
        }
        return cachedSupported!!
    }

    fun clearCache() {
        cachedSupported = null
        cachedProtocol = null
    }

    // ==================== 图片 Key 常量 ====================
    private const val PIC_MAIN = "miui.focus.pic_icon_main"
    private const val PIC_LAND = "miui.focus.pic_icon_main"
    private const val PIC_TICKER = "miui.focus.pic_ticker"
    private const val PIC_AOD = "miui.focus.pic_aod"

    // ==================== 动作 Key 常量 ====================
    private const val ACT_COMPLETE = "miui.focus.action_complete"
    private const val ACT_IDENTITY = "miui.focus.action_identity"

    // ==================== 图标映射 ====================
    private fun brandIconRes(context: Context, brandName: String?, orderType: String?): Int {
        return BrandIconResolver.resolveBuiltinFallbackResId(context, brandName, orderType ?: "餐食")
    }

    private fun brandIcon(context: Context, brandName: String?): android.graphics.drawable.Icon? {
        val bitmap = BrandIconResolver.resolveCustomIconBitmap(context, brandName) ?: return null
        return android.graphics.drawable.Icon.createWithBitmap(bitmap)
    }

    // ==================== miui.focus.pics Bundle ====================
    private fun buildPicsBundle(context: Context, iconRes: Int, customIcon: android.graphics.drawable.Icon? = null): Bundle {
        val mainIcon = customIcon ?: Icon.createWithResource(context, iconRes)
        val picsBundle = Bundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putParcelable(PIC_MAIN, mainIcon)
                putParcelable(PIC_LAND, mainIcon)
                putParcelable(PIC_TICKER, Icon.createWithResource(context, R.mipmap.ic_launcher))
                putParcelable(PIC_AOD, Icon.createWithResource(context, R.mipmap.ic_launcher))
            }
        }
        return Bundle().apply { putBundle("miui.focus.pics", picsBundle) }
    }

    // ==================== miui.focus.actions Bundle ====================
    private fun buildActionsBundle(
        context: Context,
        iconRes: Int,
        completePI: PendingIntent,
        identityPI: PendingIntent?,
        hasIdentity: Boolean
    ): Bundle {
        val actionsBundle = Bundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putParcelable(ACT_COMPLETE, Notification.Action.Builder(null, "已完成", completePI).build())
                if (hasIdentity && identityPI != null) {
                    putParcelable(ACT_IDENTITY, Notification.Action.Builder(null, "身份码", identityPI).build())
                }
            }
        }
        return Bundle().apply { putBundle("miui.focus.actions", actionsBundle) }
    }

    private const val PICKUP_TEMPLATE = """
{
    "param_v2": {
        "protocol": 3,
        "business": "pickup",
        "updatable": true,
        "ticker": "{{ticker}}",
        "enableFloat": true,
        "isShowNotification": true,
        "islandFirstFloat": true,
        "aodTitle": "{{contentText}}",
        "picInfo": {
            "type": 2,
            "pic": "miui.focus.pic_icon_main",
            "loop": false,
            "autoplay": false,
            "number": 0
        },
        "smallWindowInfo": {
            "targetPage": "{{targetPage}}"
        },
        "param_island": {
            "islandProperty": 2,
            "islandOrder": false,
            "dismissIsland": false,
            "needCloseAnimation": true,
            "bigIslandArea": {
                "imageTextInfoLeft": {
                    "type": 1,
                    "picInfo": {
                        "type": 1,
                        "pic": "miui.focus.pic_icon_main",
                        "loop": false,
                        "autoplay": false,
                        "number": 0
                    }
                },
                "textInfo": {
                    "frontTitle": "",
                    "title": "{{smallCode}}",
                    "content": "",
                    "showHighlightColor": false,
                    "narrowFont": false
                }
            },
            "smallIslandArea": {
                "imageTextInfoRight": {
                    "type": 6,
                    "textInfo": {
                        "title": "{{smallCode}}",
                        "showHighlightColor": false
                    },
                    "picInfo": {
                        "type": 1,
                        "pic": "miui.focus.pic_icon_main"
                    }
                }
            }
        },
        "chatInfo": {
            "picProfile": "miui.focus.pic_icon_main",
            "title": "{{smallCode}}",
            "content": "{{brand}}",
            "colorTitle": "#000000",
            "colorTitleDark": "#FFFFFF",
            "colorContent": "#666666",
            "colorContentDark": "#AAAAAA"
        },
        "hintInfo": {
            "type": 1,
            "title": "{{hintTitle}}",
            "colorTitleDark": "#FFFFFF",
            "colorContentBg": "{{hintActionBgColor}}",
            "actionInfo": {
                "action": "{{hintAction}}",
                "actionTitle": "{{hintActionTitle}}",
                "actionTitleColor": "#FFFFFF",
                "actionBgColor": "{{hintActionBgColor}}",
                "actionIntentType": 1
            }
        }
    }
}
"""

    private fun buildPickupJson(
        context: Context,
        brand: String,
        contentText: String,
        smallCode: String,
        isExpress: Boolean,
        hasIdentity: Boolean,
        pickupLocation: String? = null,
        orderType: String? = null
    ): String {
        val hintAction = if (hasIdentity) ACT_IDENTITY else ACT_COMPLETE
        val hintTitle = when {
            !pickupLocation.isNullOrBlank() -> pickupLocation
            isExpress -> "请仔细核对您的取件码"
            orderType == "饮品" -> "请注意大堂/荧幕叫号"
            else -> "请注意及时取餐"
        }
        val hintActionTitle = if (hasIdentity) "身份码" else "已完成"
        val hintActionBgColor = if (hasIdentity) "#007AFF" else "#34C759"
        val hintContent = if (isExpress) "快递" else "外卖"
        val targetPage = "${context.packageName}.MainActivity"

        return PICKUP_TEMPLATE
            .replace("{{ticker}}", contentText)
            .replace("{{brand}}", brand)
            .replace("{{contentText}}", contentText)
            .replace("{{hintTitle}}", hintTitle)
            .replace("{{hintContent}}", hintContent)
            .replace("{{hintAction}}", hintAction)
            .replace("{{hintActionTitle}}", hintActionTitle)
            .replace("{{hintActionBgColor}}", hintActionBgColor)
            .replace("{{targetPage}}", targetPage)
            .replace("{{smallCode}}", smallCode)
    }

    // ==================== 发送测试通知 ====================
    fun sendTestNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "promoted_live_update_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(channelId, "订单实况更新", NotificationManager.IMPORTANCE_HIGH)
                        .apply { description = "用于在状态栏和锁屏显示订单实时进度" }
                )
            }

            val testContent = "测试取件码: TEST123456"
            val dismissPI = PendingIntent.getBroadcast(
                context, 999999,
                Intent(context, com.Badnng.moe.receiver.TestNotificationDismissReceiver::class.java).apply {
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = if (isDeviceSupported(context)) {
                val picsBundle = buildPicsBundle(context, R.drawable.ic_package)
                val actionsBundle = buildActionsBundle(context, R.drawable.ic_package, dismissPI, null, false)
                val paramsJson = buildPickupJson(
                    context = context,
                    brand = "测试",
                    contentText = testContent,
                    smallCode = "TEST123456",
                    isExpress = true,
                    hasIdentity = false
                )

                Notification.Builder(context, channelId)
                    .setContentTitle("快递待取 - 测试")
                    .setContentText(testContent)
                    .setSmallIcon(R.drawable.ic_package)
                    .setContentIntent(dismissPI)
                    .setOngoing(true)
                    .addExtras(picsBundle)
                    .addExtras(actionsBundle)
                    .build()
                    .apply { extras.putString("miui.focus.param", paramsJson) }
            } else {
                Notification.Builder(context, channelId)
                    .setContentTitle("快递待取 - 测试")
                    .setContentText(testContent)
                    .setSmallIcon(R.drawable.ic_package)
                    .setContentIntent(dismissPI)
                    .setOngoing(true)
                    .addAction(Notification.Action.Builder(null, "已完成", dismissPI).build())
                    .build()
            }

            if (Build.VERSION.SDK_INT >= 35) {
                notification.extras.putBoolean("android.requestPromotedOngoing", !isDeviceSupported(context))
            }

            nm.notify(999999, notification)
            android.widget.Toast.makeText(
                context,
                if (isDeviceSupported(context)) "已发送岛通知" else "降级原生通知",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            android.util.Log.e("SuperIsland", "sendTest error", e)
            android.widget.Toast.makeText(context, "发送失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // ==================== 构建单条订单通知 ====================
    fun buildPromotedNotification(
        context: Context,
        channelId: String,
        order: OrderEntity,
        brandName: String?,
        isExpress: Boolean,
        completePendingIntent: PendingIntent,
        viewPendingIntent: PendingIntent,
        identityChooserPendingIntent: PendingIntent?
    ): Notification {
        val label = if (isExpress) "取件码" else "取餐码"
        val contentText = "$label: ${order.takeoutCode}"
        val iconRes = brandIconRes(context, brandName, order.orderType)

        val picsBundle = buildPicsBundle(context, iconRes, brandIcon(context, brandName))
        val actionsBundle = buildActionsBundle(
            context, iconRes,
            completePendingIntent, identityChooserPendingIntent, isExpress
        )
        val paramsJson = buildPickupJson(
            context = context,
            brand = brandName ?: if (isExpress) "新包裹" else "新订单",
            contentText = contentText,
            smallCode = order.takeoutCode,
            isExpress = isExpress,
            hasIdentity = isExpress && identityChooserPendingIntent != null,
            pickupLocation = order.pickupLocation,
            orderType = order.orderType
        )

        val notification = Notification.Builder(context, channelId)
            .setContentTitle(if (isExpress) "快递待取 - ${brandName ?: "新包裹"}" else "取餐提醒 - ${brandName ?: "新订单"}")
            .setContentText(contentText)
            .setSmallIcon(brandIcon(context, brandName) ?: Icon.createWithResource(context, iconRes))
            .setContentIntent(viewPendingIntent)
            .addExtras(picsBundle)
            .addExtras(actionsBundle)
            .build()

        notification.extras.putString("miui.focus.param", paramsJson)
        return notification
    }

    // ==================== 更新下载通知模板 ====================
    // 展开态(模板20): IM图文组件(chatInfo) + 进度组件2(progressInfo)
    // 摘要态(模板5): 图文组件1 + 进度文本组件(combinePicInfo)
    // 进度颜色: 天蓝色过渡 (#4FC3F7 → #0288D1)
    private const val DOWNLOAD_TEMPLATE = """
{
    "param_v2": {
        "protocol": 3,
        "business": "update",
        "updatable": true,
        "ticker": "{{ticker}}",
        "enableFloat": false,
        "isShowNotification": true,
        "islandFirstFloat": true,
        "aodTitle": "{{progressText}}",
        "picInfo": {
            "type": 2,
            "loop": false,
            "autoplay": false,
            "number": 0
        },
        "smallWindowInfo": {
            "targetPage": "{{targetPage}}"
        },
        "chatInfo": {
            "picProfile": "miui.focus.pic_icon_main",
            "title": "{{chatTitle}}",
            "content": "{{chatContent}}",
            "colorTitle": "#000000",
            "colorTitleDark": "#FFFFFF",
            "colorContent": "#666666",
            "colorContentDark": "#AAAAAA"
        },
        "progressInfo": {
            "progress": {{progressPercent}},
            "colorProgress": "#4FC3F7",
            "colorProgressEnd": "#0288D1"
        },
        "param_island": {
            "islandProperty": 2,
            "islandOrder": false,
            "dismissIsland": false,
            "needCloseAnimation": true,
            "bigIslandArea": {
                "imageTextInfoLeft": {
                    "type": 1,
                    "picInfo": {
                        "type": 1,
                        "pic": "miui.focus.pic_icon_main",
                        "loop": false,
                        "autoplay": false,
                        "number": 0
                    }
                },
                "textInfo": {
                    "frontTitle": "{{frontTitle}}",
                    "title": "{{title}}",
                    "content": "{{content}}",
                    "showHighlightColor": false,
                    "narrowFont": false
                },
                "progressTextInfo": {
                    "progressInfo": {
                        "progress": "{{progressPercent}}",
                        "colorReach": "	#1E90FF",
                        "colorUnReach": "#333333",
                        "isCCW": "True"
                    }
                }
            },
            "smallIslandArea": {
                "combinePicInfo":{
                    "picInfo": {
                        "type": 1,
                        "pic": "miui.focus.pic_icon_main"
                    },
                    "progressInfo":{
                        "progress": "{{progressPercent}}",
                        "colorReach": "	#1E90FF",
                        "colorUnReach": "#333333",
                        "isCCW": "True"
                    }
                }
            }
        },
        "hintInfo": {
            "type": 1,
            "title": "{{hintTitle}}",
            "colorTitleDark": "#FFFFFF",
            "colorContentBg": "#4FC3F7",
            "actionInfo": {
                "action": "{{hintAction}}",
                "actionTitle": "{{hintActionTitle}}",
                "actionTitleColor": "#FFFFFF",
                "actionBgColor": "#4FC3F7",
                "actionIntentType": 1
            }
        }
    }
}
"""

    fun buildUpdateDownloadNotification(
        context: Context,
        channelId: String,
        versionName: String,
        progress: Float,
        isPaused: Boolean,
        contentIntent: PendingIntent,
        pauseResumeIntent: PendingIntent?
    ): Notification {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val percent = (clampedProgress * 100).toInt()
        val progressText = "$percent%"

        val targetPage = "${context.packageName}.MainActivity"

        val hintAction = "pause_resume"
        val hintActionTitle = if (isPaused) "继续下载" else "暂停"
        val frontTitle = if (isPaused) "更新下载已暂停" else "正在后台下载更新"

        val paramsJson = DOWNLOAD_TEMPLATE
            .replace("{{ticker}}", progressText)
            .replace("{{progressText}}", progressText)
            .replace("{{frontTitle}}", frontTitle)
            .replace("{{title}}", progressText)
            .replace("{{content}}", "v$versionName")
            .replace("{{chatTitle}}", progressText)
            .replace("{{chatContent}}", "v$versionName 更新")
            .replace("{{hintTitle}}", frontTitle)
            .replace("{{hintAction}}", hintAction)
            .replace("{{hintActionTitle}}", hintActionTitle)
            .replace("{{progressPercent}}", percent.toString())
            .replace("{{targetPage}}", targetPage)

        val picsBundle = buildPicsBundle(context, android.R.drawable.stat_sys_download)

        val actionsBundle = Bundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pauseResumeIntent != null) {
                putParcelable("miui.focus.action_pause_resume",
                    Notification.Action.Builder(null, hintActionTitle, pauseResumeIntent).build())
            }
        }

        val notification = Notification.Builder(context, channelId)
            .setContentTitle(frontTitle)
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .addExtras(picsBundle)
            .addExtras(actionsBundle)
            .build()

        notification.extras.putString("miui.focus.param", paramsJson)
        return notification
    }

    // ==================== 构建组通知 ====================
    fun buildGroupNotification(
        context: Context,
        channelId: String,
        group: OrderGroup,
        orders: List<OrderEntity>,
        isExpress: Boolean,
        completeAllPendingIntent: PendingIntent,
        groupDetailPendingIntent: PendingIntent,
        identityChooserPendingIntent: PendingIntent?
    ): Notification {
        val codes = orders.take(3).joinToString(", ") { it.takeoutCode }
        val more = if (orders.size > 3) " 等${orders.size}件" else ""
        val label = if (isExpress) "取件码" else "取餐码"
        val contentText = "$label: $codes$more"
        val iconRes = brandIconRes(context, group.brandName, group.orderType)

        val picsBundle = buildPicsBundle(context, iconRes, brandIcon(context, group.brandName))
        val actionsBundle = buildActionsBundle(
            context, iconRes,
            completeAllPendingIntent, identityChooserPendingIntent, isExpress
        )
        val paramsJson = buildPickupJson(
            context = context,
            brand = group.brandName ?: if (isExpress) "新包裹" else "新订单",
            contentText = contentText,
            smallCode = codes,
            isExpress = isExpress,
            hasIdentity = isExpress && identityChooserPendingIntent != null,
            pickupLocation = orders.firstNotNullOfOrNull { it.pickupLocation },
            orderType = group.orderType
        )

        val notification = Notification.Builder(context, channelId)
            .setContentTitle(if (isExpress) "快递待取 - ${group.brandName ?: "新包裹"}" else "取餐提醒 - ${group.brandName ?: "新订单"}")
            .setContentText(contentText)
            .setSmallIcon(brandIcon(context, group.brandName) ?: Icon.createWithResource(context, iconRes))
            .setContentIntent(groupDetailPendingIntent)
            .addExtras(picsBundle)
            .addExtras(actionsBundle)
            .build()

        notification.extras.putString("miui.focus.param", paramsJson)
        return notification
    }
}