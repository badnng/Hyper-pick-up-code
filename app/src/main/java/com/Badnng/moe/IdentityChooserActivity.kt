package com.Badnng.moe

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.Badnng.moe.ui.theme.澎湃记Theme

class IdentityChooserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            澎湃记Theme {
                IdentityChooserScreen(
                    onTaobao = { openTaobaoIdentityEntry(); finish() },
                    onPdd = { openPddIdentityEntry(); finish() },
                    onClose = { finish() }
                )
            }
        }
    }

    private fun openTaobaoIdentityEntry() {
        val pkg = "com.taobao.taobao"
        val lastmile = "https://pages-fast.m.taobao.com/wow/z/uniapp/1100333/last-mile-fe/m-end-school-tab/home"
        val candidates = listOf(
            "tbopen://m.taobao.com/tbopen/index.html?h5Url=" + Uri.encode(lastmile)
        )
        for (u in candidates) {
            try {
                val i = Intent(Intent.ACTION_VIEW, u.toUri())
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                return
            } catch (_: Exception) {
            }
        }
        try {
            val i = Intent(Intent.ACTION_VIEW, lastmile.toUri())
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.setClassName(pkg, "com.taobao.browser.BrowserActivity")
            startActivity(i)
        } catch (_: Exception) {
        }
    }

    private fun openPddIdentityEntry() {
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
                startActivity(i)
                return
            } catch (_: Exception) {
            }
        }
        try {
            val i = packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            }
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun IdentityChooserScreen(
    onTaobao: () -> Unit,
    onPdd: () -> Unit,
    onClose: () -> Unit
) {
    var animationPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animationPlayed = true }
    val scale by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0.88f,
        animationSpec = tween(durationMillis = 260, easing = EaseOutBack),
        label = "chooser_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = EaseOut),
        label = "chooser_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    alpha = alpha
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("选择要打开的身份码", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onTaobao,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("淘宝身份码")
                }
                OutlinedButton(
                    onClick = onPdd,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("拼多多身份码")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("关闭")
                }
            }
        }
    }
}
