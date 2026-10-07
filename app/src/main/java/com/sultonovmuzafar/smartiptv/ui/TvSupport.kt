package com.sultonovmuzafar.smartiptv.ui

import android.content.res.Configuration
import android.content.pm.PackageManager
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import java.util.Locale

@Composable fun isTelevision(): Boolean =
    (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
        LocalContext.current.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

/** Keep Material's keyboard behavior and add a visible outline to the focused target. */
fun Modifier.focusRing(shape: Shape=RoundedCornerShape(14.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused=it.hasFocus }.border(2.dp,if(focused) Mint else Color.Transparent,shape)
}

/** BoxWithConstraints subcomposes its children during layout; focus only after attachment. */
fun Modifier.initialFocus(requester: FocusRequester,enabled: Boolean,key: Any?=Unit): Modifier = composed {
    val requested=remember(enabled,key) { booleanArrayOf(false) }
    val input=LocalInputModeManager.current
    val keyboard=input.inputMode==InputMode.Keyboard
    focusRequester(requester).onGloballyPositioned {
        if(enabled && !requested[0]) {
            if(!keyboard) input.requestInputMode(InputMode.Keyboard)
            requested[0]=requester.requestFocus()
        }
    }
}

@Composable fun appLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable fun resumeTime(position: Long): String {
    val locale=appLocale()
    return BidiFormatter.getInstance(locale).unicodeWrap(playbackTime(position,locale),TextDirectionHeuristicsCompat.LTR)
}

fun playbackTime(position: Long,locale: Locale=Locale.getDefault()): String {
    val seconds=position.coerceAtLeast(0)/1000
    return if(seconds>=3600) String.format(locale,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
    else String.format(locale,"%d:%02d",seconds/60,seconds%60)
}
