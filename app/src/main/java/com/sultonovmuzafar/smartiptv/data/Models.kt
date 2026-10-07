package com.sultonovmuzafar.smartiptv.data

data class Source(
    val id: String, val name: String, val type: String, val url: String,
    val username: String = "", val password: String = "", val epgUrl: String = "",
    val updated: Long = 0, val count: Int = 0
)
data class Channel(
    val id: String, val sourceId: String, val name: String, val url: String,
    val group: String = "", val logo: String = "", val epgId: String = "",
    val kind: String = "LIVE", val headers: Map<String, String> = emptyMap(),
    val providerId: String = "", val favorite: Boolean = false,
    val lastPlayed: Long = 0, val position: Long = 0, val parentId: String = ""
)
data class Programme(val title: String, val description: String, val start: Long, val stop: Long)
data class CatalogFilter(val tab: Int=0,val source: String="",val group: String="",val kind: String="LIVE",val search: String="")
data class CatalogPage(val channels: List<Channel>,val total: Int,val groups: List<String>)
class ImportFailure(val reason: Reason) : Exception(reason.name) {
    enum class Reason { INVALID_URL, EMPTY, AUTH, NETWORK, FILE, TOO_LARGE, SERVER, EPG, UNSUPPORTED }
}
