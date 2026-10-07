package com.sultonovmuzafar.smartiptv.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class SourceClientTest {
    @Test fun xtreamImportsLiveMoviesAndSeriesWithEncodedCredentials() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1,"status":"Active","allowed_output_formats":["ts"]}}"""))
            server.enqueue(MockResponse().setBody("""[{"category_id":"1","category_name":"News"}]"""))
            server.enqueue(MockResponse().setBody("""[{"stream_id":42,"name":"Live news","category_id":"1","epg_channel_id":"news"}]"""))
            server.enqueue(MockResponse().setBody("""[{"category_id":"2","category_name":"Cinema"}]"""))
            server.enqueue(MockResponse().setBody("""[{"stream_id":77,"name":"Movie","category_id":"2","container_extension":"mkv"}]"""))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("""[{"series_id":90,"name":"Series"}]"""))
            server.start()
            val source=Source("test","Test","xtream",server.url("/").toString(),"a/b","p &x")
            val result=SourceClient().xtream(source)
            assertEquals(3,result.size)
            assertEquals("News",result[0].group)
            assertTrue(result[0].url.endsWith("/live/a%2Fb/p%20%26x/42.ts"))
            assertEquals("news",result[0].epgId)
            assertTrue(result[1].url.endsWith("/movie/a%2Fb/p%20%26x/77.mkv"))
            assertEquals("SERIES",result[2].kind)
            assertEquals("",result[2].url)
            val request=server.takeRequest()
            assertEquals("a/b",request.requestUrl!!.queryParameter("username"))
            assertEquals("p &x",request.requestUrl!!.queryParameter("password"))
        }
    }
    @Test fun expiredAccountIsRejectedBeforeCatalogRequests() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1,"status":"Expired"}}"""));server.start()
            try { SourceClient().xtream(Source("a","A","xtream",server.url("/").toString(),"u","p")); fail("Expected auth error") }
            catch(e: ImportFailure) { assertEquals(ImportFailure.Reason.AUTH,e.reason) }
            assertEquals(1,server.requestCount)
        }
    }
    @Test fun episodesAreSortedByNumericSeasonAndUseOwnContainer() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"episodes":{"10":[{"id":"10","title":"Finale","container_extension":"mkv"}],"2":[{"id":"2","title":"Episode 2","container_extension":"mp4"}]}}"""));server.start()
            val source=Source("a","A","xtream",server.url("/").toString(),"u","p")
            val series=Channel("series","a","Show","",kind="SERIES",providerId="22")
            val episodes=SourceClient().episodes(source,series)
            assertEquals(listOf("2","10"),episodes.map { it.group })
            assertTrue(episodes[1].url.endsWith("/series/u/p/10.mkv"))
            assertTrue(episodes.all { it.parentId==series.id })
        }
    }
    @Test fun playlistRedirectUsesFinalUrlForRelativeChannels() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",server.url("/provider/list.m3u")))
            server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:-1,News\nstreams/live.m3u8\n"))
            val client=SourceClient();val download=client.download(server.url("/short").toString())
            val parsed=client.playlist(Source("a","A","url",server.url("/short").toString()),download.bytes,download.finalUrl)
            assertEquals(server.url("/provider/streams/live.m3u8").toString(),parsed.second.single().url)
        }
    }
    @Test fun serverErrorsAndLargePayloadsAreTypedWithoutCredentials() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403));server.start()
            try { SourceClient().fetch(server.url("/?password=private").toString());fail("Expected auth error") }
            catch(e: ImportFailure) { assertEquals(ImportFailure.Reason.AUTH,e.reason);assertFalse(e.toString().contains("private")) }
        }
        try { SourceClient.readLimited(ByteArrayInputStream(ByteArray(100)),50);fail("Expected limit") }
        catch(e: ImportFailure) { assertEquals(ImportFailure.Reason.TOO_LARGE,e.reason) }
    }
}
