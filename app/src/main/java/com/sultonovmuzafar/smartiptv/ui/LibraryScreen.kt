package com.sultonovmuzafar.smartiptv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sultonovmuzafar.smartiptv.R
import com.sultonovmuzafar.smartiptv.data.*
import com.sultonovmuzafar.smartiptv.playback.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun LibraryScreen(model: LibraryViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val prefs=remember { context.getSharedPreferences("settings",0) }
    var onboarding by rememberSaveable { mutableStateOf(!prefs.getBoolean("onboarded",false)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var add by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sourceFilter by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("LIVE") }
    val snackbar=remember { SnackbarHostState() }
    val errorText=state.error?.let { stringResource(errorResource(it)) }
    val message=state.message?.let { stringResource(when(it) { "added"->R.string.source_added; "epg"->R.string.epg_updated; else->R.string.source_updated }) }
    LaunchedEffect(errorText,message) {
        if(errorText!=null && add) return@LaunchedEffect
        if(message!=null) add=false
        (errorText ?: message)?.let { snackbar.showSnackbar(it); model.clearFeedback() }
    }
    val groups=remember(state.channels,sourceFilter,kind) { state.channels.filter { it.parentId.isEmpty() && (sourceFilter.isEmpty() || it.sourceId==sourceFilter) && it.kind==kind }.map { it.group }.filter { it.isNotEmpty() }.distinct().sorted() }
    LaunchedEffect(state.sources,groups) {
        if(sourceFilter.isNotEmpty() && state.sources.none { it.id==sourceFilter }) sourceFilter=""
        if(group.isNotEmpty() && group !in groups) group=""
    }
    var visible by remember { mutableStateOf(emptyList<Channel>()) }
    LaunchedEffect(state.channels,tab,query,sourceFilter,group,kind) {
        visible=withContext(Dispatchers.Default) {
            state.channels.filter {
                (it.parentId.isEmpty() || tab==2) && (sourceFilter.isEmpty() || it.sourceId==sourceFilter) &&
                    (group.isEmpty() || it.group==group) && (tab!=0 || it.kind==kind || (kind=="VIDEO" && it.kind=="IMAGE")) &&
                    (tab!=1 || it.favorite) && (tab!=2 || it.lastPlayed>0) &&
                    (query.isEmpty() || it.name.contains(query,true) || it.group.contains(query,true))
            }.let { if(tab==2) it.sortedByDescending { c->c.lastPlayed } else it }
        }
    }
    fun play(channel: Channel) { if(channel.kind=="SERIES") model.openSeries(channel) else PlayerActivity.launch(context,channel) }
    if(onboarding) {
        OnboardingScreen(onFinish={ prefs.edit().putBoolean("onboarded",true).apply(); onboarding=false; add=true })
        return
    }
    Scaffold(containerColor=Canvas,snackbarHost={ SnackbarHost(snackbar) },bottomBar={
        NavigationBar(containerColor=Panel) {
            listOf(R.string.watch to Icons.Rounded.LiveTv,R.string.favorites to Icons.Rounded.FavoriteBorder,R.string.recent to Icons.Rounded.History,R.string.playlists to Icons.AutoMirrored.Rounded.PlaylistPlay).forEachIndexed { i,(label,icon) ->
                NavigationBarItem(selected=tab==i,onClick={tab=i;group="";query=""},icon={Icon(icon,stringResource(label))},label={Text(stringResource(label))})
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=20.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically) {
                BrandMark(42)
                Column(Modifier.weight(1f).padding(start=12.dp)) {
                    Text(stringResource(R.string.app_name),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text(stringResource(R.string.your_entertainment),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CastButton()
                IconButton(onClick={settings=true}) { Icon(Icons.Rounded.Settings,stringResource(R.string.settings)) }
            }
            if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom=12.dp))
            if(!state.ready) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
            else if(tab==3) SourceScreen(state,model,onAdd={add=true})
            else if(state.sources.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Rounded.PlaylistAdd,stringResource(R.string.empty_title),stringResource(R.string.empty_body)) {
                    Button(onClick={add=true},contentPadding=PaddingValues(24.dp,14.dp)) { Icon(Icons.Rounded.Add,null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_source)) }
                }
            } else {
                if(tab==0) LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(listOf("LIVE","MOVIE","SERIES","VIDEO")) { type -> FilterChip(selected=kind==type,onClick={kind=type;group=""},label={Text(stringResource(kindLabel(type)))}) }
                }
                OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(vertical=12.dp),placeholder={Text(stringResource(R.string.search_channels))},leadingIcon={Icon(Icons.Rounded.Search,null)},trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}) { Icon(Icons.Rounded.Close,stringResource(R.string.clear)) }},singleLine=true,shape=RoundedCornerShape(16.dp))
                if(state.sources.size>1) LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected=sourceFilter.isEmpty(),onClick={sourceFilter="";group=""},label={Text(stringResource(R.string.all_playlists))}) }
                    items(state.sources,key={it.id}) { source -> FilterChip(selected=sourceFilter==source.id,onClick={sourceFilter=source.id;group=""},label={Text(source.name)}) }
                }
                if(tab==0 && groups.isNotEmpty()) LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected=group.isEmpty(),onClick={group=""},label={Text(stringResource(R.string.all_groups))}) }
                    items(groups) { name->FilterChip(selected=group==name,onClick={group=name},label={Text(name)}) }
                }
                Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.items_count,visible.size),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
                    TextButton(onClick={add=true}) { Icon(Icons.Rounded.Add,null,Modifier.size(18.dp)); Text(stringResource(R.string.add)) }
                }
                if(visible.isEmpty()) EmptyState(Icons.Rounded.SearchOff,stringResource(R.string.no_results),stringResource(if(tab==1) R.string.favorites_hint else if(tab==2) R.string.recent_hint else R.string.search_hint))
                else LazyVerticalGrid(columns=GridCells.Adaptive(330.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=20.dp)) {
                    items(visible,key={it.id}) { channel -> ChannelCard(channel,{play(channel)},{model.favorite(channel)},{model.guide(channel)}) }
                }
            }
        }
    }
    if(add) AddSourceDialog(state.busy,model,onClose={add=false;model.clearFeedback()})
    if(settings) SettingsDialog(onClose={settings=false})
    state.episodes?.let { EpisodeDialog(it,onPlay={play(it)},onClose={model.closeEpisodes()}) }
    state.schedule?.let { GuideDialog(state.guideChannel,it,onClose={model.closeGuide()}) }
}
fun errorResource(reason: ImportFailure.Reason)=when(reason) {
    ImportFailure.Reason.INVALID_URL->R.string.error_url; ImportFailure.Reason.EMPTY->R.string.error_empty
    ImportFailure.Reason.AUTH->R.string.error_auth; ImportFailure.Reason.NETWORK->R.string.error_network
    ImportFailure.Reason.TOO_LARGE->R.string.error_size; ImportFailure.Reason.SERVER->R.string.error_server
    ImportFailure.Reason.EPG->R.string.error_epg; ImportFailure.Reason.UNSUPPORTED->R.string.error_unsupported
    else->R.string.error_file
}
