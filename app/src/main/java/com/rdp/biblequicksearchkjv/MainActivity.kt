package com.rdp.biblequicksearchkjv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.rdp.biblequicksearchkjv.ui.BibleQuickSearchApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BibleQuickSearchApp()
        }
    }
}


