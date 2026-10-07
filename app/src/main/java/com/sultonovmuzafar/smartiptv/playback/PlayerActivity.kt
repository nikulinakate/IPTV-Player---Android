package com.sultonovmuzafar.smartiptv.playback

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.google.common.util.concurrent.ListenableFuture
import com.sultonovmuzafar.smartiptv.IPTVApplication
import com.sultonovmuzafar.smartiptv.data.Channel
import com.sultonovmuzafar.smartiptv.ui.IPTVTheme
import kotlinx.coroutines.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : FragmentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>?=null
    private var controller: MediaController?=null
    private var channel: Channel?=null
    private var inPip by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val app=application as IPTVApplication
        lifecycleScope.launch {
            val value=withContext(Dispatchers.IO) { intent.getStringExtra("channel_id")?.let { app.database.channel(it) } ?: PlaybackService.instance?.channel }
            if(value==null) { finish(); return@launch }
            channel=value
            if(value.kind=="IMAGE") {
                PlaybackService.instance?.pause()
                withContext(Dispatchers.IO) { app.database.played(value.id,0) }
                render(value,null); return@launch
            }
            val future=MediaController.Builder(this@PlayerActivity,SessionToken(this@PlayerActivity,ComponentName(this@PlayerActivity,PlaybackService::class.java))).buildAsync()
            controllerFuture=future
            future.addListener({
                if(isFinishing || isDestroyed) return@addListener
                try { controller=future.get(); PlaybackService.instance?.open(value); render(value,controller) } catch (_: Exception) { finish() }
            },ContextCompat.getMainExecutor(this@PlayerActivity))
        }
    }
    private fun render(channel: Channel,controller: MediaController?) {
        if(controller!=null && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            window.decorView.post { setPictureInPictureParams(pipParams()) }
        }
        setContent { IPTVTheme {
            val service=PlaybackService.instance
            if(service!=null && controller!=null) {
                val status by service.status.collectAsStateWithLifecycle()
                PlayerScreen(this,status.channel ?: channel,controller,inPip,status)
            } else PlayerScreen(this,channel,controller,inPip)
        } }
    }
    private fun pipParams(): PictureInPictureParams {
        val rect=android.graphics.Rect()
        window.decorView.getGlobalVisibleRect(rect)
        val builder=PictureInPictureParams.Builder().setAspectRatio(Rational(16,9)).setSourceRectHint(rect)
        if(android.os.Build.VERSION.SDK_INT>=31) builder.setAutoEnterEnabled(controller?.isPlaying==true && getSharedPreferences("settings",MODE_PRIVATE).getBoolean("pip",false))
        return builder.build()
    }
    fun pip() {
        if(packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && controller!=null) enterPictureInPictureMode(pipParams())
    }
    fun updatePip() {
        if(controller!=null && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) setPictureInPictureParams(pipParams())
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if(android.os.Build.VERSION.SDK_INT<31 && getSharedPreferences("settings",MODE_PRIVATE).getBoolean("pip",false) && controller?.isPlaying==true) pip()
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean,newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode,newConfig)
        inPip=isInPictureInPictureMode
    }
    override fun onStop() {
        super.onStop()
        if(!isInPictureInPictureMode && !getSharedPreferences("settings",MODE_PRIVATE).getBoolean("background",false)) controller?.pause()
        PlaybackService.instance?.savePosition()
    }
    override fun onKeyDown(keyCode: Int,event: android.view.KeyEvent): Boolean {
        if(PlaybackService.instance?.channel?.kind=="LIVE") {
            if(keyCode==android.view.KeyEvent.KEYCODE_CHANNEL_UP) { PlaybackService.instance?.step(true);return true }
            if(keyCode==android.view.KeyEvent.KEYCODE_CHANNEL_DOWN) { PlaybackService.instance?.step(false);return true }
        }
        return super.onKeyDown(keyCode,event)
    }
    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }; controller=null
        super.onDestroy()
    }
    companion object {
        fun launch(context: android.content.Context,channel: Channel) { context.startActivity(Intent(context,PlayerActivity::class.java).putExtra("channel_id",channel.id)) }
    }
}
