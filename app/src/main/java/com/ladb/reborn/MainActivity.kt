package com.ladb.reborn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ladb.reborn.core.ConnState
import com.ladb.reborn.ui.BootScreen
import com.ladb.reborn.ui.MainScaffold
import com.ladb.reborn.ui.PairingScreen
import com.ladb.reborn.ui.theme.LadbTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as LadbApp).container

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            LadbTheme(themeMode = settings.themeMode) {
                val conn by container.adbManager.state.collectAsStateWithLifecycle()

                // Keep screen on while the terminal is live (user preference).
                androidx.compose.runtime.LaunchedEffect(settings.keepScreenOn, conn) {
                    if (settings.keepScreenOn && conn is ConnState.Connected) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                LaunchedBootstrap(container)

                when (conn) {
                    is ConnState.Connected -> MainScaffold(container)
                    is ConnState.Connecting, ConnState.Idle -> BootScreen(container)
                    else -> PairingScreen(container)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun LaunchedBootstrap(container: AppContainer) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        container.adbManager.bootstrap()
    }
}
