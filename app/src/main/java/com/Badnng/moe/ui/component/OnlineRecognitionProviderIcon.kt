package com.Badnng.moe.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.Badnng.moe.recognition.OnlineRecognitionProvider

@Composable
internal fun OnlineRecognitionProviderIcon(
    provider: OnlineRecognitionProvider,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when (provider) {
        OnlineRecognitionProvider.OPENAI -> Color(0xFF111111)
        OnlineRecognitionProvider.OPENCODE_ZEN -> Color(0xFFDCF3DC)
        OnlineRecognitionProvider.CUSTOM -> Color(0xFF455A64)
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(provider.iconRes),
            contentDescription = provider.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    if (provider == OnlineRecognitionProvider.OPENAI ||
                        provider == OnlineRecognitionProvider.OPENCODE_ZEN ||
                        provider == OnlineRecognitionProvider.CUSTOM
                    ) 4.dp else 0.dp,
                ),
        )
    }
}
