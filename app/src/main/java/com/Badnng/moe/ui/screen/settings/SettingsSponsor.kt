package com.Badnng.moe.ui.screen.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SponsorSettingsContent(
    topPadding: Dp = 0.dp,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState()
) {
    val context = LocalContext.current
    val isMiuix = rememberMiuixStyle()
    val alipayImage = remember {
        runCatching {
            context.assets.open("sponsor/Alipay.jpg").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }
    val wechatImage = remember {
        runCatching {
            context.assets.open("sponsor/Wechat.png").use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isMiuix) 0.dp else 16.dp)
            .verticalScroll(scrollState)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(topPadding))

        if (isMiuix) {
            MiuixCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                MiuixText(
                    text = "感谢您使用我的项目，项目制作花费的时间精力很大，在上学期间做的小项目，软件完全免费，如果倒卖请联系退款并举报！！",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "感谢您使用我的项目，项目制作花费的时间精力很大，在上学期间做的小项目，软件完全免费，如果倒卖请联系退款并举报！！",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SponsorImageCard(alipayImage, "支付宝赞助码", isMiuix)
        if (alipayImage != null && wechatImage != null) {
            Spacer(modifier = Modifier.height(12.dp))
        }
        SponsorImageCard(wechatImage, "微信赞助码", isMiuix)

        if (alipayImage == null && wechatImage == null) {
            Spacer(modifier = Modifier.height(12.dp))
            if (isMiuix) {
                MiuixText(
                    text = "未找到赞助图片资源",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                Text(
                    text = "未找到赞助图片资源",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SponsorImageCard(image: ImageBitmap?, contentDescription: String, isMiuix: Boolean) {
    image ?: return
    if (isMiuix) {
        MiuixCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
        }
    }
}
