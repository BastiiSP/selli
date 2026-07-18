package com.prehmus.selli

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.prehmus.selli.ui.theme.SelliTheme

/**
 * Einstiegspunkt der App. Bewusst schlank gehalten — die eigentliche Kalenderansicht
 * (Owner: Claude, siehe AGENTS.md) und die Sync-/Merge-Logik (Owner: Codex) folgen
 * gemäß dem ersten Handover-Prompt.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SelliTheme {
                SelliPlaceholderScreen()
            }
        }
    }
}

@Composable
fun SelliPlaceholderScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Selli — Grundgerüst steht. Weiter geht's im Handover-Prompt.")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SelliPlaceholderScreenPreview() {
    SelliTheme {
        SelliPlaceholderScreen()
    }
}
