package com.sultonovmuzafar.smartiptv.playback

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import android.net.Uri
import com.smartiptv.core.RetryPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackFailureTest {
    @Test fun networkTimeoutCanRecoverButCodecAndDrmCannot() {
        assertEquals(FailureKind.NETWORK to true,PlaybackFailure.classify(PlaybackException("redacted",null,PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)))
        assertEquals(FailureKind.FORMAT to false,PlaybackFailure.classify(PlaybackException("redacted",null,PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)))
        assertEquals(FailureKind.DRM to false,PlaybackFailure.classify(PlaybackException("redacted",null,PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED)))
    }
    @Test fun providerAccessAndMissingUrlsNeverAutomaticallyRetry() {
        for((status,kind) in listOf(401 to FailureKind.ACCESS,403 to FailureKind.ACCESS,404 to FailureKind.NOT_FOUND,410 to FailureKind.NOT_FOUND)) {
            val cause=HttpDataSource.InvalidResponseCodeException(status,"redacted",null,emptyMap(),DataSpec(Uri.parse("https://example.com/?password=secret")),ByteArray(0))
            val result=PlaybackFailure.classify(PlaybackException("redacted",cause,PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
            assertEquals(kind to false,result);assertFalse(result.toString().contains("secret"))
        }
    }
    @Test fun recoveryBudgetIsBoundedAndResetIsExplicit() {
        val retry=RetryPolicy()
        assertEquals(listOf(1000L,2000L,4000L,8000L,16000L),(0 until 5).map { retry.nextDelayMs() })
        assertEquals(-1L,retry.nextDelayMs());assertEquals(-1L,retry.nextDelayMs())
        retry.reset();assertEquals(1000L,retry.nextDelayMs())
        assertTrue(RetryPolicy.retryHttp(429));assertTrue(RetryPolicy.retryHttp(503));assertFalse(RetryPolicy.retryHttp(403));assertFalse(RetryPolicy.retryHttp(404))
    }
}
