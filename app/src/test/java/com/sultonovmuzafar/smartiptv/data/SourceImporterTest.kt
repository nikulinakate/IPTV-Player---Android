package com.sultonovmuzafar.smartiptv.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SourceImporterTest {
    private fun importer(bytes: String="")=SourceImporter(SourceClient()) { bytes.toByteArray() }
    @Test fun filePreparationSummarizesContentWithoutExposingStreamSecrets() {
        val prepared=importer("#EXTM3U url-tvg=\"https://example.test/epg.xml\"\n#EXTINF:-1 group-title=\"News\",News\nhttps://example.test/live.m3u8?password=private\n")
            .prepare(SourceRequest(" My TV ","file",documentUri="content://provider/list.m3u"))
        assertEquals("My TV",prepared.source.name)
        assertEquals(1,prepared.preview.total);assertEquals(1,prepared.preview.groupCount)
        assertTrue(prepared.preview.hasGuide);assertEquals("LIVE",prepared.preview.preferredKind)
        assertEquals("News",prepared.preview.samples.single().name)
        assertFalse(prepared.preview.toString().contains("private"));assertFalse(prepared.toString().contains("private"))
    }
    @Test fun standaloneStreamPreparationDoesNotPretendToProbePlayback() {
        MockWebServer().use { server ->
            server.start()
            val prepared=importer().prepare(SourceRequest("Live","stream",server.url("/stream.m3u8").toString()))
            assertEquals("LIVE",prepared.preview.preferredKind);assertEquals(1,prepared.preview.total)
            assertEquals(0,server.requestCount)
        }
    }
    @Test fun hlsPlaylistIsOnePlayableStream() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\nsegment.ts\n"));server.start()
            val prepared=importer().prepare(SourceRequest("HLS","url",server.url("/live.m3u8").toString()))
            assertEquals(1,prepared.preview.total);assertEquals(server.url("/live.m3u8").toString(),prepared.channels.single().url)
        }
    }
    @Test fun invalidSourceDoesNotReturnAPreviewAndErrorsAreRedacted() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403));server.start()
            try { importer().prepare(SourceRequest("Test","url",server.url("/?password=private").toString()));fail("Expected access failure") }
            catch(e: ImportFailure) { assertEquals(ImportFailure.Reason.AUTH,e.reason);assertFalse(e.toString().contains("private")) }
        }
        try { importer("not a playlist").prepare(SourceRequest("Bad","file",documentUri="content://provider/bad"));fail("Expected empty playlist") }
        catch(e: ImportFailure) { assertEquals(ImportFailure.Reason.EMPTY,e.reason) }
    }
    @Test fun mediaAndInputValidationChooseCorrectDestination() {
        val image=importer().prepare(SourceRequest("Photo","media",documentUri="content://provider/image",mimeType="image/png"))
        assertEquals("IMAGE",image.channels.single().kind);assertEquals("VIDEO",image.preview.preferredKind)
        try { importer().prepare(SourceRequest("Bad","media",documentUri="content://provider/file",mimeType="application/pdf"));fail("Expected unsupported media") }
        catch(e: ImportFailure) { assertEquals(ImportFailure.Reason.UNSUPPORTED,e.reason) }
        try { importer().prepare(SourceRequest("Bad","xtream","https://provider.test"));fail("Expected missing credentials") }
        catch(e: ImportFailure) { assertEquals(ImportFailure.Reason.AUTH,e.reason) }
        try { importer().prepare(SourceRequest("Bad","stream","javascript:alert(1)"));fail("Expected invalid URL") }
        catch(e: ImportFailure) { assertEquals(ImportFailure.Reason.INVALID_URL,e.reason) }
    }
}
