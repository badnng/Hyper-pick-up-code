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
import org.json.JSONObject

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
    private const val PIC_PROFILE = "miui.focus.pic_profile"
    private const val PIC_ISLAND = "miui.focus.pic_island"

    // ==================== 动作 Key 常量 ====================
    private const val ACT_COMPLETE = "miui.focus.action_complete"
    private const val ACT_IDENTITY = "miui.focus.action_identity"
    private const val ACT_VIEW = "miui.focus.action_view"
    private const val ACT_QR = "miui.focus.action_qr"
    private const val ACT_HINT_V2 = "miui.focus.action_hint_v2"
    private const val PIC_LARGE_V2 = "miui.focus.pic_large_v2"
    private const val PIC_HINT_CONTENT_V2 = "miui.focus.pic_hint_content_v2"
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
        val contentIcon = customIcon ?: Icon.createWithResource(context, iconRes)
        val appIcon = Icon.createWithResource(context, R.mipmap.ic_launcher)
        val picsBundle = Bundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 新图文组件左侧使用品牌/类型图标。
                putParcelable(PIC_PROFILE, contentIcon)
                putParcelable(PIC_ISLAND, contentIcon)
                // 识别图形组件 1 位于右上角，按模板规范显示桌面应用图标。
                putParcelable(PIC_LARGE_V2, appIcon)
                putParcelable(PIC_HINT_CONTENT_V2, contentIcon)
            }
        }
        return Bundle().apply { putBundle("miui.focus.pics", picsBundle) }
    }

    // ==================== miui.focus.actions Bundle ====================
    private fun buildActionsBundle(
        actionKey: String,
        actionTitle: String,
        actionPendingIntent: PendingIntent,
        identityPI: PendingIntent?,
        hasIdentity: Boolean
    ): Bundle {
        val actionsBundle = Bundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val action = Notification.Action.Builder(null, actionTitle, actionPendingIntent).build()
                putParcelable(actionKey, action)
                if (actionKey != ACT_HINT_V2) {
                    putParcelable(ACT_HINT_V2, action)
                }
                if (hasIdentity && identityPI != null) {
                    putParcelable(ACT_IDENTITY, Notification.Action.Builder(null, "身份码", identityPI).build())
                }
            }
        }
        return Bundle().apply { putBundle("miui.focus.actions", actionsBundle) }
    }

    // 官方模板 16：新图文组件 + 识别图形组件 1 + 按钮组件 5。
    // 只替换组件样式，不改超级岛出现位置、大小策略和 action 的业务分流。
    private const val TEMPLATE_SIXTEEN = """
{
    "param_v2": {
        "protocol": 3,
        "business": {{business}},
        "updatable": true,
        "ticker": {{ticker}},
        "enableFloat": {{enableFloat}},
        {{showSmallIconEntry}}
        "isShowNotification": true,
        "islandFirstFloat": true,
        "aodTitle": {{aodTitle}},
        "aodPic": "miui.focus.pic_profile",
        "picInfo": {
            "pic": "miui.focus.pic_large_v2",
            "type": 1
        },
        "smallWindowInfo": {
            "targetPage": {{targetPage}}
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
                        "pic": "miui.focus.pic_island",
                        "loop": false,
                        "autoplay": false,
                        "number": 0
                    }
                },
                "textInfo": {
                    "frontTitle": "",
                    "title": {{islandTitle}},
                    "content": "",
                    "showHighlightColor": false,
                    "narrowFont": false
                }
            },
            "smallIslandArea": {
                "imageTextInfoRight": {
                    "type": 6,
                    "textInfo": {
                        "title": {{islandTitle}},
                        "showHighlightColor": false
                    },
                    "picInfo": {
                        "type": 1,
                        "pic": "miui.focus.pic_island"
                    }
                }
            }
        },
        "iconTextInfo": {
            "title": {{iconTextTitle}},
            "content": {{iconTextContent}},
            "colorTitle": "#111111",
            "colorTitleDark": "#FFFFFF",
            "colorContent": "#666666",
            "colorContentDark": "#AAAAAA",
            "animIconInfo": {
                "type": 3,
                "src": "miui.focus.pic_profile"
            }
        },
        "hintInfo": {
            "type": 1,
            "title": {{primaryText}},
            "colorTitle": "#000000",
            "colorTitleDark": "#FFFFFF",
            "colorContent": "#FFB300",
            "colorContentDark": "#FFC44D",
            "colorContentBg": "#3A3325",
            "picContent": "miui.focus.pic_hint_content_v2",
            "actionInfo": {
                "action": "miui.focus.action_hint_v2",
                "actionTitle": {{actionTitle}},
                "actionTitleColor": "#FFFFFF",
                "actionTitleColorDark": "#FFFFFF",
                "actionBgColor": "#3482FF",
                "actionBgColorDark": "#3482FF"
            }
        }
    }
}
"""

    private fun buildTemplateSixteenJson(
        context: Context,
        business: String,
        ticker: String,
        enableFloat: Boolean,
        showSmallIcon: Boolean?,
        aodTitle: String,
        chatTitle: String,
        chatContent: String,
        hintTitle: String,
        islandTitle: String,
        typeLabel: String,
        actionTitle: String,
    ): String {
        val targetPage = com.Badnng.moe.activity.MainActivity::class.java.name
        val showSmallIconEntry = showSmallIcon?.let { "\"showSmallIcon\": $it," } ?: ""
        return TEMPLATE_SIXTEEN
            .replace("{{business}}", JSONObject.quote(business))
            .replace("{{ticker}}", JSONObject.quote(ticker))
            .replace("{{enableFloat}}", enableFloat.toString())
            .replace("{{showSmallIconEntry}}", showSmallIconEntry)
            .replace("{{aodTitle}}", JSONObject.quote(aodTitle))
            .replace("{{iconTextTitle}}", JSONObject.quote(chatTitle))
            .replace("{{iconTextContent}}", JSONObject.quote(chatContent))
            .replace("{{primaryText}}", JSONObject.quote(hintTitle))
            .replace("{{highlightText}}", JSONObject.quote(typeLabel))
            .replace("{{actionTitle}}", JSONObject.quote(actionTitle))
            .replace("{{islandTitle}}", JSONObject.quote(islandTitle))
            .replace("{{targetPage}}", JSONObject.quote(targetPage))
    }
    private fun pickupHintTitle(
        pickupLocation: String?,
        isExpress: Boolean,
        orderType: String?,
    ): String = when {
        !pickupLocation.isNullOrBlank() -> pickupLocation
        isExpress -> "请仔细核对您的取件码"
        orderType == "饮品" -> "请注意大堂/荧幕叫号"
        else -> "请注意及时取餐"
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
                val actionsBundle = buildActionsBundle(
                    actionKey = ACT_COMPLETE,
                    actionTitle = "已完成",
                    actionPendingIntent = dismissPI,
                    identityPI = null,
                    hasIdentity = false,
                )
                val paramsJson = buildTemplateSixteenJson(
                    context = context,
                    business = "pickup",
                    ticker = testContent,
                    enableFloat = true,
                    showSmallIcon = false,
                    aodTitle = testContent,
                    chatTitle = "TEST123456",
                    chatContent = "测试",
                    hintTitle = pickupHintTitle(null, isExpress = true, orderType = "快递"),
                    islandTitle = "TEST123456",
                    typeLabel = "快递",
                    actionTitle = "已完成",
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
        qrDetailPendingIntent: PendingIntent?,
        identityChooserPendingIntent: PendingIntent?
    ): Notification {
        val label = if (isExpress) "取件码" else "取餐码"
        val contentText = "$label: ${order.takeoutCode}"
        val iconRes = brandIconRes(context, brandName, order.orderType)
        val hasQrCode = !order.qrCodeData.isNullOrBlank() && qrDetailPendingIntent != null
        val hasIdentityAction = isExpress && identityChooserPendingIntent != null
        val actionKey = when {
            hasQrCode -> ACT_QR
            hasIdentityAction -> ACT_IDENTITY
            else -> ACT_COMPLETE
        }
        val actionTitle = when {
            hasQrCode -> "二维码"
            hasIdentityAction -> "身份码"
            else -> "已完成"
        }
        val actionPendingIntent = when {
            hasQrCode -> qrDetailPendingIntent
            hasIdentityAction -> identityChooserPendingIntent
            else -> completePendingIntent
        }

        val picsBundle = buildPicsBundle(context, iconRes, brandIcon(context, brandName))
        val actionsBundle = buildActionsBundle(
            actionKey = ACT_HINT_V2,
            actionTitle = actionTitle,
            actionPendingIntent = requireNotNull(actionPendingIntent),
            identityPI = identityChooserPendingIntent,
            hasIdentity = isExpress && actionKey != ACT_IDENTITY,
        )
        val displayBrand = brandName ?: if (isExpress) "新包裹" else "新订单"
        val hintTitle = pickupHintTitle(order.pickupLocation, isExpress, order.orderType)
        val paramsJson = buildTemplateSixteenJson(
            context = context,
            business = "pickup",
            ticker = contentText,
            enableFloat = true,
            showSmallIcon = false,
            aodTitle = contentText,
            chatTitle = order.takeoutCode,
            chatContent = displayBrand,
            hintTitle = hintTitle,
            islandTitle = order.takeoutCode,
            typeLabel = order.orderType,
            actionTitle = actionTitle,
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

    // ==================== 更新下载通知 ====================

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

        val frontTitle = if (isPaused) "更新下载已暂停" else "正在后台下载更新"

        val paramsJson = buildTemplateSixteenJson(
            context = context,
            business = "update",
            ticker = progressText,
            enableFloat = false,
            showSmallIcon = null,
            aodTitle = progressText,
            chatTitle = frontTitle,
            chatContent = "v$versionName · $progressText",
            hintTitle = "澎湃记",
            islandTitle = progressText,
            typeLabel = "更新",
            actionTitle = "查看",
        )

        val picsBundle = buildPicsBundle(context, R.mipmap.ic_launcher)
        val actionsBundle = buildActionsBundle(
            actionKey = ACT_VIEW,
            actionTitle = "查看",
            actionPendingIntent = contentIntent,
            identityPI = null,
            hasIdentity = false,
        )

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
        qrDetailPendingIntent: PendingIntent?,
        identityChooserPendingIntent: PendingIntent?
    ): Notification {
        val codes = orders.take(3).joinToString(", ") { it.takeoutCode }
        val more = if (orders.size > 3) " 等${orders.size}件" else ""
        val label = if (isExpress) "取件码" else "取餐码"
        val contentText = "$label: $codes$more"
        val iconRes = brandIconRes(context, group.brandName, group.orderType)
        val hasQrCode = orders.any { !it.qrCodeData.isNullOrBlank() } && qrDetailPendingIntent != null
        val hasIdentityAction = isExpress && identityChooserPendingIntent != null
        val actionKey = when {
            hasQrCode -> ACT_QR
            hasIdentityAction -> ACT_IDENTITY
            else -> ACT_COMPLETE
        }
        val actionTitle = when {
            hasQrCode -> "二维码"
            hasIdentityAction -> "身份码"
            else -> "已完成"
        }
        val actionPendingIntent = when {
            hasQrCode -> qrDetailPendingIntent
            hasIdentityAction -> identityChooserPendingIntent
            else -> completeAllPendingIntent
        }

        val picsBundle = buildPicsBundle(context, iconRes, brandIcon(context, group.brandName))
        val actionsBundle = buildActionsBundle(
            actionKey = ACT_HINT_V2,
            actionTitle = actionTitle,
            actionPendingIntent = requireNotNull(actionPendingIntent),
            identityPI = identityChooserPendingIntent,
            hasIdentity = isExpress && actionKey != ACT_IDENTITY,
        )
        val displayBrand = group.brandName ?: if (isExpress) "新包裹" else "新订单"
        val pickupLocation = orders.firstNotNullOfOrNull { order ->
            order.pickupLocation?.takeIf(String::isNotBlank)
        }
        val hintTitle = pickupHintTitle(pickupLocation, isExpress, group.orderType)
        val paramsJson = buildTemplateSixteenJson(
            context = context,
            business = "pickup",
            ticker = contentText,
            enableFloat = true,
            showSmallIcon = false,
            aodTitle = contentText,
            chatTitle = codes,
            chatContent = displayBrand,
            hintTitle = hintTitle,
            islandTitle = if (orders.size > 1) "${orders.size}件" else codes,
            typeLabel = group.orderType,
            actionTitle = actionTitle,
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
