package com.sultonovmuzafar.smartiptv.ui

import androidx.lifecycle.ViewModelStore
import com.sultonovmuzafar.smartiptv.IPTVApplication
import com.sultonovmuzafar.smartiptv.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=IPTVApplication::class)
class SourcePreparationTest {
    private fun scenario(readDocument: (String)->ByteArray={ "#EXTM3U\n#EXTINF:-1,News\nhttps://example.test/live.m3u8\n".toByteArray() },block: (LibraryViewModel,LibraryDatabase,TestScope)->Unit) {
        val dispatcher=StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val app=(RuntimeEnvironment.getApplication() as IPTVApplication)
        val db=LibraryDatabase(app,object : TextVault { override fun seal(value: String)=value;override fun open(value: String)=value },"draft-${System.nanoTime()}.db")
        val model=LibraryViewModel(app,db,SourceImporter(SourceClient(),readDocument),dispatcher)
        val store=ViewModelStore().apply { put("model",model) }
        try { block(model,db,TestScope(dispatcher)) } finally { store.clear();db.close();Dispatchers.resetMain() }
    }
    @Test fun previewAndCancellationNeverWriteToLibrary() = scenario { model,db,scope ->
        scope.advanceUntilIdle()
        model.prepareSource(SourceRequest("Draft","stream","https://example.test/video.mp4"));scope.advanceUntilIdle()
        assertNotNull(model.state.value.sourcePreview);assertTrue(db.sources().isEmpty())
        model.discardSource();model.confirmSource();scope.advanceUntilIdle()
        assertNull(model.state.value.sourcePreview);assertTrue(db.sources().isEmpty())
    }
    @Test fun confirmSavesOnceAndSelectsMediaInsteadOfEmptyLiveTab() = scenario { model,db,scope ->
        scope.advanceUntilIdle()
        model.prepareSource(SourceRequest("Video","stream","https://example.test/video.mp4"));scope.advanceUntilIdle()
        model.confirmSource();model.confirmSource();scope.advanceUntilIdle()
        assertEquals(1,db.sources().size)
        assertEquals("added",model.state.value.message);assertEquals("VIDEO",model.state.value.addedKind)
        assertEquals(db.sources().single().id,model.state.value.addedSourceId)
        model.confirmSource();scope.advanceUntilIdle();assertEquals(1,db.sources().size)
    }
    @Test fun cancellationBeforePreparationRunsCannotPublishStalePreview() = scenario { model,db,scope ->
        scope.advanceUntilIdle()
        model.prepareSource(SourceRequest("Cancelled","stream","https://example.test/first.mp4"))
        model.discardSource()
        model.prepareSource(SourceRequest("Current","stream","https://example.test/second.mp4"));scope.advanceUntilIdle()
        assertEquals("Current",model.state.value.sourcePreview!!.name)
        assertFalse(model.state.value.busy);assertFalse(model.state.value.sourcePreparing);assertTrue(db.sources().isEmpty())
    }
    @Test fun cancellationAfterReadStartsKeepsTheNewerDraftAndBusyState() {
        lateinit var active: LibraryViewModel
        scenario(readDocument={
            active.discardSource()
            active.prepareSource(SourceRequest("Newer","stream","https://example.test/new.mp4"))
            "#EXTM3U\n#EXTINF:-1,Old\nhttps://example.test/old.m3u8\n".toByteArray()
        }) { model,db,scope ->
            active=model;scope.advanceUntilIdle()
            model.prepareSource(SourceRequest("Old","file",documentUri="content://provider/list"));scope.advanceUntilIdle()
            assertEquals("Newer",model.state.value.sourcePreview!!.name)
            assertFalse(model.state.value.busy);assertTrue(db.sources().isEmpty())
        }
    }
    @Test fun failedPreparationCanBeCorrectedWithoutSavingTheInvalidSource() = scenario { model,db,scope ->
        scope.advanceUntilIdle()
        model.prepareSource(SourceRequest("Bad","stream","not-a-url"));scope.advanceUntilIdle()
        assertEquals(ImportFailure.Reason.INVALID_URL,model.state.value.error)
        assertNull(model.state.value.sourcePreview);assertFalse(model.state.value.busy)
        model.prepareSource(SourceRequest("Good","stream","https://example.test/good.mp4"));scope.advanceUntilIdle()
        assertNull(model.state.value.error);assertEquals("Good",model.state.value.sourcePreview!!.name);assertTrue(db.sources().isEmpty())
    }
}
