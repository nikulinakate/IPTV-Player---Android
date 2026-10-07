package com.sultonovmuzafar.smartiptv.playback

import android.content.Context
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.*
import com.google.android.gms.cast.framework.media.*
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity
import com.sultonovmuzafar.smartiptv.data.Channel

class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context) = CastOptions.Builder().setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        .setCastMediaOptions(CastMediaOptions.Builder().setExpandedControllerActivityClassName(CastControllerActivity::class.java.name).build()).build()
    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
class CastControllerActivity : ExpandedControllerActivity()

object CastSupport {
    fun context(context: Context): CastContext? = try { CastContext.getSharedInstance(context) } catch (_: Exception) { null }
    fun canCast(channel: Channel) = channel.url.startsWith("http") && channel.headers.isEmpty() && channel.kind!="IMAGE" && channel.kind!="SERIES"
    fun load(context: Context,channel: Channel,position: Long,onResult: (Boolean)->Unit): Boolean {
        val session=context(context)?.sessionManager?.currentCastSession ?: return false
        if(!canCast(channel) || !session.isConnected) return false
        val metadata=MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC).apply { putString(MediaMetadata.KEY_TITLE,channel.name); putString(MediaMetadata.KEY_SUBTITLE,channel.group) }
        val path=android.net.Uri.parse(channel.url).path.orEmpty().lowercase()
        val mime=when {
            path.endsWith(".m3u8") -> "application/x-mpegURL"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".ts") -> "video/mp2t"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mp3") -> "audio/mpeg"
            else -> "video/mp4"
        }
        val media=MediaInfo.Builder(channel.url).setStreamType(if(channel.kind=="LIVE") MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED).setContentType(mime).setMetadata(metadata).build()
        val remote=session.remoteMediaClient ?: return false
        remote.load(MediaLoadRequestData.Builder().setMediaInfo(media).setAutoplay(true).setCurrentTime(if(channel.kind=="LIVE") 0 else position).build()).setResultCallback { onResult(it.status.isSuccess) }
        return true
    }
}
