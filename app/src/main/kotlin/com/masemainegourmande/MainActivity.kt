package com.masemainegourmande

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masemainegourmande.ui.MsgNavHost
import com.masemainegourmande.ui.theme.MsgTheme
import com.masemainegourmande.viewmodel.ImportViewModel
import com.masemainegourmande.viewmodel.ShoppingViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // URL shared from browser (android.intent.action.SEND + text/plain)
        val sharedUrl = extractSharedUrl(intent)

        setContent {
            MsgTheme {
                val app        = application as MsgApplication
                val importVm   = viewModel<ImportViewModel>(
                    factory = ImportViewModel.factory(app.repository, app.recipeImporter)
                )
                val shoppingVm = viewModel<ShoppingViewModel>(
                    factory = ShoppingViewModel.factory(app.repository)
                )

                // Trigger import immediately if launched via Share
                sharedUrl?.let { url ->
                    androidx.compose.runtime.LaunchedEffect(url) {
                        importVm.importFromUrl(url)
                    }
                }

                MsgNavHost(importVm = importVm, shoppingVm = shoppingVm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle subsequent shares while app is open
        // The composable will pick it up via ViewModel state
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        // Extract first URL from shared text
        val urlRegex = Regex("""https?://\S+""")
        return urlRegex.find(text)?.value
    }
}
