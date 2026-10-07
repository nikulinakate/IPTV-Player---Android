package com.sultonovmuzafar.smartiptv.playback

import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.android.gms.cast.framework.*
import com.sultonovmuzafar.smartiptv.R
import com.sultonovmuzafar.smartiptv.data.Channel
import com.sultonovmuzafar.smartiptv.ui.CastButton
import com.sultonovmuzafar.smartiptv.ui.Mint
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable fun PlayerScreen(activity: PlayerActivity,channel: Channel,controller: MediaController?,inPip: Boolean=false,status: PlaybackStatus=PlaybackStatus()) {
    var locked by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var castError by remember { mutableStateOf(false) }
    var casting by remember { mutableStateOf(false) }
    var castPending by remember { mutableStateOf(false) }
    var timerMinutes by remember { mutableIntStateOf(0) }
    val manager=remember { CastSupport.context(activity)?.sessionManager }
    fun cast(session: CastSession?) {
        if(session?.isConnected!=true) return
        if(!CastSupport.canCast(channel)) { castError=true; return }
        castPending=true
        if(!CastSupport.load(activity,channel,controller?.currentPosition ?: 0) { success ->
            castPending=false; casting=success; castError=!success
            if(success) controller?.pause()
        }) { castPending=false; castError=true }
    }
    DisposableEffect(controller) {
        val listener=object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering=state==Player.STATE_BUFFERING }
            override fun onIsPlayingChanged(isPlaying: Boolean) { activity.updatePip() }
        }
        controller?.addListener(listener)
        buffering=controller?.playbackState==Player.STATE_BUFFERING
        onDispose { controller?.removeListener(listener) }
    }
    DisposableEffect(manager,channel.id) {
        val listener=object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession,id: String) { cast(session) }
            override fun onSessionResumed(session: CastSession,wasSuspended: Boolean) { cast(session) }
            override fun onSessionEnded(session: CastSession,code: Int) { casting=false; castPending=false }
            override fun onSessionStarting(session: CastSession) = Unit
            override fun onSessionStartFailed(session: CastSession,error: Int) { castError=true; castPending=false }
            override fun onSessionEnding(session: CastSession) = Unit
            override fun onSessionResuming(session: CastSession,id: String) = Unit
            override fun onSessionResumeFailed(session: CastSession,error: Int) { castError=true; castPending=false }
            override fun onSessionSuspended(session: CastSession,reason: Int) { casting=false }
        }
        manager?.addSessionManagerListener(listener,CastSession::class.java)
        cast(manager?.currentCastSession)
        onDispose { manager?.removeSessionManagerListener(listener,CastSession::class.java) }
    }
    DisposableEffect(Unit) {
        WindowCompat.getInsetsController(activity.window,activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        onDispose { WindowCompat.getInsetsController(activity.window,activity.window.decorView).show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(Unit) {
        while(true) { timerMinutes=((PlaybackService.instance?.sleepDeadline ?: 0)-SystemClock.elapsedRealtime()).coerceAtLeast(0).let { ((it+59_999)/60_000).toInt() }; delay(1000) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if(channel.kind=="IMAGE") AsyncImage(channel.url,channel.name,modifier=Modifier.fillMaxSize())
        else AndroidView(factory={PlayerView(it).apply { player=controller; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); setShowSubtitleButton(true) }},update={ it.player=controller;it.useController=!locked && !casting && !inPip },modifier=Modifier.fillMaxSize())
        if(!locked && !inPip) {
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha=.6f)).safeDrawingPadding().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={activity.finish()}) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,stringResource(R.string.back),tint=Color.White) }
                Text(channel.name,Modifier.weight(1f),maxLines=1,color=Color.White)
                if(timerMinutes>0) Text(stringResource(R.string.minutes,timerMinutes),color=Mint,style=MaterialTheme.typography.labelSmall)
                if(CastSupport.canCast(channel)) CastButton()
                IconButton(onClick={tracks=true},enabled=controller!=null) { Icon(Icons.Rounded.Subtitles,stringResource(R.string.tracks),tint=Color.White) }
                IconButton(onClick={tools=true}) { Icon(Icons.Rounded.Tune,stringResource(R.string.player_tools),tint=Color.White) }
            }
            Row(Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(12.dp)) {
                if(channel.kind=="LIVE" && !casting) {
                    IconButton(onClick={PlaybackService.instance?.step(false)}) { Icon(Icons.Rounded.SkipPrevious,stringResource(R.string.previous_channel),tint=Color.White) }
                    IconButton(onClick={PlaybackService.instance?.step(true)}) { Icon(Icons.Rounded.SkipNext,stringResource(R.string.next_channel),tint=Color.White) }
                }
                IconButton(onClick={locked=true}) { Icon(Icons.Rounded.LockOpen,stringResource(R.string.lock_controls),tint=Color.White) }
                IconButton(onClick={activity.pip()},enabled=controller!=null) { Icon(Icons.Rounded.PictureInPictureAlt,stringResource(R.string.pip),tint=Color.White) }
                IconButton(onClick={activity.requestedOrientation=if(activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE}) { Icon(Icons.Rounded.Fullscreen,stringResource(R.string.fullscreen),tint=Color.White) }
            }
        } else if(!inPip) FilledIconButton(onClick={locked=false},modifier=Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(16.dp)) { Icon(Icons.Rounded.Lock,stringResource(R.string.unlock_controls)) }
        if(buffering || castPending) CircularProgressIndicator(Modifier.align(Alignment.Center),color=Mint)
        if(casting) Column(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha=.8f)).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.CastConnected,null,Modifier.size(48.dp),tint=Mint)
            Text(stringResource(R.string.playing_on_tv),color=Color.White)
            Button(onClick={activity.startActivity(Intent(activity,CastControllerActivity::class.java))}) { Text(stringResource(R.string.cast_controls)) }
            TextButton(onClick={manager?.endCurrentSession(true);casting=false;controller?.play()}) { Text(stringResource(R.string.play_on_phone)) }
        }
        if(status.failure!=null && !casting && !inPip) Column(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha=.9f)).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(stringResource(when(status.failure) {
                FailureKind.ACCESS->R.string.playback_access;FailureKind.NOT_FOUND->R.string.playback_not_found
                FailureKind.FORMAT->R.string.playback_format;FailureKind.DRM->R.string.playback_drm
                FailureKind.FILE->R.string.playback_file;FailureKind.NETWORK->R.string.error_network
                else->R.string.playback_error
            }),color=Color.White)
            if(status.recovering) {
                Text(if(status.waitingForNetwork) stringResource(R.string.waiting_for_network) else stringResource(R.string.reconnecting,status.attempt,5),color=Mint)
                TextButton(onClick={PlaybackService.instance?.pause()}) { Text(stringResource(R.string.cancel_reconnect)) }
            } else Button(onClick={PlaybackService.instance?.retry()}) { Text(stringResource(R.string.retry)) }
        }
    }
    if(tools) PlayerTools(activity,channel,onClose={tools=false})
    if(tracks && controller!=null) TrackDialog(controller,onClose={tracks=false})
    if(castError) AlertDialog(onDismissRequest={castError=false},title={Text(stringResource(R.string.cast_unavailable))},text={Text(stringResource(R.string.cast_help))},confirmButton={TextButton(onClick={castError=false}) { Text(stringResource(R.string.done)) }})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PlayerTools(activity: PlayerActivity,channel: Channel,onClose: ()->Unit) {
    val audio=remember { activity.getSystemService(AudioManager::class.java) }
    var brightness by remember { mutableFloatStateOf(activity.window.attributes.screenBrightness.takeIf { it>=0 } ?: .6f) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()/audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)) }
    var externalError by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(R.string.player_tools))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.brightness)); Slider(brightness,{ brightness=it;activity.window.attributes=activity.window.attributes.apply { screenBrightness=it.coerceAtLeast(.02f) } })
            Text(stringResource(R.string.volume)); Slider(volume,{volume=it;audio.setStreamVolume(AudioManager.STREAM_MUSIC,(it*audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(),0)})
            Text(stringResource(R.string.sleep_timer))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(0,15,30,60,90).forEach { minutes -> OutlinedButton(onClick={PlaybackService.instance?.setSleep(minutes);onClose()}) { Text(if(minutes==0) stringResource(R.string.off) else stringResource(R.string.minutes,minutes)) } }
            }
            TextButton(enabled=channel.headers.isEmpty(),onClick={
                val mime=when { channel.kind=="IMAGE"->"image/*";Uri.parse(channel.url).path.orEmpty().endsWith(".m3u8")->"application/x-mpegURL";else->"video/*" }
                try { activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(channel.url),mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),activity.getString(R.string.external_player))) }
                catch (_: Exception) { externalError=true }
            }) { Text(stringResource(R.string.external_player)) }
            if(externalError) Text(stringResource(R.string.no_external_player),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(onClick=onClose) { Text(stringResource(R.string.done)) }})
}

@Composable private fun TrackDialog(controller: MediaController,onClose: ()->Unit) {
    val groups=controller.currentTracks.groups.filter { it.type==C.TRACK_TYPE_AUDIO || it.type==C.TRACK_TYPE_TEXT || it.type==C.TRACK_TYPE_VIDEO }
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(R.string.tracks))},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            TextButton(onClick={controller.trackSelectionParameters=controller.trackSelectionParameters.buildUpon().clearOverrides().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).build();onClose()}) { Text(stringResource(R.string.auto_quality)) }
            TextButton(onClick={controller.trackSelectionParameters=controller.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build();onClose()}) { Text(stringResource(R.string.subtitles_off)) }
            groups.forEach { group ->
                Text(stringResource(when(group.type) { C.TRACK_TYPE_AUDIO->R.string.audio;C.TRACK_TYPE_TEXT->R.string.subtitles;else->R.string.quality }),color=Mint)
                repeat(group.length) { index ->
                    val format=group.getTrackFormat(index)
                    if(group.isTrackSupported(index)) TextButton(onClick={controller.trackSelectionParameters=controller.trackSelectionParameters.buildUpon().setTrackTypeDisabled(group.type,false).setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup,listOf(index))).build();onClose()}) {
                        Text((format.label ?: format.language ?: if(format.height>0) "${format.height}p" else "${index+1}") + if(group.isTrackSelected(index)) " ✓" else "")
                    }
                }
            }
        }
    },confirmButton={TextButton(onClick=onClose) { Text(stringResource(R.string.done)) }})
}
