package com.sultonovmuzafar.smartiptv.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sultonovmuzafar.smartiptv.IPTVApplication
import com.sultonovmuzafar.smartiptv.data.Channel
import kotlinx.coroutines.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val http = DefaultHttpDataSource.Factory().setUserAgent("SmartIPTV/1.0").setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000).setAllowCrossProtocolRedirects(true)
    var channel: Channel? = null; private set
    var sleepDeadline: Long = 0; private set
    private var openJob: Job? = null
    private val timer = Runnable {
        player.pause()
        CastSupport.context(this)?.sessionManager?.currentCastSession?.remoteMediaClient?.pause()
        sleepDeadline=0
    }
    private val checkpoint = object : Runnable {
        override fun run() { if(player.isPlaying) savePosition(); handler.postDelayed(this,10_000) }
    }
    override fun onCreate() {
        super.onCreate(); instance=this
        player=ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this,http))).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
            setHandleAudioBecomingNoisy(true)
        }
        session=MediaSession.Builder(this,player).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                if(controller.packageName!=packageName && !controller.isTrusted) return MediaSession.ConnectionResult.reject()
                return super.onConnect(session,controller)
            }
        }).setSessionActivity(PendingIntent.getActivity(this,0,Intent(this,PlayerActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
        handler.post(checkpoint)
    }
    fun open(value: Channel) {
        if(channel?.id==value.id && player.mediaItemCount>0) { player.play(); return }
        savePosition(); openJob?.cancel()
        channel=value
        http.setDefaultRequestProperties(value.headers)
        player.setMediaItem(MediaItem.Builder().setMediaId(value.id).setUri(value.url).setMediaMetadata(MediaMetadata.Builder().setTitle(value.name).setArtist(value.group).build()).build())
        if(value.kind!="LIVE" && value.position>0) player.seekTo(value.position)
        player.prepare(); player.play()
        scope.launch(Dispatchers.IO) { (application as IPTVApplication).database.played(value.id,value.position) }
    }
    fun setSleep(minutes: Int) {
        handler.removeCallbacks(timer)
        sleepDeadline=if(minutes>0) SystemClock.elapsedRealtime()+minutes*60_000L else 0
        if(minutes>0) handler.postDelayed(timer,minutes*60_000L)
    }
    fun pause() { player.pause(); savePosition() }
    fun savePosition() {
        val id=channel?.id ?: return
        val position=if(channel?.kind=="LIVE" || player.playbackState==androidx.media3.common.Player.STATE_ENDED) 0 else player.currentPosition.coerceAtLeast(0)
        scope.launch(Dispatchers.IO) { (application as IPTVApplication).database.played(id,position) }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onDestroy() {
        val value=channel; val position=if(value?.kind=="LIVE") 0 else player.currentPosition.coerceAtLeast(0)
        // Complete the last checkpoint before cancelling service work.
        if(value!=null) runBlocking(Dispatchers.IO) { (application as IPTVApplication).database.played(value.id,position) }
        handler.removeCallbacksAndMessages(null); scope.cancel(); session.release(); player.release(); instance=null
        super.onDestroy()
    }
    companion object { var instance: PlaybackService? = null; private set }
}
