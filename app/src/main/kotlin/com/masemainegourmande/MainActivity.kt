// ═══════════════════════════════════════════════════════════════
// FILE: MainActivity.kt
// ═══════════════════════════════════════════════════════════════
package com.masemainegourmande

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.masemainegourmande.ui.MsgNavHost
import com.masemainegourmande.ui.theme.MsgTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedUrl = extractSharedUrl(intent)

        setContent {
            MsgTheme {
                MsgNavHost()
                // If launched via browser Share, pre-fill URL in ImportViewModel
                // (handled inside Navigation composable via shared state if needed)
            }
        }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return Regex("""https?://\S+""").find(text)?.value
    }
}
