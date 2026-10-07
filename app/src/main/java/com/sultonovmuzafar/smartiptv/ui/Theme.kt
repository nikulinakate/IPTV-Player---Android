package com.sultonovmuzafar.smartiptv.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Mint = Color(0xFF9FF3CE)
val Canvas = Color(0xFF0C1017)
val Panel = Color(0xFF171E29)
@Composable fun IPTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Mint,onPrimary=Canvas,background=Canvas,surface=Panel,surfaceVariant=Color(0xFF232D3C),onSurface=Color(0xFFF4F6FA),onSurfaceVariant=Color(0xFF9EAABD),secondary=Color(0xFFA8BAFA)),content=content)
}
