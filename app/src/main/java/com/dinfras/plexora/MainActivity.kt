package com.dinfras.plexora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import com.dinfras.plexora.data.XtreamCredentials
import com.dinfras.plexora.ui.screens.LiveTvScreen
import com.dinfras.plexora.ui.screens.LoginScreen
import com.dinfras.plexora.ui.theme.PlexoraTheme

class MainActivity : ComponentActivity() {
    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlexoraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var creds by remember { mutableStateOf<XtreamCredentials?>(null) }
                    val current = creds
                    if (current == null) {
                        LoginScreen(onLoggedIn = { creds = it })
                    } else {
                        LiveTvScreen(current)
                    }
                }
            }
        }
    }
}
