package com.sultonovmuzafar.smartiptv.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.smartiptv.core.XmlTvParser
import org.json.JSONObject
import java.util.Locale

class LibraryDatabase(context: Context,private val vault: TextVault = SecretVault(),name: String="library.db") : SQLiteOpenHelper(context, name, null, 2) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true); db.enableWriteAheadLogging() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sources(id TEXT PRIMARY KEY,name TEXT NOT NULL,config TEXT NOT NULL,updated INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE channels(id TEXT PRIMARY KEY,source TEXT NOT NULL REFERENCES sources(id) ON DELETE CASCADE,name TEXT NOT NULL,group_name TEXT NOT NULL,logo TEXT NOT NULL,epg TEXT NOT NULL,kind TEXT NOT NULL,secret TEXT NOT NULL,provider TEXT NOT NULL,parent TEXT NOT NULL,search_text TEXT NOT NULL)")
        db.execSQL("CREATE INDEX channels_source ON channels(source)")
        db.execSQL("CREATE TABLE history(id TEXT PRIMARY KEY,favorite INTEGER NOT NULL DEFAULT 0,last_played INTEGER NOT NULL DEFAULT 0,position INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE programmes(source TEXT NOT NULL REFERENCES sources(id) ON DELETE CASCADE,channel TEXT NOT NULL,title TEXT NOT NULL,description TEXT NOT NULL,start INTEGER NOT NULL,stop INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX epg_channel_time ON programmes(source,channel,start,stop)")
        createCatalogIndexes(db)
    }
    private fun createCatalogIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS catalog_kind_name ON channels(kind,parent,name COLLATE NOCASE,id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS catalog_source_group ON channels(source,kind,group_name,name COLLATE NOCASE,id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS history_favorite ON history(favorite,id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS history_recent ON history(last_played DESC,id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if(oldVersion<2) {
            db.execSQL("ALTER TABLE channels ADD COLUMN search_text TEXT NOT NULL DEFAULT ''")
            db.compileStatement("UPDATE channels SET search_text=? WHERE id=?").use { statement ->
                db.rawQuery("SELECT id,name,group_name FROM channels",null).use { cursor ->
                    while(cursor.moveToNext()) {
                        statement.bindString(1,"${cursor.getString(1)} ${cursor.getString(2)}".lowercase(Locale.ROOT))
                        statement.bindString(2,cursor.getString(0));statement.executeUpdateDelete();statement.clearBindings()
                    }
                }
            }
            createCatalogIndexes(db)
        }
    }
    fun sources(): List<Source> = readableDatabase.rawQuery("SELECT s.*, (SELECT COUNT(*) FROM channels c WHERE c.source=s.id AND c.parent='') AS count FROM sources s ORDER BY s.name COLLATE NOCASE", null).use { c ->
        buildList { while (c.moveToNext()) {
            val j = JSONObject(vault.open(c.string("config")))
            add(Source(c.string("id"), c.string("name"), j.getString("type"), j.getString("url"), j.optString("username"), j.optString("password"), j.optString("epgUrl"), c.long("updated"), c.getInt(c.getColumnIndexOrThrow("count"))))
        } }
    }
    fun save(source: Source, channels: List<Channel>, replace: Boolean = true) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val config = JSONObject().put("type", source.type).put("url", source.url).put("username", source.username).put("password", source.password).put("epgUrl", source.epgUrl)
            val values = ContentValues().apply { put("id",source.id); put("name",source.name); put("config",vault.seal(config.toString())); put("updated",System.currentTimeMillis()) }
            // UPDATE preserves FK children (SQLite REPLACE would delete EPG and existing channels).
            if (db.update("sources",values,"id=?",arrayOf(source.id)) == 0) db.insertOrThrow("sources",null,values)
            if (replace) db.delete("channels","source=?",arrayOf(source.id))
            val insert = db.compileStatement("INSERT OR REPLACE INTO channels VALUES(?,?,?,?,?,?,?,?,?,?,?)")
            insert.use { statement -> channels.forEach { ch ->
                val secret = vault.seal(JSONObject().put("url",ch.url).put("headers",JSONObject(ch.headers)).toString())
                listOf(ch.id,ch.sourceId,ch.name,ch.group,ch.logo,ch.epgId,ch.kind,secret,ch.providerId,ch.parentId,"${ch.name} ${ch.group}".lowercase(Locale.ROOT)).forEachIndexed { index, value -> statement.bindString(index+1,value) }
                statement.executeInsert(); statement.clearBindings()
            } }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun episodes(parentId: String): List<Channel> = readableDatabase.rawQuery("$resolvedSelect WHERE c.parent=? ORDER BY CAST(c.group_name AS INTEGER),c.name COLLATE NOCASE,c.id",arrayOf(parentId)).use { c -> buildList { while(c.moveToNext()) add(channel(c)) } }
    fun catalog(filter: CatalogFilter,offset: Int=0,limit: Int=PAGE_SIZE): CatalogPage {
        val sql=CatalogSql.forFilter(filter)
        val db=readableDatabase
        val total=db.rawQuery("SELECT COUNT(*) FROM channels c LEFT JOIN history h ON c.id=h.id WHERE ${sql.where}",sql.args).use { it.moveToFirst();it.getInt(0) }
        val order=if(filter.tab==2) "h.last_played DESC,c.id" else "c.name COLLATE NOCASE,c.id"
        val rows=db.rawQuery("$metadataSelect WHERE ${sql.where} ORDER BY $order LIMIT ? OFFSET ?",sql.args+arrayOf(limit.coerceIn(1,PAGE_SIZE).toString(),offset.coerceAtLeast(0).toString())).use { c -> buildList { while(c.moveToNext()) add(metadata(c)) } }
        val groupSql=CatalogSql.forFilter(filter.copy(search="",group=""))
        val groups=db.rawQuery("SELECT DISTINCT c.group_name FROM channels c LEFT JOIN history h ON c.id=h.id WHERE ${groupSql.where} AND c.group_name<>'' ORDER BY c.group_name COLLATE NOCASE",groupSql.args).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
        return CatalogPage(rows,total,groups)
    }
    fun neighbor(current: Channel,forward: Boolean): Channel? {
        val group=if(current.group.isNotEmpty()) " AND c.group_name=?" else ""
        val baseArgs=arrayOf(current.sourceId)+if(current.group.isNotEmpty()) arrayOf(current.group) else emptyArray()
        val base="c.source=? AND c.kind='LIVE' AND c.parent=''$group"
        val compare=if(forward) ">" else "<"
        val direction=if(forward) "ASC" else "DESC"
        val order="c.name COLLATE NOCASE $direction,c.id $direction"
        val query="$resolvedSelect WHERE $base AND (c.name COLLATE NOCASE $compare ? OR (c.name COLLATE NOCASE=? AND c.id $compare ?)) ORDER BY $order LIMIT 1"
        return readableDatabase.rawQuery(query,baseArgs+arrayOf(current.name,current.name,current.id)).use { if(it.moveToFirst()) channel(it) else null }
            ?: readableDatabase.rawQuery("$resolvedSelect WHERE $base ORDER BY $order LIMIT 1",baseArgs).use { if(it.moveToFirst()) channel(it) else null }
    }
    fun channel(id: String): Channel? = readableDatabase.rawQuery("SELECT c.*,COALESCE(h.favorite,0) AS favorite,COALESCE(h.last_played,0) AS last_played,COALESCE(h.position,0) AS position FROM channels c LEFT JOIN history h ON c.id=h.id WHERE c.id=?",arrayOf(id)).use { if(it.moveToFirst()) channel(it) else null }
    private fun channel(c: Cursor): Channel {
        val secret = JSONObject(vault.open(c.string("secret")))
        val headers = secret.getJSONObject("headers")
        return Channel(c.string("id"),c.string("source"),c.string("name"),secret.getString("url"),c.string("group_name"),c.string("logo"),c.string("epg"),c.string("kind"),headers.keys().asSequence().associateWith { headers.getString(it) },c.string("provider"),c.long("favorite") == 1L,c.long("last_played"),c.long("position"),c.string("parent"))
    }
    private fun metadata(c: Cursor) = Channel(c.string("id"),c.string("source"),c.string("name"),"",c.string("group_name"),c.string("logo"),c.string("epg"),c.string("kind"),providerId=c.string("provider"),favorite=c.long("favorite")==1L,lastPlayed=c.long("last_played"),position=c.long("position"),parentId=c.string("parent"))
    fun favorite(id: String, value: Boolean) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO history(id) VALUES(?)",arrayOf(id))
        writableDatabase.execSQL("UPDATE history SET favorite=? WHERE id=?",arrayOf<Any>(if(value) 1 else 0,id))
    }
    fun played(id: String, position: Long,at: Long=System.currentTimeMillis()) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO history(id) SELECT id FROM channels WHERE id=?",arrayOf(id))
        writableDatabase.execSQL("UPDATE history SET last_played=?,position=? WHERE id=? AND EXISTS(SELECT 1 FROM channels WHERE channels.id=history.id)",arrayOf<Any>(at,position.coerceAtLeast(0),id))
    }
    fun remove(id: String) {
        val db = writableDatabase; db.beginTransaction()
        try {
            db.execSQL("DELETE FROM history WHERE id IN (SELECT id FROM channels WHERE source=?)",arrayOf(id))
            db.delete("sources","id=?",arrayOf(id)); db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun saveEpg(sourceId: String, programmes: List<XmlTvParser.Programme>) {
        val db = writableDatabase; db.beginTransaction()
        try {
            db.delete("programmes","source=?",arrayOf(sourceId))
            db.compileStatement("INSERT INTO programmes VALUES(?,?,?,?,?,?)").use { stmt -> programmes.forEach {
                stmt.bindString(1,sourceId); stmt.bindString(2,it.channelId); stmt.bindString(3,it.title); stmt.bindString(4,it.description); stmt.bindLong(5,it.start); stmt.bindLong(6,it.stop); stmt.executeInsert(); stmt.clearBindings()
            } }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun schedule(channel: Channel): List<Programme> = readableDatabase.rawQuery("SELECT * FROM programmes WHERE source=? AND channel=? AND stop>? ORDER BY start LIMIT 100",arrayOf(channel.sourceId,channel.epgId,System.currentTimeMillis().toString())).use { c -> buildList { while(c.moveToNext()) add(Programme(c.string("title"),c.string("description"),c.long("start"),c.long("stop"))) } }
    private fun Cursor.string(name: String) = getString(getColumnIndexOrThrow(name))
    private fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
    companion object {
        const val PAGE_SIZE=200
        private const val metadataSelect="SELECT c.id,c.source,c.name,c.group_name,c.logo,c.epg,c.kind,c.provider,c.parent,COALESCE(h.favorite,0) AS favorite,COALESCE(h.last_played,0) AS last_played,COALESCE(h.position,0) AS position FROM channels c LEFT JOIN history h ON c.id=h.id"
        private const val resolvedSelect="SELECT c.*,COALESCE(h.favorite,0) AS favorite,COALESCE(h.last_played,0) AS last_played,COALESCE(h.position,0) AS position FROM channels c LEFT JOIN history h ON c.id=h.id"
    }
}
