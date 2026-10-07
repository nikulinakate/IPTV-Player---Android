package com.sultonovmuzafar.smartiptv.ui

import android.content.res.Configuration
import android.view.View
import com.sultonovmuzafar.smartiptv.IPTVApplication
import com.sultonovmuzafar.smartiptv.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=IPTVApplication::class)
class LocalizedResourcesTest {
    private fun resources(tag: String)=RuntimeEnvironment.getApplication().let { app ->
        val configuration=Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        app.createConfigurationContext(configuration).resources
    }

    @Test fun packagedResourcesResolveEveryDeclaredLocaleWithoutEnglishFallback() {
        val expected=mapOf("en" to "Connect my playlist","ru" to "Подключить мой плейлист",
            "es" to "Conectar mi lista","de" to "Meine Playlist verbinden","fr" to "Connecter ma liste",
            "pt-BR" to "Conectar minha lista","it" to "Collega la mia playlist","ja" to "プレイリストを接続",
            "ko" to "내 재생목록 연결","zh-Hans" to "连接我的播放列表","tr" to "Listemi bağla","ar" to "ربط قائمتي")
        expected.forEach { (tag,title) ->
            val res=resources(tag)
            assertEquals(tag,title,res.getString(R.string.add_first_playlist))
            assertEquals("Smart IPTV",res.getString(R.string.app_name))
            val formatted=res.getString(R.string.reconnecting,2,5)
            assertFalse(tag,formatted.contains("%"))
            val count=res.getQuantityString(R.plurals.item_count,21,21)
            assertFalse(tag,count.contains("%"));assertTrue(tag,count.isNotBlank())
        }
        // Simplified script resources serve both mainland and Singapore Chinese.
        assertEquals("连接我的播放列表",resources("zh-CN").getString(R.string.add_first_playlist))
        assertEquals("连接我的播放列表",resources("zh-SG").getString(R.string.add_first_playlist))
    }

    @Test fun pluralSelectionUsesRussianAndAllSixArabicForms() {
        val ru=resources("ru")
        assertEquals("1 элемент",ru.getQuantityString(R.plurals.item_count,1,1))
        assertEquals("2 элемента",ru.getQuantityString(R.plurals.item_count,2,2))
        assertEquals("5 элементов",ru.getQuantityString(R.plurals.item_count,5,5))
        val ar=resources("ar")
        val endings=mapOf(0 to "لا عناصر",1 to "عنصر واحد",2 to "عنصران",3 to "عناصر",11 to "عنصرًا",100 to "عنصر")
        endings.forEach { (number,ending) -> assertTrue("Arabic quantity $number",ar.getQuantityString(R.plurals.item_count,number,number).endsWith(ending)) }
        assertEquals("فئتان",ar.getQuantityString(R.plurals.category_count,2,2))
        assertEquals(View.LAYOUT_DIRECTION_RTL,ar.configuration.layoutDirection)
    }

    @Test fun resumeDurationUsesAppLocaleInsteadOfSystemLocale() {
        val old=Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("2:05",playbackTime(125_000,Locale.GERMAN))
            assertEquals("1:02:03",playbackTime(3_723_000,Locale.JAPANESE))
            assertEquals("٢:٠٥",playbackTime(125_000,Locale.forLanguageTag("ar")))
        } finally { Locale.setDefault(old) }
    }
}
