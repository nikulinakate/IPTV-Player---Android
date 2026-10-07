package com.sultonovmuzafar.smartiptv.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

val Mint = Color(0xFF9FF3CE)
val Canvas = Color(0xFF0C1017)
val Panel = Color(0xFF171E29)
@Composable fun IPTVTheme(content: @Composable () -> Unit) {
    val base=Typography()
    val typography=if(isTelevision()) base.copy(bodyLarge=base.bodyLarge.copy(fontSize=20.sp,lineHeight=28.sp),bodyMedium=base.bodyMedium.copy(fontSize=18.sp,lineHeight=26.sp),bodySmall=base.bodySmall.copy(fontSize=16.sp,lineHeight=22.sp),labelLarge=base.labelLarge.copy(fontSize=18.sp,lineHeight=24.sp),labelMedium=base.labelMedium.copy(fontSize=16.sp,lineHeight=22.sp),labelSmall=base.labelSmall.copy(fontSize=14.sp,lineHeight=20.sp)) else base
    MaterialTheme(colorScheme=darkColorScheme(primary=Mint,onPrimary=Canvas,primaryContainer=Color(0xFF25433B),onPrimaryContainer=Mint,secondaryContainer=Color(0xFF25433B),onSecondaryContainer=Mint,outline=Color(0xFF465263),outlineVariant=Color(0xFF2C3544),background=Canvas,surface=Panel,surfaceVariant=Color(0xFF232D3C),onSurface=Color(0xFFF4F6FA),onSurfaceVariant=Color(0xFF9EAABD),secondary=Color(0xFFA8BAFA)),typography=typography,content=content)
}
