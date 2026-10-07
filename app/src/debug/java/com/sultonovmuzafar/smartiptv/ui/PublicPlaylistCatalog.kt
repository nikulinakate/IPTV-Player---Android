package com.sultonovmuzafar.smartiptv.ui

import com.sultonovmuzafar.smartiptv.R

/** Optional manual QA sources. No request is made until the tester chooses one. */
internal object PublicPlaylistCatalog {
    val entries=listOf(
        PublicPlaylistSample("russia",R.string.public_playlist_russia,"https://iptv-org.github.io/iptv/countries/ru.m3u"),
        PublicPlaylistSample("usa",R.string.public_playlist_usa,"https://iptv-org.github.io/iptv/countries/us.m3u"),
        PublicPlaylistSample("relax",R.string.public_playlist_relax,"https://iptv-org.github.io/iptv/categories/relax.m3u")
    )
}
