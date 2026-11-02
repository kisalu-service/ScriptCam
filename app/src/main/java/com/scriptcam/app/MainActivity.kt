package com.scriptcam.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scriptcam.app.ui.AppViewModel
import com.scriptcam.app.ui.ScriptCamNavHost
import com.scriptcam.app.ui.theme.Ink900
import com.scriptcam.app.ui.theme.ScriptCamTheme

/**
 * Activité unique de ScriptCam (Compose).
 *
 * Elle gère aussi le partage de texte entrant : envoyer un script depuis une autre
 * application vers « ScriptCam » ouvre directement l'éditeur avec le texte préchargé.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        captureSharedText(intent)

        val container = applicationContext as ScriptCamApp
        setContent {
            ScriptCamTheme {
                val viewModel: AppViewModel = viewModel(
                    factory = remember {
                        AppViewModel.factory(
                            container.localStore,
                            container.settingsRepository,
                            container.videoExporter
                        )
                    }
                )
                LaunchedEffect(Unit) {
                    SharedTextHolder.text?.let { shared ->
                        viewModel.onSharedTextReceived(shared)
                        SharedTextHolder.text = null
                    }
                }
                Surface(modifier = Modifier.fillMaxSize(), color = Ink900) {
                    ScriptCamNavHost(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureSharedText(intent)
    }

    private fun captureSharedText(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.takeIf { it.isNotBlank() }
                ?.let { SharedTextHolder.text = it }
        }
    }
}

/** Passerelle entre l'Intent de partage et le ViewModel Compose. */
object SharedTextHolder {
    @Volatile
    var text: String? = null
}
