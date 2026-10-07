package com.sultonovmuzafar.smartiptv.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.net.ConnectivityManager
import android.net.Network
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sultonovmuzafar.smartiptv.IPTVApplication
import com.sultonovmuzafar.smartiptv.data.Channel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.smartiptv.core.RetryPolicy

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
    private var recoveryJob: Job?=null
    private var stableJob: Job?=null
    private val retries=RetryPolicy()
    private val mutableStatus=MutableStateFlow(PlaybackStatus())
    val status=mutableStatus.asStateFlow()
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private var networkRegistered=false
    private var destroyed=false
    private val networkCallback=object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post {
                if(!destroyed && mutableStatus.value.waitingForNetwork && player.playWhenReady) scheduleRecovery()
            }
        }
    }
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
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                stableJob?.cancel()
                val (kind,retryable)=PlaybackFailure.classify(error)
                mutableStatus.value=mutableStatus.value.copy(failure=kind,recovering=false,waitingForNetwork=false)
                if(retryable && player.playWhenReady) scheduleRecovery()
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean,reason: Int) {
                if(!playWhenReady) cancelRecovery()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if(state==Player.STATE_READY) mutableStatus.value=mutableStatus.value.copy(failure=null,recovering=false,waitingForNetwork=false)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                stableJob?.cancel()
                if(isPlaying) stableJob=scope.launch { delay(15_000);retries.reset() }
            }
        })
        try { connectivity.registerDefaultNetworkCallback(networkCallback);networkRegistered=true } catch (_: Exception) {}
        session=MediaSession.Builder(this,player).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                if(controller.packageName!=packageName && !controller.isTrusted) return MediaSession.ConnectionResult.reject()
                return super.onConnect(session,controller)
            }
        }).setSessionActivity(PendingIntent.getActivity(this,0,Intent(this,PlayerActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
        handler.post(checkpoint)
    }
    fun open(value: Channel) {
        if(channel?.id==value.id && player.mediaItemCount>0) {
            if(player.playerError!=null) retry() else player.play()
            return
        }
        cancelRecovery();stableJob?.cancel();retries.reset()
        savePosition(); openJob?.cancel()
        channel=value
        mutableStatus.value=PlaybackStatus(channel=value)
        http.setDefaultRequestProperties(value.headers)
        player.setMediaItem(MediaItem.Builder().setMediaId(value.id).setUri(value.url).setMediaMetadata(MediaMetadata.Builder().setTitle(value.name).setArtist(value.group).build()).build())
        if(value.kind!="LIVE" && value.position>0) player.seekTo(value.position)
        player.prepare(); player.play()
        (application as IPTVApplication).recordPlayback(value.id,value.position)
    }
    private fun cancelRecovery() {
        recoveryJob?.cancel();recoveryJob=null
        mutableStatus.value=mutableStatus.value.copy(recovering=false,waitingForNetwork=false)
    }
    private fun scheduleRecovery() {
        recoveryJob?.cancel()
        if(!player.playWhenReady || channel==null) return
        if(networkRegistered && connectivity.activeNetwork==null) {
            mutableStatus.value=mutableStatus.value.copy(recovering=true,waitingForNetwork=true)
            return
        }
        val delayMs=retries.nextDelayMs()
        if(delayMs<0) { mutableStatus.value=mutableStatus.value.copy(recovering=false,waitingForNetwork=false);return }
        val id=channel!!.id
        mutableStatus.value=mutableStatus.value.copy(recovering=true,attempt=retries.attempts(),delayMs=delayMs,waitingForNetwork=false)
        recoveryJob=scope.launch {
            delay(delayMs)
            if(channel?.id!=id || !player.playWhenReady) return@launch
            if(channel?.kind=="LIVE") player.seekToDefaultPosition()
            player.prepare()
        }
    }
    fun retry() {
        cancelRecovery();retries.reset()
        mutableStatus.value=mutableStatus.value.copy(failure=null,attempt=0)
        if(channel?.kind=="LIVE") player.seekToDefaultPosition()
        player.prepare();player.play()
    }
    fun step(forward: Boolean) {
        val current=channel?.takeIf { it.kind=="LIVE" } ?: return
        openJob?.cancel()
        openJob=scope.launch {
            val next=withContext(Dispatchers.IO) { (application as IPTVApplication).database.neighbor(current,forward) }
            if(next!=null && channel?.id==current.id && next.id!=current.id) open(next)
        }
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
        (application as IPTVApplication).recordPlayback(id,position)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onDestroy() {
        destroyed=true
        savePosition()
        handler.removeCallbacksAndMessages(null); scope.cancel(); session.release(); player.release(); instance=null
        if(networkRegistered) connectivity.unregisterNetworkCallback(networkCallback)
        super.onDestroy()
    }
    companion object { var instance: PlaybackService? = null; private set }
}
