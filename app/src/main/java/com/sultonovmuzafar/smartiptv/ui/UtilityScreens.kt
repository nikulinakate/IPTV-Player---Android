package com.sultonovmuzafar.smartiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sultonovmuzafar.smartiptv.R
import com.sultonovmuzafar.smartiptv.data.*
import java.text.DateFormat
import java.util.Date

@Composable fun OnboardingScreen(onFinish: ()->Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val titles=listOf(R.string.onboard_title_1,R.string.onboard_title_2,R.string.onboard_title_3)
    val descriptions=listOf(R.string.onboard_body_1,R.string.onboard_body_2,R.string.onboard_body_3)
    val icons=listOf(Icons.AutoMirrored.Rounded.PlaylistAdd,Icons.Rounded.LiveTv,Icons.Rounded.CastConnected)
    val compact=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp<500
    Surface(color=Canvas) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { BrandMark(42); Spacer(Modifier.weight(1f)); TextButton(onClick=onFinish) { Text(stringResource(R.string.skip)) } }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(if(compact) 12.dp else 24.dp)) {
                Box(Modifier.size(if(compact) 86.dp else 220.dp).background(Panel,RoundedCornerShape(if(compact) 22.dp else 48.dp)),contentAlignment=Alignment.Center) {
                    Icon(icons[page],null,Modifier.size(if(compact) 46.dp else 108.dp),tint=Mint)
                }
                Text(stringResource(titles[page]),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                Text(stringResource(descriptions[page]),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { repeat(3) { Box(Modifier.size(if(page==it) 24.dp else 8.dp,8.dp).background(if(page==it) Mint else MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(8.dp))) } }
                Button(onClick={if(page<2) page++ else onFinish()},modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(18.dp)) { Text(stringResource(if(page<2) R.string.continue_label else R.string.add_first_playlist)) }
            }
        }
    }
}
@Composable fun SettingsDialog(onClose: ()->Unit) {
    val context=LocalContext.current
    val prefs=remember { context.getSharedPreferences("settings",0) }
    var background by remember { mutableStateOf(prefs.getBoolean("background",false)) }
    var pip by remember { mutableStateOf(prefs.getBoolean("pip",false)) }
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(R.string.settings))},text={
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Text(stringResource(R.string.background_playback),Modifier.weight(1f)); Switch(background,{background=it;prefs.edit().putBoolean("background",it).apply()}) }
            Row(verticalAlignment=Alignment.CenterVertically) { Text(stringResource(R.string.auto_pip),Modifier.weight(1f)); Switch(pip,{pip=it;prefs.edit().putBoolean("pip",it).apply()}) }
            HorizontalDivider()
            Text(stringResource(R.string.cast_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.content_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("1.0.0",style=MaterialTheme.typography.labelSmall,color=Mint)
        }
    },confirmButton={TextButton(onClick=onClose) { Text(stringResource(R.string.done)) }})
}
@Composable fun EpisodeDialog(episodes: List<Channel>,onPlay: (Channel)->Unit,onClose: ()->Unit) {
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(R.string.episodes))},text={
        LazyColumn(Modifier.heightIn(max=450.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(episodes,key={it.id}) { episode -> Card(onClick={onPlay(episode)}) { Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(stringResource(R.string.season,episode.group),style=MaterialTheme.typography.labelSmall,color=Mint)
                Text(episode.name)
            } } }
        }
    },confirmButton={TextButton(onClick=onClose) { Text(stringResource(R.string.done)) }})
}
@Composable fun GuideDialog(channel: Channel?,programmes: List<Programme>,onClose: ()->Unit) {
    val formatter=remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    AlertDialog(onDismissRequest=onClose,title={Text(channel?.name ?: stringResource(R.string.guide))},text={
        if(programmes.isEmpty()) Text(stringResource(R.string.epg_empty))
        else LazyColumn(Modifier.heightIn(max=450.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            items(programmes) { programme -> Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("${formatter.format(Date(programme.start))} – ${formatter.format(Date(programme.stop))}",style=MaterialTheme.typography.labelLarge,color=Mint)
                Text(programme.title,fontWeight=FontWeight.SemiBold)
                if(programme.description.isNotBlank()) Text(programme.description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
    },confirmButton={TextButton(onClick=onClose) { Text(stringResource(R.string.done)) }})
}
