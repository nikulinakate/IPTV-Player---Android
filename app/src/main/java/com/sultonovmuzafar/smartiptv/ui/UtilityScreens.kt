package com.sultonovmuzafar.smartiptv.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@Composable fun SettingsDialog(onClose: ()->Unit) {
    val context=LocalContext.current
    val prefs=remember { context.getSharedPreferences("settings",0) }
    var background by remember { mutableStateOf(prefs.getBoolean("background",false)) }
    var pip by remember { mutableStateOf(prefs.getBoolean("pip",false)) }
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(R.string.settings))},text={
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Text(stringResource(R.string.background_playback),Modifier.weight(1f)); Switch(background,modifier=Modifier.focusRing(),onCheckedChange={background=it;prefs.edit().putBoolean("background",it).apply()}) }
            Row(verticalAlignment=Alignment.CenterVertically) { Text(stringResource(R.string.auto_pip),Modifier.weight(1f)); Switch(pip,modifier=Modifier.focusRing(),onCheckedChange={pip=it;prefs.edit().putBoolean("pip",it).apply()}) }
            HorizontalDivider()
            Text(stringResource(R.string.cast_help),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.content_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("1.0.0",style=MaterialTheme.typography.labelSmall,color=Mint)
        }
    },confirmButton={TextButton(modifier=Modifier.focusRing(),onClick=onClose) { Text(stringResource(R.string.done)) }})
}
@Composable fun EpisodeDialog(episodes: List<Channel>,onPlay: (Channel)->Unit,onClose: ()->Unit) {
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(R.string.episodes))},text={
        LazyColumn(Modifier.heightIn(max=450.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(episodes,key={it.id}) { episode -> Card(onClick={onPlay(episode)},modifier=Modifier.focusRing()) { Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(stringResource(R.string.season,episode.group),style=MaterialTheme.typography.labelSmall,color=Mint)
                Text(episode.name)
            } } }
        }
    },confirmButton={TextButton(modifier=Modifier.focusRing(),onClick=onClose) { Text(stringResource(R.string.done)) }})
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
    },confirmButton={TextButton(modifier=Modifier.focusRing(),onClick=onClose) { Text(stringResource(R.string.done)) }})
}
