package com.sultonovmuzafar.smartiptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sultonovmuzafar.smartiptv.R

@Composable fun OnboardingScreen(onFinish: ()->Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val titles=listOf(R.string.onboard_title_1,R.string.onboard_title_2,R.string.onboard_title_3)
    val bodies=listOf(R.string.onboard_body_1,R.string.onboard_body_2,R.string.onboard_body_3)
    val hints=listOf(R.string.onboard_hint_1,R.string.onboard_hint_2,R.string.onboard_hint_3)
    val tv=isTelevision()
    val nextFocus=remember { FocusRequester() }
    BackHandler(page>0) { page-- }
    Surface(color=Canvas) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide=maxWidth>700.dp && maxHeight>400.dp
            val artHeight=if(maxHeight<600.dp) 240.dp else 260.dp
            Column(Modifier.fillMaxSize().padding(if(tv) 40.dp else 24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    BrandMark(40)
                    Text(stringResource(R.string.app_name),Modifier.weight(1f).padding(start=12.dp),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    TextButton(onClick=onFinish,modifier=Modifier.focusRing()) { Text(stringResource(R.string.skip)) }
                }
                if(wide) Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(36.dp),verticalAlignment=Alignment.CenterVertically) {
                    OnboardingArtwork(page,Modifier.weight(1f).heightIn(min=240.dp,max=340.dp))
                    OnboardingCopy(page,titles[page],bodies[page],hints[page],Modifier.weight(1f).verticalScroll(rememberScrollState()))
                } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                    OnboardingArtwork(page,Modifier.fillMaxWidth().height(artHeight))
                    OnboardingCopy(page,titles[page],bodies[page],hints[page])
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.align(Alignment.CenterHorizontally)) {
                    repeat(3) { Box(Modifier.size(if(page==it) 28.dp else 8.dp,6.dp).background(if(page==it) Mint else MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(6.dp))) }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(page>0) OutlinedButton(onClick={page--},modifier=Modifier.height(56.dp).focusRing()) { Text(stringResource(R.string.back)) }
                    Button(onClick={if(page<2) page++ else onFinish()},modifier=Modifier.weight(1f).height(56.dp).initialFocus(nextFocus,tv,page).focusRing(),shape=RoundedCornerShape(16.dp)) {
                        Text(stringResource(if(page<2) R.string.continue_label else R.string.add_first_playlist))
                    }
                }
            }
        }
    }
}

@Composable private fun OnboardingCopy(page: Int,title: Int,body: Int,hint: Int,modifier: Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.step_of,page+1,3),style=MaterialTheme.typography.labelLarge,color=Mint)
        Text(stringResource(title),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
        Text(stringResource(body),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color=Panel,shape=RoundedCornerShape(16.dp)) { Text(stringResource(hint),Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium) }
    }
}
