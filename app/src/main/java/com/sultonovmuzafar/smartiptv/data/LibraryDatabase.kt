package com.sultonovmuzafar.smartiptv.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.smartiptv.core.XmlTvParser
import org.json.JSONObject

class LibraryDatabase(context: Context) : SQLiteOpenHelper(context, "library.db", null, 1) {
    private val vault = SecretVault()
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true); db.enableWriteAheadLogging() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sources(id TEXT PRIMARY KEY,name TEXT NOT NULL,config TEXT NOT NULL,updated INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE channels(id TEXT PRIMARY KEY,source TEXT NOT NULL REFERENCES sources(id) ON DELETE CASCADE,name TEXT NOT NULL,group_name TEXT NOT NULL,logo TEXT NOT NULL,epg TEXT NOT NULL,kind TEXT NOT NULL,secret TEXT NOT NULL,provider TEXT NOT NULL,parent TEXT NOT NULL)")
        db.execSQL("CREATE INDEX channels_source ON channels(source)")
        db.execSQL("CREATE TABLE history(id TEXT PRIMARY KEY,favorite INTEGER NOT NULL DEFAULT 0,last_played INTEGER NOT NULL DEFAULT 0,position INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE programmes(source TEXT NOT NULL REFERENCES sources(id) ON DELETE CASCADE,channel TEXT NOT NULL,title TEXT NOT NULL,description TEXT NOT NULL,start INTEGER NOT NULL,stop INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX epg_channel_time ON programmes(source,channel,start,stop)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
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
            val insert = db.compileStatement("INSERT OR REPLACE INTO channels VALUES(?,?,?,?,?,?,?,?,?,?)")
            insert.use { statement -> channels.forEach { ch ->
                val secret = vault.seal(JSONObject().put("url",ch.url).put("headers",JSONObject(ch.headers)).toString())
                listOf(ch.id,ch.sourceId,ch.name,ch.group,ch.logo,ch.epgId,ch.kind,secret,ch.providerId,ch.parentId).forEachIndexed { index, value -> statement.bindString(index+1,value) }
                statement.executeInsert(); statement.clearBindings()
            } }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun channels(): List<Channel> = readableDatabase.rawQuery("SELECT c.*,COALESCE(h.favorite,0) AS favorite,COALESCE(h.last_played,0) AS last_played,COALESCE(h.position,0) AS position FROM channels c LEFT JOIN history h ON c.id=h.id ORDER BY c.name COLLATE NOCASE",null).use { c -> buildList { while(c.moveToNext()) add(channel(c)) } }
    fun channel(id: String): Channel? = readableDatabase.rawQuery("SELECT c.*,COALESCE(h.favorite,0) AS favorite,COALESCE(h.last_played,0) AS last_played,COALESCE(h.position,0) AS position FROM channels c LEFT JOIN history h ON c.id=h.id WHERE c.id=?",arrayOf(id)).use { if(it.moveToFirst()) channel(it) else null }
    private fun channel(c: Cursor): Channel {
        val secret = JSONObject(vault.open(c.string("secret")))
        val headers = secret.getJSONObject("headers")
        return Channel(c.string("id"),c.string("source"),c.string("name"),secret.getString("url"),c.string("group_name"),c.string("logo"),c.string("epg"),c.string("kind"),headers.keys().asSequence().associateWith { headers.getString(it) },c.string("provider"),c.long("favorite") == 1L,c.long("last_played"),c.long("position"),c.string("parent"))
    }
    fun favorite(id: String, value: Boolean) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO history(id) VALUES(?)",arrayOf(id))
        writableDatabase.execSQL("UPDATE history SET favorite=? WHERE id=?",arrayOf<Any>(if(value) 1 else 0,id))
    }
    fun played(id: String, position: Long) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO history(id) VALUES(?)",arrayOf(id))
        writableDatabase.execSQL("UPDATE history SET last_played=?,position=? WHERE id=?",arrayOf<Any>(System.currentTimeMillis(),position.coerceAtLeast(0),id))
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
}
