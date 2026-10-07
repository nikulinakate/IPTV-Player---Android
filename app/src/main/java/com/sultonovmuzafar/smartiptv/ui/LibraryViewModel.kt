package com.sultonovmuzafar.smartiptv.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartiptv.core.UrlTools
import android.content.Intent
import com.sultonovmuzafar.smartiptv.IPTVApplication
import com.sultonovmuzafar.smartiptv.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LibraryState(
    val sources: List<Source> = emptyList(), val channels: List<Channel> = emptyList(),
    val busy: Boolean = false, val ready: Boolean = false, val error: ImportFailure.Reason? = null,
    val message: String? = null, val episodes: List<Channel>? = null,
    val schedule: List<Programme>? = null, val guideChannel: Channel? = null,
    val total: Int=0,val groups: List<String> = emptyList(),val catalogLoading: Boolean=false,
    val continueWatching: List<Channel> = emptyList(),val sourcePreview: SourcePreview?=null,
    val sourcePreparing: Boolean=false,val sourceSaving: Boolean=false,
    val addedSourceId: String="",val addedKind: String="LIVE"
)
class LibraryViewModel internal constructor(application: Application,private val db: LibraryDatabase,private val importer: SourceImporter,private val io: CoroutineDispatcher) : AndroidViewModel(application) {
    constructor(application: Application): this(application,(application as IPTVApplication).database,
        SourceImporter(application.sources) { uri ->
            application.contentResolver.openInputStream(Uri.parse(uri))?.use { SourceClient.readLimited(it,50*1024*1024) }
                ?: throw ImportFailure(ImportFailure.Reason.FILE)
        },Dispatchers.IO)
    private val app = application as IPTVApplication
    private val client = app.sources
    private val mutable = MutableStateFlow(LibraryState())
    val state = mutable.asStateFlow()
    private var catalogFilter=CatalogFilter()
    private var catalogJob: Job?=null
    private var catalogGeneration=0L
    private var sourceGeneration=0L
    private var prepareJob: Job?=null
    private var pendingSource: PreparedSource?=null
    init { reload() }
    fun reload() = viewModelScope.launch {
        try { load() }
        catch(e: CancellationException) { throw e }
        catch (_: Exception) { mutable.value=mutable.value.copy(ready=true,error=ImportFailure.Reason.FILE) }
    }
    private suspend fun load() {
        val (sources,resume) = withContext(io) { db.sources() to db.continueWatching() }
        mutable.value = mutable.value.copy(sources=sources,continueWatching=resume,ready=true)
        queryCatalog()
    }
    fun setFilter(filter: CatalogFilter) {
        if(filter==catalogFilter) return
        val typing=filter.search!=catalogFilter.search
        catalogFilter=filter
        queryCatalog(debounce=typing)
    }
    fun loadMore() {
        if(mutable.value.catalogLoading || mutable.value.channels.size>=mutable.value.total) return
        queryCatalog(append=true)
    }
    private fun queryCatalog(append: Boolean=false,debounce: Boolean=false) {
        catalogJob?.cancel()
        val generation=++catalogGeneration
        val filter=catalogFilter
        val offset=if(append) mutable.value.channels.size else 0
        mutable.value=mutable.value.copy(catalogLoading=true)
        catalogJob=viewModelScope.launch {
            try {
                if(debounce) delay(180)
                val page=withContext(io) { db.catalog(filter,offset) }
                if(generation==catalogGeneration) mutable.value=mutable.value.copy(
                    channels=if(append) (mutable.value.channels+page.channels).distinctBy { it.id } else page.channels,
                    total=page.total,groups=page.groups,catalogLoading=false)
            } catch(e: CancellationException) { throw e }
            catch (_: Exception) { if(generation==catalogGeneration) mutable.value=mutable.value.copy(catalogLoading=false,error=ImportFailure.Reason.FILE) }
        }
    }
    private fun operation(block: suspend () -> Unit) {
        if(mutable.value.busy) return
        mutable.value=mutable.value.copy(busy=true,error=null,message=null)
        viewModelScope.launch {
            try { withContext(io) { block() }; load() }
            catch(e: CancellationException) { throw e }
            catch(e: ImportFailure) { mutable.value=mutable.value.copy(error=e.reason) }
            catch (_: Exception) { mutable.value=mutable.value.copy(error=ImportFailure.Reason.FILE) }
            finally { mutable.value=mutable.value.copy(busy=false,ready=true) }
        }
    }
    fun clearFeedback() { mutable.value=mutable.value.copy(error=null,message=null) }
    fun prepareSource(request: SourceRequest) {
        if(mutable.value.busy) return
        val generation=++sourceGeneration
        pendingSource=null
        mutable.value=mutable.value.copy(busy=true,sourcePreparing=true,sourcePreview=null,error=null,message=null)
        prepareJob=viewModelScope.launch {
            try {
                val (prepared,preview)=withContext(io) { importer.prepare(request).let { it to it.preview } }
                if(generation==sourceGeneration) {
                    pendingSource=prepared
                    mutable.value=mutable.value.copy(sourcePreview=preview)
                }
            } catch(e: CancellationException) { throw e }
            catch(e: ImportFailure) { if(generation==sourceGeneration) mutable.value=mutable.value.copy(error=e.reason) }
            catch (_: Exception) { if(generation==sourceGeneration) mutable.value=mutable.value.copy(error=ImportFailure.Reason.FILE) }
            finally { if(generation==sourceGeneration) mutable.value=mutable.value.copy(busy=false,sourcePreparing=false) }
        }
    }
    fun discardSource() {
        if(mutable.value.sourceSaving) return
        sourceGeneration++
        prepareJob?.cancel();prepareJob=null;pendingSource=null
        mutable.value=mutable.value.copy(busy=if(mutable.value.sourcePreparing) false else mutable.value.busy,
            sourcePreparing=false,sourcePreview=null,error=null)
    }
    fun confirmSource() {
        if(mutable.value.busy) return
        val prepared=pendingSource ?: return
        mutable.value=mutable.value.copy(busy=true,sourceSaving=true,error=null)
        viewModelScope.launch {
            try {
                withContext(io) {
                    if(prepared.source.type=="file" || prepared.source.type=="media") {
                        try { app.contentResolver.takePersistableUriPermission(Uri.parse(prepared.source.url),Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        catch (_: SecurityException) { throw ImportFailure(ImportFailure.Reason.FILE) }
                    }
                    db.save(prepared.source,prepared.channels)
                }
                load()
                pendingSource=null
                mutable.value=mutable.value.copy(sourcePreview=null,message="added",addedSourceId=prepared.source.id,addedKind=prepared.preview.preferredKind)
            } catch(e: CancellationException) { throw e }
            catch(e: ImportFailure) { mutable.value=mutable.value.copy(error=e.reason) }
            catch (_: Exception) { mutable.value=mutable.value.copy(error=ImportFailure.Reason.FILE) }
            finally { mutable.value=mutable.value.copy(busy=false,sourceSaving=false) }
        }
    }
    fun refresh(source: Source) = operation {
        when(source.type) {
            "xtream" -> db.save(source,client.xtream(source))
            "url","file" -> {
                val downloaded = if(source.type=="file") null else client.download(source.url)
                val bytes = downloaded?.bytes ?: app.contentResolver.openInputStream(Uri.parse(source.url))?.use { SourceClient.readLimited(it,50*1024*1024) } ?: throw ImportFailure(ImportFailure.Reason.FILE)
                val (resolved,channels) = client.playlist(source,bytes,downloaded?.finalUrl)
                db.save(resolved,channels)
            }
            else -> Unit
        }
        mutable.value=mutable.value.copy(message="updated")
    }
    fun remove(source: Source) = operation { db.remove(source.id) }
    fun favorite(channel: Channel) = viewModelScope.launch {
        withContext(io) { db.favorite(channel.id,!channel.favorite) }
        if(catalogFilter.tab==1 && channel.favorite) {
            mutable.value=mutable.value.copy(channels=mutable.value.channels.filterNot { it.id==channel.id },total=(mutable.value.total-1).coerceAtLeast(0))
        } else mutable.value=mutable.value.copy(channels=mutable.value.channels.map { if(it.id==channel.id) it.copy(favorite=!channel.favorite) else it })
    }
    fun openSeries(series: Channel) = operation {
        val source = db.sources().first { it.id==series.sourceId }
        val cached = db.episodes(series.id)
        val episodes = cached.ifEmpty { client.episodes(source,series).also { db.save(source,it,replace=false) } }
        mutable.value=mutable.value.copy(episodes=episodes)
    }
    fun closeEpisodes() { mutable.value=mutable.value.copy(episodes=null) }
    fun guide(channel: Channel) = operation { mutable.value=mutable.value.copy(schedule=db.schedule(channel),guideChannel=channel) }
    fun closeGuide() { mutable.value=mutable.value.copy(schedule=null,guideChannel=null) }
    fun updateEpg(source: Source, url: String) = operation {
        val updated = source.copy(epgUrl=try { UrlTools.requireHttp(url) } catch (_: Exception) { throw ImportFailure(ImportFailure.Reason.INVALID_URL) })
        val guide = client.epg(updated)
        db.save(updated,emptyList(),replace=false); db.saveEpg(source.id,guide)
        mutable.value=mutable.value.copy(message="epg")
    }
    fun refreshEpg(source: Source) = operation { db.saveEpg(source.id,client.epg(source)); mutable.value=mutable.value.copy(message="epg") }
}
