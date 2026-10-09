package com.datafloat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.datafloat.app.ui.AppRoot
import com.datafloat.app.ui.DataFloatTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DataFloatTheme { AppRoot(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh() // re-check permissions (user may just have come back from system settings) and re-read stats
    }
}
