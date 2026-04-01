package com.Badnng.moe

import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.asImageBitmap
import com.Badnng.moe.ui.theme.澎湃记Theme
import kotlinx.coroutines.launch
import java.io.File

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
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 背景遮罩层，点击关闭
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
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
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun QuickViewDialogContent(order: OrderEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isExpress = order.orderType == "快递"
    val label = if (isExpress) "取件码" else "取餐码"
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val hintTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val isDarkPalette = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val qrBackgroundColor = if (isDarkPalette) Color.White else Color.Transparent
    val scope = rememberCoroutineScope()
    val markCompleted: () -> Unit = {
        val orderId = order.id
        val completedTime = System.currentTimeMillis()
        val database = OrderDatabase.getDatabase(context)
        val notificationHelper = NotificationHelper(context)
        scope.launch {
            database.orderDao().markAsCompleted(orderId, completedTime)
            notificationHelper.cancelNotification(orderId)
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
        animationSpec = tween(
            durationMillis = 300,
            easing = EaseOutBack
        ),
        label = "scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = EaseOut
        ),
        label = "alpha"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth(if (isLandscape) 0.65f else 0.85f)
            .widthIn(max = if (isLandscape) 350.dp else 450.dp)
            .wrapContentHeight()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                alpha = alpha
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        if (isLandscape) {
            // 横屏布局：上半部分左右分栏，下半部分关闭按钮
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // 上半部分：左右分栏
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // 左边：取餐信息
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 品牌信息
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(id = brandIcon),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = Color.Unspecified
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = order.brandName ?: if (isExpress) "快递" else "取餐码",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 取餐码/取件码
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = secondaryTextColor,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = order.takeoutCode,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // 取货地点（如果有）
                        if (!order.pickupLocation.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = order.pickupLocation!!,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请向商家出示此码",
                            fontSize = 13.sp,
                            color = hintTextColor,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 分隔线（竖向）
                    VerticalDivider(
                        modifier = Modifier.height(250.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // 右边：二维码或截图副本
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (!order.qrCodeData.isNullOrEmpty()) "二维码" else "截图副本",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (!order.qrCodeData.isNullOrEmpty()) {
                            // 显示二维码
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
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            
                            if (qrBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "二维码",
                                    modifier = Modifier
                                        .size(200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(qrBackgroundColor)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "二维码生成失败",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else if (!order.screenshotPath.isNullOrEmpty() && File(order.screenshotPath).exists()) {
                            // 显示截图副本
                            AsyncImage(
                                model = File(order.screenshotPath),
                                contentDescription = "原图",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.note),
                                        contentDescription = null,
                                        modifier = Modifier.size(50.dp).alpha(0.3f),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "暂无图片数据",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 下半部分：已完成和关闭按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 关闭按钮
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("关闭", fontSize = 16.sp)
                    }
                    
                    // 已完成按钮
                    OutlinedButton(
                        onClick = markCompleted,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("已完成", fontSize = 16.sp)
                    }
                }

                if (isExpress) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { openTaobaoIdentityEntry(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("打开淘宝身份码")
                        }
                        OutlinedButton(
                            onClick = { openPddIdentityEntry(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("打开拼多多身份码")
                        }
                    }
                }
            }
        } else {
            // 竖屏布局：保持原有样式
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 品牌信息
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = brandIcon),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = order.brandName ?: if (isExpress) "快递" else "取餐码",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 取餐码/取件码
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = secondaryTextColor,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = order.takeoutCode,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 4.sp
                )

                // 取货地点（如果有）
                if (!order.pickupLocation.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = order.pickupLocation!!,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "请向商家出示此码",
                    fontSize = 12.sp,
                    color = hintTextColor,
                    fontWeight = FontWeight.Medium
                )

                // 分隔线
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // 二维码或截图副本
                Text(
                    text = if (!order.qrCodeData.isNullOrEmpty()) "二维码" else "截图副本",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (!order.qrCodeData.isNullOrEmpty()) {
                    // 显示二维码
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
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    if (qrBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "二维码",
                            modifier = Modifier
                                .size(250.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(qrBackgroundColor)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "二维码生成失败",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else if (!order.screenshotPath.isNullOrEmpty() && File(order.screenshotPath).exists()) {
                    // 显示截图副本
                    AsyncImage(
                        model = File(order.screenshotPath),
                        contentDescription = "原图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(id = R.drawable.note),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp).alpha(0.3f),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "暂无图片数据",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 关闭和已完成按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 关闭按钮
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("关闭", fontSize = 16.sp)
                    }
                    
                    // 已完成按钮
                    OutlinedButton(
                        onClick = markCompleted,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("已完成", fontSize = 16.sp)
                    }
                }

                if (isExpress) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { openTaobaoIdentityEntry(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("打开淘宝身份码")
                        }
                        OutlinedButton(
                            onClick = { openPddIdentityEntry(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("打开拼多多身份码")
                        }
                    }
                }
            }
        }
    }
}

private fun openTaobaoIdentityEntry(context: android.content.Context) {
    val pkg = "com.taobao.taobao"
    val lastmile = "https://pages-fast.m.taobao.com/wow/z/uniapp/1100333/last-mile-fe/m-end-school-tab/home"
    val candidates = listOf(
        "tbopen://m.taobao.com/tbopen/index.html?h5Url=" + Uri.encode(lastmile)
    )
    for (u in candidates) {
        try {
            val i = Intent(Intent.ACTION_VIEW, u.toUri())
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            return
        } catch (_: Exception) {
        }
    }
    try {
        val i = Intent(Intent.ACTION_VIEW, lastmile.toUri())
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.setClassName(pkg, "com.taobao.browser.BrowserActivity")
        context.startActivity(i)
    } catch (_: Exception) {
    }
}

private fun openPddIdentityEntry(context: android.content.Context) {
    val pkg = "com.xunmeng.pinduoduo"
    val schemes = listOf(
        "pinduoduo://com.xunmeng.pinduoduo/mdkd/package",
        "pinduoduo://com.xunmeng.pinduoduo/",
        "pinduoduo://"
    )
    for (u in schemes) {
        try {
            val i = Intent(Intent.ACTION_VIEW, u.toUri())
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            return
        } catch (_: Exception) {
        }
    }
    try {
        val i = context.packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    } catch (_: Exception) {
    }
}
