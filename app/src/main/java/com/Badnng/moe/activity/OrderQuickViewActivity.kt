package com.Badnng.moe.activity

import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.asImageBitmap
import com.Badnng.moe.R
import com.Badnng.moe.data.db.OrderDatabase
import com.Badnng.moe.data.db.OrderEntity
import com.Badnng.moe.helper.EdgeToEdgeHelper
import com.Badnng.moe.helper.NotificationHelper
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import com.Badnng.moe.ui.theme.澎湃记Theme
import kotlinx.coroutines.launch
import java.io.File
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

class OrderQuickViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.applyGestureEdgeToEdge(this)

        val orderId = intent.getStringExtra("order_id") ?: ""
        val fromNotification = intent.getBooleanExtra("from_notification", false)

        setContent {
            澎湃记Theme {
                OrderQuickViewScreen(
                    orderId = orderId,
                    fromNotification = fromNotification,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
fun OrderQuickViewScreen(
    orderId: String,
    fromNotification: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var order by remember { mutableStateOf<OrderEntity?>(null) }

    LaunchedEffect(orderId) {
        if (orderId.isNotEmpty()) {
            order = OrderDatabase.getDatabase(context).orderDao().getOrderById(orderId)
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 背景遮罩层，点击背景关闭（全屏，不含安全边距）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (fromNotification) {
                        (context as? ComponentActivity)?.moveTaskToBack(true)
                    }
                    onDismiss()
                }
        )

        // 卡片内容（应用安全边距）
        Box(modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
            if (order != null) {
                QuickViewDialogContent(
                    order = order!!,
                    onDismiss = {
                        if (fromNotification) {
                            (context as? ComponentActivity)?.moveTaskToBack(true)
                        }
                        onDismiss()
                    }
                )
        } else {
            val isMiuix = rememberMiuixStyle()
            if (isMiuix) {
                InfiniteProgressIndicator(
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
            }
        }
        }
    }
}

@Composable
fun QuickViewDialogContent(order: OrderEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isMiuix = rememberMiuixStyle()
    val isExpress = order.orderType == "快递"
    val label = if (isExpress) "取件码" else "取餐码"
    val scope = rememberCoroutineScope()

    val markCompleted: () -> Unit = {
        val orderId = order.id
        val completedTime = System.currentTimeMillis()
        val database = OrderDatabase.getDatabase(context)
        val notificationHelper = NotificationHelper(context)
        scope.launch {
            val groupId = order.groupId
            database.orderDao().markAsCompleted(orderId, completedTime)
            notificationHelper.cancelNotification(orderId)
            if (groupId != null) {
                val orderDao = database.orderDao()
                val groupDao = database.orderGroupDao()
                val incompleteCount = orderDao.getAllOrdersList().count { it.groupId == groupId && !it.isCompleted }
                if (incompleteCount < 2) {
                    val remaining = orderDao.getAllOrdersList().filter { it.groupId == groupId }
                    val group = groupDao.getGroupById(groupId)
                    remaining.forEach { orderDao.update(it.copy(groupId = null)) }
                    if (group != null) groupDao.deleteGroup(group)
                    notificationHelper.cancelGroupNotification(groupId)
                } else {
                    groupDao.updateOrderCount(groupId, incompleteCount)
                }
            }
            onDismiss()
        }
    }

    // 动画状态
    var animationPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animationPlayed = true
    }

    val brandIcon = remember(order.brandName, order.orderType) {
        val resName = when (order.brandName) {
            "麦当劳" -> "ic_mcdonalds"
            "肯德基", "KFC" -> "ic_kfc"
            "瑞幸" -> "ic_luckin"
            "喜茶" -> "ic_heytea"
            "星巴克" -> "ic_starbucks"
            "霸王茶姬" -> "ic_chagee"
            "古茗" -> "ic_goodme"
            "蜜雪冰城" -> "ic_mixue"
            "库迪" -> "ic_cotti"
            "茶百道" -> "ic_chabaidao"
            "奈雪", "奈雪的茶" -> "ic_naixue"
            "汉堡王" -> "ic_burger_king"
            "塔斯汀" -> "ic_tastien"
            "顺丰" -> "ic_sf_express"
            "菜鸟" -> "ic_cainiao"
            "丰巢" -> "ic_hive_box"
            "中国邮政" -> "ic_china_post"
            "沪上阿姨" -> "ic_hushang_ayi"
            "CoCo" -> "ic_coco"
            "华莱士" -> "ic_wallace"
            "老乡鸡" -> "ic_lao_xiang_ji"
            else -> null
        }
        val resId = if (resName != null) context.resources.getIdentifier(resName, "drawable", context.packageName) else 0
        if (resId != 0) resId else when (order.orderType) {
            "饮品" -> R.drawable.ic_drink
            "快递" -> R.drawable.ic_package
            else -> R.drawable.ic_restaurant
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 弹出动画
    val scale by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0.8f,
        animationSpec = tween(durationMillis = 300, easing = EaseOutBack),
        label = "scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = EaseOut),
        label = "alpha"
    )

    // 颜色
    val surfaceColor = if (isMiuix) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surface
    val onSurfaceColor = if (isMiuix) MiuixTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface
    val primaryColor = if (isMiuix) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
    val secondaryTextColor = if (isMiuix) MiuixTheme.colorScheme.onSurfaceVariantSummary else MaterialTheme.colorScheme.onSurfaceVariant
    val hintTextColor = secondaryTextColor.copy(alpha = 0.75f)
    val isDarkPalette = surfaceColor.luminance() < 0.5f
    val qrBackgroundColor = if (isDarkPalette) Color.White else Color.Transparent

    // 卡片
    if (isMiuix) {
        MiuixCard(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.7f else 0.85f)
                .widthIn(max = if (isLandscape) 600.dp else 450.dp)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* 消费点击事件，阻止穿透到背景 */ }
        ) {
            if (isLandscape) {
                LandscapeContent(order, isExpress, label, brandIcon, primaryColor, onSurfaceColor, secondaryTextColor, hintTextColor, qrBackgroundColor, markCompleted, onDismiss, isMiuix)
            } else {
                PortraitContent(order, isExpress, label, brandIcon, primaryColor, onSurfaceColor, secondaryTextColor, hintTextColor, qrBackgroundColor, markCompleted, onDismiss, isMiuix)
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.7f else 0.85f)
                .widthIn(max = if (isLandscape) 600.dp else 450.dp)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* 消费点击事件，阻止穿透到背景 */ },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            if (isLandscape) {
                LandscapeContent(order, isExpress, label, brandIcon, primaryColor, onSurfaceColor, secondaryTextColor, hintTextColor, qrBackgroundColor, markCompleted, onDismiss, isMiuix)
            } else {
                PortraitContent(order, isExpress, label, brandIcon, primaryColor, onSurfaceColor, secondaryTextColor, hintTextColor, qrBackgroundColor, markCompleted, onDismiss, isMiuix)
            }
        }
    }
}

@Composable
private fun LandscapeContent(
    order: OrderEntity,
    isExpress: Boolean,
    label: String,
    brandIcon: Int,
    primaryColor: Color,
    onSurfaceColor: Color,
    secondaryTextColor: Color,
    hintTextColor: Color,
    qrBackgroundColor: Color,
    markCompleted: () -> Unit,
    onDismiss: () -> Unit,
    isMiuix: Boolean
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Row(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左边：取餐信息
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BrandInfo(order, brandIcon, onSurfaceColor, isMiuix)
                Spacer(modifier = Modifier.height(12.dp))
                CodeLabel(label, secondaryTextColor, isMiuix)
                Spacer(modifier = Modifier.height(6.dp))
                TakeoutCode(order, primaryColor, 24.sp, isMiuix)
                PickupLocation(order, primaryColor, secondaryTextColor, isMiuix)
                Spacer(modifier = Modifier.height(8.dp))
                HintText(hintTextColor, isMiuix)
            }

            // 分隔线
            if (isMiuix) {
                VerticalDivider(modifier = Modifier.height(200.dp), color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f))
            } else {
                VerticalDivider(modifier = Modifier.height(200.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // 右边：二维码或截图
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                QrSectionTitle(order, primaryColor, isMiuix)
                QrContent(order, qrBackgroundColor, isMiuix)
            }
        }

        if (isExpress) {
            Spacer(modifier = Modifier.height(12.dp))
            IdentityButtons(isMiuix)
        }

        Spacer(modifier = Modifier.height(12.dp))
        ActionButtons(markCompleted, onDismiss, isMiuix)
    }
}

@Composable
private fun PortraitContent(
    order: OrderEntity,
    isExpress: Boolean,
    label: String,
    brandIcon: Int,
    primaryColor: Color,
    onSurfaceColor: Color,
    secondaryTextColor: Color,
    hintTextColor: Color,
    qrBackgroundColor: Color,
    markCompleted: () -> Unit,
    onDismiss: () -> Unit,
    isMiuix: Boolean
) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandInfo(order, brandIcon, onSurfaceColor, isMiuix)
            Spacer(modifier = Modifier.height(16.dp))
            CodeLabel(label, secondaryTextColor, isMiuix)
            Spacer(modifier = Modifier.height(4.dp))
            TakeoutCode(order, primaryColor, 28.sp, isMiuix)
            PickupLocation(order, primaryColor, secondaryTextColor, isMiuix)
            Spacer(modifier = Modifier.height(8.dp))
            HintText(hintTextColor, isMiuix)

            // 分隔线
            if (isMiuix) {
                top.yukonga.miuix.kmp.basic.HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            } else {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            QrSectionTitle(order, primaryColor, isMiuix)
            QrContent(order, qrBackgroundColor, isMiuix)

            if (isExpress) {
                Spacer(modifier = Modifier.height(12.dp))
                IdentityButtons(isMiuix)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ActionButtons(markCompleted, onDismiss, isMiuix)
    }
}

@Composable
private fun BrandInfo(order: OrderEntity, brandIcon: Int, onSurfaceColor: Color, isMiuix: Boolean) {
    val isExpress = order.orderType == "快递"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isMiuix) {
            MiuixIcon(painter = painterResource(id = brandIcon), contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.Unspecified)
            Spacer(modifier = Modifier.width(12.dp))
            MiuixText(text = order.brandName ?: if (isExpress) "快递" else "取餐码", style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Bold, color = onSurfaceColor)
        } else {
            Icon(painter = painterResource(id = brandIcon), contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.Unspecified)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = order.brandName ?: if (isExpress) "快递" else "取餐码", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = onSurfaceColor)
        }
    }
}

@Composable
private fun CodeLabel(label: String, secondaryTextColor: Color, isMiuix: Boolean) {
    if (isMiuix) {
        MiuixText(text = label, style = MiuixTheme.textStyles.body2, color = secondaryTextColor)
    } else {
        Text(text = label, fontSize = 12.sp, color = secondaryTextColor, letterSpacing = 1.2.sp)
    }
}

@Composable
private fun TakeoutCode(order: OrderEntity, primaryColor: Color, fontSize: androidx.compose.ui.unit.TextUnit, isMiuix: Boolean) {
    if (isMiuix) {
        MiuixText(
            text = order.takeoutCode,
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.ExtraBold,
            color = primaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        Text(
            text = order.takeoutCode,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            color = primaryColor,
            letterSpacing = if (fontSize == 28.sp) 4.sp else 2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PickupLocation(order: OrderEntity, primaryColor: Color, secondaryTextColor: Color, isMiuix: Boolean) {
    if (!order.pickupLocation.isNullOrEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (isMiuix) {
                MiuixIcon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = primaryColor.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.width(4.dp))
                MiuixText(text = order.pickupLocation!!, style = MiuixTheme.textStyles.body2, color = secondaryTextColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = primaryColor.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = order.pickupLocation!!, fontSize = 13.sp, color = secondaryTextColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HintText(hintTextColor: Color, isMiuix: Boolean) {
    if (isMiuix) {
        MiuixText(text = "请向商家出示此码", style = MiuixTheme.textStyles.body2, color = hintTextColor)
    } else {
        Text(text = "请向商家出示此码", fontSize = 12.sp, color = hintTextColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QrSectionTitle(order: OrderEntity, primaryColor: Color, isMiuix: Boolean) {
    val title = if (!order.qrCodeData.isNullOrEmpty()) "二维码" else "截图副本"
    if (isMiuix) {
        MiuixText(text = title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Bold, color = primaryColor, modifier = Modifier.padding(bottom = 8.dp))
    } else {
        Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primaryColor, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun QrContent(order: OrderEntity, qrBackgroundColor: Color, isMiuix: Boolean) {
    val context = LocalContext.current
    if (!order.qrCodeData.isNullOrEmpty()) {
        val qrBitmap = remember(order.qrCodeData) {
            try {
                val writer = com.google.zxing.qrcode.QRCodeWriter()
                val bitMatrix = writer.encode(order.qrCodeData, com.google.zxing.BarcodeFormat.QR_CODE, 200, 200)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
                bitmap
            } catch (e: Exception) { null }
        }
        if (qrBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "二维码",
                modifier = Modifier.size(250.dp).clip(RoundedCornerShape(12.dp)).background(qrBackgroundColor)
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                if (isMiuix) MiuixText("二维码生成失败", color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.body2)
                else Text("二维码生成失败", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    } else if (!order.screenshotPath.isNullOrEmpty() && File(order.screenshotPath).exists()) {
        AsyncImage(
            model = File(order.screenshotPath),
            contentDescription = "原图",
            modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isMiuix) {
                    MiuixIcon(painter = painterResource(id = R.drawable.note), contentDescription = null, modifier = Modifier.size(32.dp).alpha(0.3f), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(modifier = Modifier.height(4.dp))
                    MiuixText("暂无图片数据", color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f), style = MiuixTheme.textStyles.body2)
                } else {
                    Icon(painter = painterResource(id = R.drawable.note), contentDescription = null, modifier = Modifier.size(32.dp).alpha(0.3f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("暂无图片数据", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun IdentityButtons(isMiuix: Boolean) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isMiuix) {
            MiuixButton(
                onClick = { openTaobaoIdentityEntry(context) },
                modifier = Modifier.fillMaxWidth(),
                colors = MiuixButtonDefaults.buttonColors(color = Color(0xFFFF8A00), contentColor = Color.White)
            ) { MiuixText("打开淘宝身份码") }
            MiuixButton(
                onClick = { openPddIdentityEntry(context) },
                modifier = Modifier.fillMaxWidth(),
                colors = MiuixButtonDefaults.buttonColors(color = Color(0xFFE53935), contentColor = Color.White)
            ) { MiuixText("打开拼多多身份码") }
        } else {
            Button(
                onClick = { openTaobaoIdentityEntry(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A00), contentColor = Color.White)
            ) { Text("打开淘宝身份码") }
            Button(
                onClick = { openPddIdentityEntry(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935), contentColor = Color.White)
            ) { Text("打开拼多多身份码") }
        }
    }
}

@Composable
private fun ActionButtons(markCompleted: () -> Unit, onDismiss: () -> Unit, isMiuix: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isMiuix) {
            MiuixButton(onClick = onDismiss, modifier = Modifier.weight(1f), colors = MiuixButtonDefaults.buttonColors()) { MiuixText("关闭") }
            MiuixButton(onClick = markCompleted, modifier = Modifier.weight(1f), colors = MiuixButtonDefaults.buttonColorsPrimary()) { MiuixText("已完成") }
        } else {
            Button(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("关闭", fontSize = 16.sp) }
            OutlinedButton(onClick = markCompleted, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { Text("已完成", fontSize = 16.sp) }
        }
    }
}

private fun openTaobaoIdentityEntry(context: android.content.Context) {
    val pkg = "com.taobao.taobao"
    val lastmile = "https://pages-fast.m.taobao.com/wow/z/uniapp/1100333/last-mile-fe/m-end-school-tab/home"
    val candidates = listOf("tbopen://m.taobao.com/tbopen/index.html?h5Url=" + Uri.encode(lastmile))
    for (u in candidates) {
        try {
            val i = Intent(Intent.ACTION_VIEW, u.toUri())
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            return
        } catch (_: Exception) {}
    }
    try {
        val i = Intent(Intent.ACTION_VIEW, lastmile.toUri())
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.setClassName(pkg, "com.taobao.browser.BrowserActivity")
        context.startActivity(i)
    } catch (_: Exception) {}
}

private fun openPddIdentityEntry(context: android.content.Context) {
    val pkg = "com.xunmeng.pinduoduo"
    val schemes = listOf("pinduoduo://com.xunmeng.pinduoduo/mdkd/package", "pinduoduo://com.xunmeng.pinduoduo/", "pinduoduo://")
    for (u in schemes) {
        try {
            val i = Intent(Intent.ACTION_VIEW, u.toUri())
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            return
        } catch (_: Exception) {}
    }
    try {
        val i = context.packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    } catch (_: Exception) {}
}
