package com.sultonovmuzafar.smartiptv

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.sultonovmuzafar.smartiptv.ui.*

class MainActivity : FragmentActivity() {
    private val model: LibraryViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { IPTVTheme { LibraryScreen(model) } }
    }
    override fun onResume() { super.onResume(); model.reload() }
}
