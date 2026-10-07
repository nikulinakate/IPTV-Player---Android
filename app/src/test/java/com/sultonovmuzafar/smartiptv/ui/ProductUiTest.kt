package com.sultonovmuzafar.smartiptv.ui

import android.graphics.Bitmap
import androidx.compose.ui.platform.ViewRootForTest
import android.graphics.Canvas
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.sultonovmuzafar.smartiptv.IPTVApplication
import com.sultonovmuzafar.smartiptv.data.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=IPTVApplication::class,qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ProductUiTest {
    @get:Rule val compose=createComposeRule()
    private fun screenshot(name: String) {
        val view=(compose.onAllNodes(isRoot()).onLast().fetchSemanticsNode().root as ViewRootForTest).view
        compose.runOnIdle {
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file=File("build/reports/ui/$name.png");file.parentFile?.mkdirs()
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        }
    }
    private inline fun withModel(block: (LibraryViewModel,LibraryDatabase)->Unit) {
        val app=(RuntimeEnvironment.getApplication() as IPTVApplication)
        val db=LibraryDatabase(app,object : TextVault { override fun seal(value: String)=value;override fun open(value: String)=value },"ui-${System.nanoTime()}.db")
        val model=LibraryViewModel(app,db,SourceImporter(SourceClient()) { throw ImportFailure(ImportFailure.Reason.FILE) },Dispatchers.Main.immediate)
        val store=ViewModelStore().apply { put("model",model) }
        try { block(model,db) } finally { store.clear();db.close() }
    }
    @Test fun phoneOnboardingMovesBackAndFinishesOnlyOnFinalAction() {
        var finished=0
        compose.setContent { IPTVTheme { OnboardingScreen { finished++ } } }
        compose.onNodeWithText("Step 1 of 3").assertIsDisplayed();screenshot("phone-onboarding-source")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Step 2 of 3").assertIsDisplayed();screenshot("phone-onboarding-library")
        compose.onNodeWithText("Back").performClick();compose.onNodeWithText("Step 1 of 3").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick();compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Connect my playlist").assertIsDisplayed();assertEquals(0,finished);screenshot("phone-onboarding-cast")
        compose.onNodeWithText("Connect my playlist").performClick();assertEquals(1,finished)
    }
    @Test @Config(qualifiers="w1280dp-h720dp-land-television-mdpi")
    fun televisionOnboardingUsesFocusedActionWithDpad() {
        compose.setContent { IPTVTheme { OnboardingScreen {} } }
        compose.onNodeWithText("Continue").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Step 2 of 3").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Connect my playlist").assertIsFocused();screenshot("tv-onboarding-cast")
    }
    @Test fun sourcePreviewRequiresConfirmationBeforeSaving() = withModel { model,db ->
        compose.setContent { IPTVTheme { AddSourceDialog(model) {} } }
        compose.onNodeWithText("Open a single stream").performClick()
        compose.onNodeWithText("URL").performTextInput("https://example.test/live.m3u8")
        compose.onNodeWithText("Preview source").performClick()
        compose.onNodeWithText("Ready to add").assertIsDisplayed();assertTrue(db.sources().isEmpty())
        screenshot("phone-source-preview")
        compose.onNodeWithText("Add to my library").performClick();compose.waitForIdle()
        assertEquals(1,db.sources().size);assertEquals("LIVE",model.state.value.addedKind)
    }
    @Test @Config(qualifiers="w1280dp-h720dp-land-television-mdpi")
    fun televisionLibrarySupportsDpadMenuAndResume() = withModel { model,db ->
        prepareLibrary(model,db)
        compose.setContent { IPTVTheme { LibraryScreen(model) } }
        compose.onNodeWithText("Watch").assertIsFocused();screenshot("tv-library")
        compose.onNodeWithText("Watch").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Favorites").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Your saved channels and titles").assertIsDisplayed()
    }
    private fun prepareLibrary(model: LibraryViewModel,db: LibraryDatabase) {
        val app=(RuntimeEnvironment.getApplication() as IPTVApplication)
        app.getSharedPreferences("settings",0).edit().putBoolean("onboarded",true).commit()
        val source=Source("demo","QA playlist","url","https://example.test/list.m3u")
        db.save(source,(1..20).map { Channel("live-$it",source.id,"Channel $it","https://example.test/$it.m3u8",group=if(it%2==0) "News" else "Entertainment") }+
            Channel("movie",source.id,"A saved movie","https://example.test/movie.mp4",kind="MOVIE"))
        db.played("movie",125_000);model.reload()
    }
    @Test fun phoneLibraryKeepsResumeAndFirstChannelVisible() = withModel { model,db ->
        prepareLibrary(model,db)
        compose.setContent { IPTVTheme { LibraryScreen(model) } }
        compose.onNodeWithText("Continue watching").assertIsDisplayed()
        compose.onNodeWithText("Channel 1").assertIsDisplayed();screenshot("phone-library")
    }
    @Test @Config(qualifiers="ru-w411dp-h891dp-mdpi")
    fun russianOnboardingFinalActionFitsThePhone() {
        compose.setContent { IPTVTheme { OnboardingScreen {} } }
        compose.onNodeWithText("Продолжить").performClick();compose.onNodeWithText("Продолжить").performClick()
        compose.onNodeWithText("Подключить мой плейлист").assertIsDisplayed();screenshot("phone-onboarding-cast-ru")
    }
    private fun assertTextFits(text: String) {
        val results=mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.isNotEmpty())
        val layout=results.first()
        // Paragraph width can reserve the whole available width for RTL/CJK while the
        // Text node wraps its glyphs. Check rendered lines rather than that reservation.
        assertFalse("Clipped text height: $text",layout.didOverflowHeight)
        assertFalse("Missing text lines: $text",layout.multiParagraph.didExceedMaxLines)
        repeat(layout.lineCount) { line ->
            assertFalse("Ellipsized text: $text",layout.isLineEllipsized(line))
            assertTrue("Clipped text width: $text",layout.getLineRight(line)-layout.getLineLeft(line)<=layout.size.width+1f)
        }
    }
    @Test @Config(qualifiers="de-w320dp-h640dp-mdpi")
    fun germanOnboardingFinalActionFitsASmallPhone() {
        compose.setContent { IPTVTheme { OnboardingScreen {} } }
        compose.onNodeWithText("Weiter").performClick();compose.onNodeWithText("Weiter").performClick()
        assertTextFits("Meine Playlist verbinden");screenshot("phone-onboarding-cast-de")
    }
    @Test @Config(qualifiers="ja-w411dp-h891dp-mdpi")
    fun japaneseOnboardingUsesLocalizedArtworkAndActions() {
        compose.setContent { IPTVTheme { OnboardingScreen {} } }
        compose.onNodeWithText("あなたのライブラリ").assertIsDisplayed()
        compose.onNodeWithText("次へ").performClick();compose.onNodeWithText("次へ").performClick()
        assertTextFits("プレイリストを接続");screenshot("phone-onboarding-cast-ja")
    }
    @Test @Config(qualifiers="ar-ldrtl-w411dp-h891dp-mdpi")
    fun arabicOnboardingMirrorsLayoutAndKeepsFinalActionVisible() {
        var direction=LayoutDirection.Ltr
        compose.setContent { val value=LocalLayoutDirection.current;SideEffect { direction=value };IPTVTheme { OnboardingScreen {} } }
        compose.onNodeWithText("متابعة").performClick();compose.onNodeWithText("متابعة").performClick()
        assertEquals(LayoutDirection.Rtl,direction);assertTextFits("ربط قائمتي");screenshot("phone-onboarding-cast-ar")
    }
    @Test @Config(qualifiers="ar-ldrtl-w411dp-h891dp-mdpi")
    fun arabicSourceEntryKeepsUrlLeftToRightAndCanPreview() = withModel { model,db ->
        compose.setContent { IPTVTheme { AddSourceDialog(model) {} } }
        compose.onNodeWithText("فتح بث واحد").performClick()
        val url="https://example.test/live.m3u8?user=demo&token=abc"
        compose.onNodeWithText("الرابط").performTextInput(url)
        val results=mutableListOf<TextLayoutResult>()
        compose.onNode(hasSetTextAction() and hasText(url)).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(TextDirection.Ltr,results.first().layoutInput.style.textDirection)
        screenshot("phone-source-details-ar")
        compose.onNodeWithText("معاينة المصدر").performClick()
        assertTextFits("إضافة إلى مكتبتي");assertTrue(db.sources().isEmpty());screenshot("phone-source-preview-ar")
    }
    @Test @Config(qualifiers="ar-ldrtl-w1280dp-h720dp-land-television-mdpi")
    fun arabicTelevisionNavigationMirrorsAndRemainsFocused() = withModel { model,db ->
        prepareLibrary(model,db)
        compose.setContent { IPTVTheme { LibraryScreen(model) } }
        val node=compose.onNodeWithText("شاهد").assertIsFocused().fetchSemanticsNode()
        assertTrue("Navigation should be on the right",node.boundsInRoot.center.x>640f)
        screenshot("tv-library-ar")
        compose.onNodeWithText("شاهد").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("المفضلة").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("قنواتك وعناوينك المحفوظة").assertIsDisplayed()
    }
}
