package com.sultonovmuzafar.smartiptv

import android.app.Application
import com.sultonovmuzafar.smartiptv.data.LibraryDatabase
import com.sultonovmuzafar.smartiptv.data.SourceClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow

class IPTVApplication : Application() {
    val database by lazy { LibraryDatabase(this) }
    val sources by lazy { SourceClient() }
    private data class PlaybackCheckpoint(val id: String,val position: Long,val at: Long)
    private val historyScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val history by lazy {
        Channel<PlaybackCheckpoint>(64,onBufferOverflow=BufferOverflow.DROP_OLDEST).also { updates ->
            historyScope.launch {
                for(update in updates) runCatching { database.played(update.id,update.position,update.at) }
            }
        }
    }
    /** Ordered checkpoints survive service teardown and never block the UI behind an import. */
    fun recordPlayback(id: String,position: Long) { history.trySend(PlaybackCheckpoint(id,position,System.currentTimeMillis())) }
}
