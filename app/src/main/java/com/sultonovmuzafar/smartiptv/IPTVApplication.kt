package com.sultonovmuzafar.smartiptv

import android.app.Application
import com.sultonovmuzafar.smartiptv.data.LibraryDatabase
import com.sultonovmuzafar.smartiptv.data.SourceClient

class IPTVApplication : Application() {
    val database by lazy { LibraryDatabase(this) }
    val sources by lazy { SourceClient() }
}
