package com.sultonovmuzafar.smartiptv.playback

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.smartiptv.core.RetryPolicy
import com.sultonovmuzafar.smartiptv.data.Channel

enum class FailureKind { NETWORK,ACCESS,NOT_FOUND,FORMAT,DRM,FILE,UNKNOWN }
data class PlaybackStatus(val channel: Channel?=null,val failure: FailureKind?=null,val recovering: Boolean=false,val attempt: Int=0,val delayMs: Long=0,val waitingForNetwork: Boolean=false)

/** Do not put provider URLs, exception messages or credentials in UI/log state. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlaybackFailure {
    fun classify(error: PlaybackException): Pair<FailureKind,Boolean> {
        var cause: Throwable?=error
        var httpFailure=false
        while(cause!=null) {
            if(cause is HttpDataSource.InvalidResponseCodeException) {
                return when(cause.responseCode) {
                    401,403->FailureKind.ACCESS to false
                    404,410->FailureKind.NOT_FOUND to false
                    else->FailureKind.NETWORK to RetryPolicy.retryHttp(cause.responseCode)
                }
            }
            if(cause is HttpDataSource.InvalidContentTypeException) return FailureKind.FORMAT to false
            if(cause is HttpDataSource.HttpDataSourceException) httpFailure=true
            cause=cause.cause
        }
        if(httpFailure) return FailureKind.NETWORK to true
        return when(error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT->FailureKind.NETWORK to true
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW->FailureKind.NETWORK to true
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND->FailureKind.FILE to false
            in 3000..4999->FailureKind.FORMAT to false
            in 6000..6999->FailureKind.DRM to false
            else->FailureKind.UNKNOWN to false
        }
    }
}
