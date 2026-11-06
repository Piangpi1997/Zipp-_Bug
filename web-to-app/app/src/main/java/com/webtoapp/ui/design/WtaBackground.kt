package com.webtoapp.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun WtaBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val bg = MaterialTheme.colorScheme.background
    val tint = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(SolidColor(bg))
                drawRect(Brush.linearGradient(listOf(tint.copy(alpha = 0.08f), Color.Transparent, tint.copy(alpha = 0.04f))))
            },
        content = content
    )
}
