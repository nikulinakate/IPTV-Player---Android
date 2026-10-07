package com.sultonovmuzafar.smartiptv.data

import com.smartiptv.core.UrlTools
import java.util.UUID

data class SourceRequest(
    val name: String,val type: String,val url: String="",val username: String="",
    val password: String="",val epgUrl: String="",val documentUri: String="",val mimeType: String=""
) {
    override fun toString()="SourceRequest(type=$type, details=redacted)"
}
data class PreviewItem(val name: String,val group: String,val kind: String)
data class SourcePreview(
    val name: String,val type: String,val total: Int,val counts: Map<String,Int>,
    val groupCount: Int,val samples: List<PreviewItem>,val hasGuide: Boolean,val preferredKind: String
)
class PreparedSource(val source: Source,val channels: List<Channel>) {
    val preview: SourcePreview by lazy {
        val counts=channels.groupingBy { it.kind }.eachCount()
        val preferred=listOf("LIVE","MOVIE","SERIES","VIDEO","IMAGE").firstOrNull { (counts[it] ?: 0)>0 } ?: "LIVE"
        SourcePreview(source.name,source.type,channels.size,counts,channels.map { it.group }.filter { it.isNotBlank() }.distinct().size,
            channels.groupBy { it.kind }.values.flatMap { it.take(3) }.take(8).map { PreviewItem(it.name,it.group,it.kind) },
            source.epgUrl.isNotBlank() || source.type=="xtream",if(preferred=="IMAGE") "VIDEO" else preferred)
    }
    override fun toString()="PreparedSource(channels=${channels.size}, details=redacted)"
}

/** Preparation performs no database writes and never persists a document grant. */
class SourceImporter(private val client: SourceClient,private val readDocument: (String)->ByteArray) {
    fun prepare(request: SourceRequest): PreparedSource {
        val type=request.type
        if(type !in setOf("url","xtream","file","stream","media")) throw ImportFailure(ImportFailure.Reason.UNSUPPORTED)
        val address=try {
            when(type) {
                "xtream"->UrlTools.server(request.url.trim())
                "file","media"->request.documentUri.takeIf { it.startsWith("content://") } ?: throw IllegalArgumentException()
                else->UrlTools.requireHttp(request.url.trim())
            }
        } catch (_: Exception) { throw ImportFailure(if(type=="file" || type=="media") ImportFailure.Reason.FILE else ImportFailure.Reason.INVALID_URL) }
        val epg=if(request.epgUrl.isBlank()) "" else try { UrlTools.requireHttp(request.epgUrl.trim()) } catch (_: Exception) { throw ImportFailure(ImportFailure.Reason.INVALID_URL) }
        val source=Source(UUID.randomUUID().toString(),request.name.trim().ifBlank { "Playlist" },type,address,request.username.trim(),request.password,epg)
        return when(type) {
            "xtream"->{
                if(request.username.isBlank() || request.password.isBlank()) throw ImportFailure(ImportFailure.Reason.AUTH)
                PreparedSource(source,client.xtream(source))
            }
            "stream","media"->{
                val mime=request.mimeType.lowercase()
                if(type=="media" && !mime.startsWith("video/") && !mime.startsWith("audio/") && !mime.startsWith("image/")) throw ImportFailure(ImportFailure.Reason.UNSUPPORTED)
                val kind=when {
                    mime.startsWith("image/")->"IMAGE"
                    type=="stream" && java.net.URI(address).path.orEmpty().endsWith(".m3u8",true)->"LIVE"
                    else->"VIDEO"
                }
                PreparedSource(source,listOf(Channel(SourceClient.id(source.id,address),source.id,source.name,address,kind=kind)))
            }
            else->{
                val downloaded=if(type=="file") null else client.download(address)
                val bytes=downloaded?.bytes ?: readDocument(address)
                val (resolved,channels)=client.playlist(source,bytes,downloaded?.finalUrl)
                PreparedSource(resolved,channels)
            }
        }
    }
}
