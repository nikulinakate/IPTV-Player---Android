package com.sultonovmuzafar.smartiptv.data

import com.smartiptv.core.PlaylistParser
import com.smartiptv.core.UrlTools
import com.smartiptv.core.XmlTvParser
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

class SourceClient {
    data class Download(val bytes: ByteArray,val finalUrl: String)
    private val client = OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS).callTimeout(90,TimeUnit.SECONDS).build()
    fun fetch(url: String, limit: Int = 50 * 1024 * 1024): ByteArray = download(url,limit).bytes
    fun download(url: String, limit: Int = 50 * 1024 * 1024): Download {
        val valid = try { UrlTools.requireHttp(url) } catch (_: Exception) { throw ImportFailure(ImportFailure.Reason.INVALID_URL) }
        try {
            client.newCall(Request.Builder().url(valid).header("User-Agent","SmartIPTV/1.0").build()).execute().use { response ->
                if(response.code == 401 || response.code == 403) throw ImportFailure(ImportFailure.Reason.AUTH)
                if(!response.isSuccessful) throw ImportFailure(ImportFailure.Reason.SERVER)
                val body = response.body ?: throw ImportFailure(ImportFailure.Reason.EMPTY)
                if(body.contentLength() > limit) throw ImportFailure(ImportFailure.Reason.TOO_LARGE)
                return Download(body.byteStream().use { readLimited(it,limit) },response.request.url.toString())
            }
        } catch(e: ImportFailure) { throw e } catch (_: Exception) { throw ImportFailure(ImportFailure.Reason.NETWORK) }
    }
    fun playlist(source: Source, bytes: ByteArray, baseUrl: String?): Pair<Source,List<Channel>> {
        val parsed = try { PlaylistParser.parse(InputStreamReader(ByteArrayInputStream(bytes),Charsets.UTF_8),baseUrl) }
            catch (_: Exception) { throw ImportFailure(ImportFailure.Reason.FILE) }
        if(parsed.channels.isEmpty()) throw ImportFailure(ImportFailure.Reason.EMPTY)
        return source.copy(epgUrl=source.epgUrl.ifEmpty { parsed.epgUrl }) to parsed.channels.map { c ->
            Channel(id(source.id,c.url),source.id,c.name,c.url,c.group,c.logo,c.epgId,headers=c.headers)
        }
    }
    fun xtream(source: Source): List<Channel> {
        val account = objectApi(source,"").optJSONObject("user_info") ?: throw ImportFailure(ImportFailure.Reason.AUTH)
        if(account.optInt("auth",0) != 1 || !account.optString("status","Active").equals("Active",true)) throw ImportFailure(ImportFailure.Reason.AUTH)
        val formats=account.optJSONArray("allowed_output_formats")
        val liveExtension=if(formats!=null && (0 until formats.length()).none { formats.optString(it)=="m3u8" }) "ts" else "m3u8"
        val channels = mutableListOf<Channel>()
        for((type,action) in listOf("LIVE" to "live", "MOVIE" to "vod", "SERIES" to "series")) {
            val categories = arrayApi(source,"get_${action}_categories").objects().associate { it.optString("category_id") to it.optString("category_name") }
            val items = arrayApi(source,if(type=="SERIES") "get_series" else "get_${action}_streams")
            items.objects().forEach { j ->
                val provider = j.optString(if(type=="SERIES") "series_id" else "stream_id")
                if(provider.isBlank()) return@forEach
                val ext = if(type=="LIVE") liveExtension else j.optString("container_extension","mp4")
                channels.add(Channel(id(source.id,"$type:$provider"),source.id,j.optString("name","Stream"),
                    if(type=="SERIES") "" else UrlTools.stream(source.url,source.username,source.password,if(type=="LIVE") "live" else "movie",provider,ext),
                    categories[j.optString("category_id")] ?: "",j.optString(if(type=="SERIES") "cover" else "stream_icon"),j.optString("epg_channel_id").takeUnless { it=="null" } ?: "",type,providerId=provider))
            }
        }
        if(channels.isEmpty()) throw ImportFailure(ImportFailure.Reason.EMPTY)
        return channels
    }
    fun episodes(source: Source, series: Channel): List<Channel> {
        val j = objectApi(source,"get_series_info","series_id",series.providerId).optJSONObject("episodes") ?: throw ImportFailure(ImportFailure.Reason.EMPTY)
        return j.keys().asSequence().sortedBy { it.toIntOrNull() ?: 0 }.flatMap { season ->
            (j.optJSONArray(season) ?: JSONArray()).objects().asSequence().map { episode ->
                val provider = episode.getString("id")
                Channel(id(source.id,"EPISODE:$provider"),source.id,episode.optString("title","Episode ${episode.optString("episode_num")}"),
                    UrlTools.stream(source.url,source.username,source.password,"series",provider,episode.optString("container_extension","mp4")),
                    season,series.logo,kind="EPISODE",providerId=provider,parentId=series.id)
            }
        }.toList().ifEmpty { throw ImportFailure(ImportFailure.Reason.EMPTY) }
    }
    fun epg(source: Source): List<XmlTvParser.Programme> {
        val url = source.epgUrl.ifEmpty {
            if(source.type=="xtream") "${UrlTools.server(source.url)}/xmltv.php?username=${UrlTools.encode(source.username)}&password=${UrlTools.encode(source.password)}" else ""
        }
        if(url.isBlank()) throw ImportFailure(ImportFailure.Reason.EPG)
        val bytes = fetch(url)
        try {
            val input = ByteArrayInputStream(bytes)
            val unpacked = if(bytes.size>2 && bytes[0]==0x1f.toByte() && bytes[1]==0x8b.toByte()) readLimited(GZIPInputStream(input),100*1024*1024) else bytes
            return XmlTvParser.parse(ByteArrayInputStream(unpacked),System.currentTimeMillis()).ifEmpty { throw ImportFailure(ImportFailure.Reason.EPG) }
        } catch(e: ImportFailure) { throw e } catch (_: Exception) { throw ImportFailure(ImportFailure.Reason.EPG) }
    }
    private fun api(source: Source, action: String, key: String = "", value: String = ""): String = UrlTools.api(source.url,source.username,source.password,action) + if(key.isEmpty()) "" else "&$key=${UrlTools.encode(value)}"
    private fun arrayApi(s: Source,a: String): JSONArray = try { JSONArray(fetch(api(s,a)).toString(Charsets.UTF_8)) } catch(e: ImportFailure) { throw e } catch (_: Exception) { throw ImportFailure(ImportFailure.Reason.SERVER) }
    private fun objectApi(s: Source,a: String,k: String="",v: String=""): JSONObject = try { JSONObject(fetch(api(s,a,k,v)).toString(Charsets.UTF_8)) } catch(e: ImportFailure) { throw e } catch (_: Exception) { throw ImportFailure(ImportFailure.Reason.SERVER) }
    companion object {
        fun id(source: String,key: String) = MessageDigest.getInstance("SHA-256").digest("$source:$key".toByteArray()).joinToString("") { "%02x".format(it) }
        fun readLimited(input: InputStream,limit: Int): ByteArray {
            val result = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while(true) { val count = input.read(buffer); if(count<0) break; if(result.size()+count>limit) throw ImportFailure(ImportFailure.Reason.TOO_LARGE); result.write(buffer,0,count) }
            return result.toByteArray()
        }
    }
}
private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
