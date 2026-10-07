package com.sultonovmuzafar.smartiptv.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartiptv.core.UrlTools
import com.sultonovmuzafar.smartiptv.IPTVApplication
import com.sultonovmuzafar.smartiptv.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class LibraryState(
    val sources: List<Source> = emptyList(), val channels: List<Channel> = emptyList(),
    val busy: Boolean = false, val ready: Boolean = false, val error: ImportFailure.Reason? = null,
    val message: String? = null, val episodes: List<Channel>? = null,
    val schedule: List<Programme>? = null, val guideChannel: Channel? = null,
    val total: Int=0,val groups: List<String> = emptyList(),val catalogLoading: Boolean=false
)
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as IPTVApplication
    private val db = app.database
    private val client = app.sources
    private val mutable = MutableStateFlow(LibraryState())
    val state = mutable.asStateFlow()
    private var catalogFilter=CatalogFilter()
    private var catalogJob: Job?=null
    private var catalogGeneration=0L
    init { reload() }
    fun reload() = viewModelScope.launch {
        try { load() }
        catch(e: CancellationException) { throw e }
        catch (_: Exception) { mutable.value=mutable.value.copy(ready=true,error=ImportFailure.Reason.FILE) }
    }
    private suspend fun load() {
        val sources = withContext(Dispatchers.IO) { db.sources() }
        mutable.value = mutable.value.copy(sources=sources,ready=true)
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
                val page=withContext(Dispatchers.IO) { db.catalog(filter,offset) }
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
            try { withContext(Dispatchers.IO) { block() }; load() }
            catch(e: CancellationException) { throw e }
            catch(e: ImportFailure) { mutable.value=mutable.value.copy(error=e.reason) }
            catch (_: Exception) { mutable.value=mutable.value.copy(error=ImportFailure.Reason.FILE) }
            finally { mutable.value=mutable.value.copy(busy=false,ready=true) }
        }
    }
    fun clearFeedback() { mutable.value=mutable.value.copy(error=null,message=null) }
    fun add(name: String,type: String,url: String,user: String="",password: String="",epg: String="",uri: Uri?=null) = operation {
        val validated = try {
            if(type=="xtream") UrlTools.server(url) else if(type=="file" || type=="media") requireNotNull(uri).toString() else UrlTools.requireHttp(url)
        } catch (_: Exception) { throw ImportFailure(ImportFailure.Reason.INVALID_URL) }
        val source = Source(UUID.randomUUID().toString(),name.trim().ifEmpty { "Playlist" },type,validated,user.trim(),password,
            if(epg.isBlank()) "" else try { UrlTools.requireHttp(epg) } catch (_: Exception) { throw ImportFailure(ImportFailure.Reason.INVALID_URL) })
        when(type) {
            "xtream" -> {
                if(user.isBlank() || password.isBlank()) throw ImportFailure(ImportFailure.Reason.AUTH)
                db.save(source,client.xtream(source))
            }
            "stream","media" -> {
                val mime = uri?.let { app.contentResolver.getType(it) }.orEmpty()
                if(type=="media" && !mime.startsWith("video/") && !mime.startsWith("audio/") && !mime.startsWith("image/")) throw ImportFailure(ImportFailure.Reason.UNSUPPORTED)
                val kind=when {
                    mime.startsWith("image/")->"IMAGE"
                    type=="stream" && Uri.parse(validated).path.orEmpty().endsWith(".m3u8",true)->"LIVE"
                    else->"VIDEO"
                }
                db.save(source,listOf(Channel(SourceClient.id(source.id,validated),source.id,source.name,validated,kind=kind)))
            }
            else -> {
                val downloaded = if(type=="file") null else client.download(validated)
                val bytes = downloaded?.bytes ?: app.contentResolver.openInputStream(requireNotNull(uri))?.use { SourceClient.readLimited(it,50*1024*1024) } ?: throw ImportFailure(ImportFailure.Reason.FILE)
                val (resolved,channels) = client.playlist(source,bytes,downloaded?.finalUrl)
                db.save(resolved,channels)
            }
        }
        mutable.value=mutable.value.copy(message="added")
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
        withContext(Dispatchers.IO) { db.favorite(channel.id,!channel.favorite) }
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
