package com.networktools.opencode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.networktools.opencode.ui.navigation.AppNavHost
import com.networktools.opencode.ui.theme.NetworkToolsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetworkToolsTheme {
                AppNavHost()
            }
        }
    }
}