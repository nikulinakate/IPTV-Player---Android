package com.sultonovmuzafar.smartiptv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sultonovmuzafar.smartiptv.R
import com.sultonovmuzafar.smartiptv.data.*
import com.sultonovmuzafar.smartiptv.playback.PlayerActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable fun LibraryScreen(model: LibraryViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val prefs=remember { context.getSharedPreferences("settings",0) }
    val tv=isTelevision()
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
    val message=state.message?.let { stringResource(when(it) { "added"->R.string.source_added;"epg"->R.string.epg_updated;else->R.string.source_updated }) }
    val grid=rememberLazyGridState()
    val navFocus=remember { FocusRequester() }
    val navItems=listOf(R.string.watch to Icons.Rounded.LiveTv,R.string.favorites to Icons.Rounded.FavoriteBorder,R.string.recent to Icons.Rounded.History,R.string.playlists to Icons.AutoMirrored.Rounded.PlaylistPlay)
    LaunchedEffect(errorText,message) {
        if(errorText!=null && add) return@LaunchedEffect
        if(state.message=="added") {
            add=false;tab=0;sourceFilter=state.addedSourceId;kind=state.addedKind;group="";query=""
        }
        (errorText ?: message)?.let { snackbar.showSnackbar(it);model.clearFeedback() }
    }
    LaunchedEffect(state.sources,state.groups,state.catalogLoading) {
        if(sourceFilter.isNotEmpty() && state.sources.none { it.id==sourceFilter }) sourceFilter=""
        if(!state.catalogLoading && group.isNotEmpty() && group !in state.groups) group=""
    }
    LaunchedEffect(tab,query,sourceFilter,group,kind) {
        if(tab!=3) model.setFilter(CatalogFilter(tab,sourceFilter,group,kind,query))
        grid.scrollToItem(0)
    }
    LaunchedEffect(grid,state.channels.size,state.catalogLoading,state.total,tab) {
        if(tab!=3) snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged().filter { it>=state.channels.size-12 && it>=0 }
            .collect { model.loadMore() }
    }
    fun play(channel: Channel) { if(channel.kind=="SERIES") model.openSeries(channel) else PlayerActivity.launch(context,channel) }
    fun openAdd() { model.discardSource();add=true }
    fun changeTab(value: Int) { tab=value;group="";query="" }
    fun resetFilters() { query="";sourceFilter="";group="" }
    if(onboarding) {
        OnboardingScreen { prefs.edit().putBoolean("onboarded",true).apply();onboarding=false;openAdd() }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail=tv || maxWidth>=840.dp
        val horizontalFilters=tv && maxWidth>=1150.dp
        Scaffold(containerColor=Canvas,snackbarHost={SnackbarHost(snackbar)},bottomBar={
            if(!rail) NavigationBar(containerColor=Panel) {
                navItems.forEachIndexed { index,(label,icon) ->
                    NavigationBarItem(selected=tab==index,onClick={changeTab(index)},modifier=Modifier.focusRing(),icon={Icon(icon,null)},label={Text(stringResource(label))})
                }
            }
        }) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if(rail) NavigationRail(containerColor=Panel,modifier=Modifier.fillMaxHeight().width(if(tv) 148.dp else 112.dp).safeDrawingPadding()) {
                    Spacer(Modifier.height(28.dp))
                    navItems.forEachIndexed { index,(label,icon) ->
                        NavigationRailItem(selected=tab==index,onClick={changeTab(index)},modifier=Modifier.padding(vertical=8.dp).then(if(index==0) Modifier.initialFocus(navFocus,tv) else Modifier).focusRing(),icon={Icon(icon,null)},label={Text(stringResource(label),maxLines=1)})
                    }
                    Spacer(Modifier.weight(1f))
                    if(tv) Text(stringResource(R.string.remote_hint),Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal=if(tv) 28.dp else 20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top=if(rail) 24.dp else 16.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
                        BrandMark(40)
                        Column(Modifier.weight(1f).padding(start=12.dp)) {
                            Text(stringResource(R.string.app_name),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(stringResource(R.string.your_entertainment),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        CastButton()
                        IconButton(onClick={settings=true},modifier=Modifier.focusRing()) { Icon(Icons.Rounded.Settings,stringResource(R.string.settings)) }
                    }
                    if(state.busy || state.catalogLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom=8.dp))
                    when {
                        !state.ready->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
                        tab==3->SourceScreen(state,model,onAdd={if(!state.busy) openAdd()})
                        state.sources.isEmpty()->EmptyLibrary(onAdd={if(!state.busy) openAdd()},enabled=!state.busy)
                        else->LazyVerticalGrid(state=grid,columns=GridCells.Adaptive(if(tv) 420.dp else 330.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                            item(key="heading",span={GridItemSpan(maxLineSpan)}) {
                                Column(Modifier.padding(top=4.dp,bottom=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Text(stringResource(if(tab==0) R.string.library_title else navItems[tab].first),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                                    if(!tv || tab!=0) Text(stringResource(if(tab==0) R.string.library_subtitle else if(tab==1) R.string.favorite_results else R.string.recent_results),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            item(key="filters",span={GridItemSpan(maxLineSpan)}) {
                                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                    if(tab==0 && horizontalFilters) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                        CatalogKinds(kind,{kind=it;group=""},Modifier.weight(1f))
                                        CatalogSearch(query,{query=it},Modifier.width(420.dp))
                                    } else {
                                        if(tab==0) CatalogKinds(kind,{kind=it;group=""})
                                        CatalogSearch(query,{query=it})
                                    }
                                    if(state.sources.size>1) LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(2.dp)) {
                                        item { FilterChip(selected=sourceFilter.isEmpty(),onClick={sourceFilter="";group=""},modifier=Modifier.focusRing(),label={Text(stringResource(R.string.all_playlists))}) }
                                        items(state.sources,key={it.id}) { source -> FilterChip(selected=sourceFilter==source.id,onClick={sourceFilter=source.id;group=""},modifier=Modifier.focusRing(),label={Text(source.name)}) }
                                    }
                                    if(tab==0 && state.groups.isNotEmpty()) LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(2.dp)) {
                                        item { FilterChip(selected=group.isEmpty(),onClick={group=""},modifier=Modifier.focusRing(),label={Text(stringResource(R.string.all_groups))}) }
                                        items(state.groups) { label -> FilterChip(selected=group==label,onClick={group=label},modifier=Modifier.focusRing(),label={Text(label)}) }
                                    }
                                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                        Text(if(state.catalogLoading) stringResource(R.string.loading_catalog) else itemCount(state.total),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
                                        TextButton(enabled=!state.busy,onClick={openAdd()},modifier=Modifier.focusRing()) { Icon(Icons.Rounded.Add,null,Modifier.size(18.dp));Text(stringResource(R.string.add)) }
                                    }
                                }
                            }
                            if(tab==0 && query.isEmpty() && group.isEmpty()) {
                                val resume=state.continueWatching.filter { sourceFilter.isEmpty() || it.sourceId==sourceFilter }
                                if(resume.isNotEmpty()) item(key="resume",span={GridItemSpan(maxLineSpan)}) { ContinueWatching(resume,::play) }
                            }
                            if(state.channels.isEmpty() && !state.catalogLoading) item(key="empty",span={GridItemSpan(maxLineSpan)}) {
                                val filtered=query.isNotBlank() || sourceFilter.isNotBlank() || group.isNotBlank()
                                EmptyState(if(tab==1 && !filtered) Icons.Rounded.FavoriteBorder else if(tab==2 && !filtered) Icons.Rounded.History else Icons.Rounded.SearchOff,
                                    stringResource(if(filtered || tab==0) R.string.no_matches else if(tab==1) R.string.favorite_empty_title else R.string.recent_empty_title),
                                    stringResource(if(filtered || tab==0) R.string.search_hint else if(tab==1) R.string.favorites_hint else R.string.recent_hint)) {
                                    if(filtered) OutlinedButton(onClick={resetFilters()},modifier=Modifier.focusRing()) { Text(stringResource(R.string.clear_filters)) }
                                }
                            }
                            items(state.channels,key={it.id}) { channel -> ChannelCard(channel,{play(channel)},{model.favorite(channel)},{model.guide(channel)}) }
                            if(state.catalogLoading && state.channels.isEmpty()) item(key="loading",span={GridItemSpan(maxLineSpan)}) {
                                Box(Modifier.fillMaxWidth().height(120.dp),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
                            }
                        }
                    }
                }
            }
        }
    }
    if(add) AddSourceDialog(model,onClose={add=false;model.clearFeedback()})
    if(settings) SettingsDialog(onClose={settings=false})
    state.episodes?.let { EpisodeDialog(it,onPlay={play(it)},onClose={model.closeEpisodes()}) }
    state.schedule?.let { GuideDialog(state.guideChannel,it,onClose={model.closeGuide()}) }
}

@Composable private fun CatalogKinds(kind: String,onKind: (String)->Unit,modifier: Modifier=Modifier) {
    LazyRow(modifier,horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(2.dp)) {
        items(listOf("LIVE","MOVIE","SERIES","VIDEO")) { type -> FilterChip(selected=kind==type,onClick={onKind(type)},modifier=Modifier.focusRing(),label={Text(stringResource(kindLabel(type)))}) }
    }
}
@Composable private fun CatalogSearch(query: String,onQuery: (String)->Unit,modifier: Modifier=Modifier) {
    OutlinedTextField(query,onQuery,modifier.fillMaxWidth().focusRing(),placeholder={Text(stringResource(R.string.search_channels))},leadingIcon={Icon(Icons.Rounded.Search,null)},trailingIcon={if(query.isNotEmpty()) IconButton(onClick={onQuery("")},modifier=Modifier.focusRing()) { Icon(Icons.Rounded.Close,stringResource(R.string.clear)) }},singleLine=true,shape=RoundedCornerShape(16.dp))
}

@Composable private fun EmptyLibrary(onAdd: ()->Unit,enabled: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide=maxWidth>650.dp && maxHeight>350.dp
        if(wide) Row(Modifier.fillMaxSize().padding(vertical=24.dp),horizontalArrangement=Arrangement.spacedBy(28.dp),verticalAlignment=Alignment.CenterVertically) {
            OnboardingArtwork(0,Modifier.weight(1f).height(260.dp))
            EmptyLibraryCopy(onAdd,enabled,Modifier.weight(1f).verticalScroll(rememberScrollState()))
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            OnboardingArtwork(0,Modifier.fillMaxWidth().height(240.dp))
            EmptyLibraryCopy(onAdd,enabled)
        }
    }
}
@Composable private fun EmptyLibraryCopy(onAdd: ()->Unit,enabled: Boolean,modifier: Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.empty_title),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text(stringResource(R.string.empty_connect_hint),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick=onAdd,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).focusRing(),shape=RoundedCornerShape(16.dp)) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.add_first_playlist)) }
        Text(stringResource(R.string.content_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
fun errorResource(reason: ImportFailure.Reason)=when(reason) {
    ImportFailure.Reason.INVALID_URL->R.string.error_url;ImportFailure.Reason.EMPTY->R.string.error_empty
    ImportFailure.Reason.AUTH->R.string.error_auth;ImportFailure.Reason.NETWORK->R.string.error_network
    ImportFailure.Reason.TOO_LARGE->R.string.error_size;ImportFailure.Reason.SERVER->R.string.error_server
    ImportFailure.Reason.EPG->R.string.error_epg;ImportFailure.Reason.UNSUPPORTED->R.string.error_unsupported
    else->R.string.error_file
}
