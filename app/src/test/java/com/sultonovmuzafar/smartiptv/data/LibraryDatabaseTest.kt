package com.sultonovmuzafar.smartiptv.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class LibraryDatabaseTest {
    /** Counts secret reads; does not pretend to benchmark Android Keystore on real hardware. */
    private class CountingVault : TextVault {
        var reads=0
        override fun seal(value: String)="sealed:$value"
        override fun open(value: String): String { reads++;return value.removePrefix("sealed:") }
    }
    private fun database(vault: TextVault=CountingVault())=LibraryDatabase(RuntimeEnvironment.getApplication(),vault,"test-${System.nanoTime()}.db")
    private inline fun LibraryDatabase.withDatabase(block: (LibraryDatabase)->Unit) { try { block(this) } finally { close() } }
    private val source=Source("source","Test","url","https://provider.example/list.m3u")
    private fun channel(id: String,name: String=id,group: String="News")=Channel(id,source.id,name,"https://provider.example/$id.m3u8",group)

    @Test fun catalogLoadsMetadataOnlyAndResolvesOneSecretAtPlayback() {
        val vault=CountingVault()
        database(vault).withDatabase { db ->
            db.save(source,(0 until 1000).map { channel("id-$it","Channel $it") })
            val page=db.catalog(CatalogFilter())
            assertEquals(1000,page.total);assertEquals(200,page.channels.size)
            assertTrue(page.channels.all { it.url.isEmpty() && it.headers.isEmpty() })
            assertEquals(0,vault.reads)
            assertTrue(db.channel(page.channels.first().id)!!.url.startsWith("https://"));assertEquals(1,vault.reads)
        }
    }
    @Test fun refreshPersistsFavoritesResumeAndDeleteRemovesHistory() {
        database().withDatabase { db ->
            val c=channel("a");db.save(source,listOf(c));db.favorite(c.id,true);db.played(c.id,123_456)
            db.save(source,listOf(c.copy(name="Updated")))
            val updated=db.channel(c.id)!!
            assertTrue(updated.favorite);assertEquals(123_456L,updated.position)
            assertEquals(1,db.catalog(CatalogFilter(tab=1)).total)
            assertEquals(1,db.catalog(CatalogFilter(tab=2)).total)
            db.remove(source.id)
            assertEquals(0,db.catalog(CatalogFilter()).total)
            db.readableDatabase.rawQuery("SELECT COUNT(*) FROM history",null).use { it.moveToFirst();assertEquals(0,it.getInt(0)) }
        }
    }
    @Test fun failedRefreshRollsBackThePreviousCatalogAndSource() {
        val vault=object : TextVault {
            var seals=0
            override fun seal(value: String): String { seals++;if(seals==5) throw IllegalStateException("Simulated vault failure");return value }
            override fun open(value: String)=value
        }
        database(vault).withDatabase { db ->
            db.save(source,listOf(channel("old")))
            try { db.save(source.copy(name="Replacement"),listOf(channel("new-1"),channel("new-2")));fail("Expected injected failure") } catch (_: IllegalStateException) {}
            assertNotNull(db.channel("old"));assertNull(db.channel("new-1"));assertEquals("Test",db.sources().single().name)
        }
    }
    @Test fun delayedCheckpointsKeepEventTimeAndCannotRecreateDeletedHistory() {
        database().withDatabase { db ->
            db.save(source,listOf(channel("a")))
            db.played("a",456,123_000)
            assertEquals(123_000L,db.channel("a")!!.lastPlayed)
            assertEquals(456L,db.channel("a")!!.position)
            db.remove(source.id)
            db.played("a",999,124_000)
            db.readableDatabase.rawQuery("SELECT COUNT(*) FROM history",null).use { it.moveToFirst();assertEquals(0,it.getInt(0)) }
        }
    }
    @Test fun unicodeSearchAndSqlWildcardsAreLiteralAndPaginationIsStable() {
        database().withDatabase { db ->
            db.save(source,listOf(channel("a","НОВОСТИ"),channel("b","100% Live"),channel("c","1000 Live"),channel("d","Under_score"),channel("e","UnderXscore")))
            assertEquals("a",db.catalog(CatalogFilter(search="новости")).channels.single().id)
            assertEquals("b",db.catalog(CatalogFilter(search="100%")).channels.single().id)
            assertEquals("d",db.catalog(CatalogFilter(search="Under_")).channels.single().id)
            assertEquals(0,db.catalog(CatalogFilter(search="' OR 1=1 --")).total)
            val pages=(0 until 5).map { db.catalog(CatalogFilter(),it,1).channels.single().id }
            assertEquals(5,pages.distinct().size)
        }
    }
    @Test fun channelNavigationWrapsWithinSourceAndGroup() {
        database().withDatabase { db ->
            val a=channel("a","A");val b=channel("b","B")
            db.save(source,listOf(a,b,channel("other","Other","Sports")))
            assertEquals(b.id,db.neighbor(a,true)!!.id)
            assertEquals(a.id,db.neighbor(b,true)!!.id)
            assertEquals(b.id,db.neighbor(a,false)!!.id)
        }
    }
    @Test fun continueWatchingIsBoundedMetadataAndExcludesLiveImagesAndCompletedItems() {
        val vault=CountingVault()
        database(vault).withDatabase { db ->
            val movies=(0 until 12).map { channel("movie-$it").copy(kind="MOVIE") }
            db.save(source,movies+listOf(channel("live"),channel("photo").copy(kind="IMAGE"),channel("ended").copy(kind="VIDEO")))
            movies.forEachIndexed { index,c -> db.played(c.id,1000,index.toLong()+1) }
            db.played("live",10_000,100);db.played("photo",10_000,101);db.played("ended",0,102)
            val resume=db.continueWatching()
            assertEquals(8,resume.size);assertEquals("movie-11",resume.first().id)
            assertTrue(resume.all { it.kind=="MOVIE" && it.url.isEmpty() });assertEquals(0,vault.reads)
        }
    }
    @Test fun versionOneMigrationKeepsChannelsHistoryAndUnicodeSearch() {
        val context=RuntimeEnvironment.getApplication()
        val name="migration-${System.nanoTime()}.db"
        val vault=CountingVault()
        LibraryDatabase(context,vault,name).withDatabase { db ->
            db.save(source,listOf(channel("old","НОВОСТИ")));db.favorite("old",true)
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DROP INDEX catalog_kind_name");db.execSQL("DROP INDEX catalog_source_group")
            db.execSQL("ALTER TABLE channels RENAME TO channels_v2")
            db.execSQL("CREATE TABLE channels(id TEXT PRIMARY KEY,source TEXT NOT NULL REFERENCES sources(id) ON DELETE CASCADE,name TEXT NOT NULL,group_name TEXT NOT NULL,logo TEXT NOT NULL,epg TEXT NOT NULL,kind TEXT NOT NULL,secret TEXT NOT NULL,provider TEXT NOT NULL,parent TEXT NOT NULL)")
            db.execSQL("INSERT INTO channels SELECT id,source,name,group_name,logo,epg,kind,secret,provider,parent FROM channels_v2")
            db.execSQL("DROP TABLE channels_v2");db.version=1
        }
        LibraryDatabase(context,vault,name).withDatabase { db ->
            assertEquals("old",db.catalog(CatalogFilter(search="новости")).channels.single().id)
            assertTrue(db.channel("old")!!.favorite)
        }
    }
    @Test fun benchmarkTwentyAndFiftyThousandChannels() {
        for(count in listOf(20_000,50_000)) {
            val vault=CountingVault()
            database(vault).withDatabase { db ->
                val playlist=buildString {
                    append("#EXTM3U\n")
                    repeat(count) { append("#EXTINF:-1 group-title=\"Group ${it%20}\",Channel ${it.toString().padStart(5,'0')}\nhttps://provider.example/$it.m3u8\n") }
                }.toByteArray()
                val parseStart=System.nanoTime();val channels=SourceClient().playlist(source,playlist,source.url).second;val parseMs=(System.nanoTime()-parseStart)/1_000_000
                val importStart=System.nanoTime();db.save(source,channels);val importMs=(System.nanoTime()-importStart)/1_000_000
                val pageStart=System.nanoTime();val page=db.catalog(CatalogFilter());val pageMs=(System.nanoTime()-pageStart)/1_000_000
                val searchStart=System.nanoTime();val search=db.catalog(CatalogFilter(search="Channel 19999"));val searchMs=(System.nanoTime()-searchStart)/1_000_000
                assertEquals(count,page.total);assertEquals(200,page.channels.size);assertEquals(1,search.total);assertEquals(0,vault.reads)
                println("CATALOG_BENCH count=$count parse_ms=$parseMs import_ms=$importMs first_page_ms=$pageMs search_ms=$searchMs secret_reads=${vault.reads} (Robolectric SQLite, test vault)")
            }
        }
    }
}
