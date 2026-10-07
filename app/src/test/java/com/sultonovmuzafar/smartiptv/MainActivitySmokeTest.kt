package com.sultonovmuzafar.smartiptv

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[26],application=IPTVApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivitySmokeTest {
    @Test fun launcherCreatesAndClosesOnMinimumSupportedAndroidVersion() {
        Robolectric.buildActivity(MainActivity::class.java).use { lifecycle ->
            val activity=lifecycle.setup().visible().get()
            assertNotNull(activity.window.decorView)
            assertFalse(activity.isFinishing)
            activity.finish()
            assertTrue(activity.isFinishing)
        }
    }
}
