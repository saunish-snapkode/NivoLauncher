package com.snapkode.nivolauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Health Connect's "why does this app want my data?" screen. Plain words; nothing leaves the phone. */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Steps on your home screen", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Nivo Launcher reads today's step count from Health Connect to show it on the Today page. " +
                                "It only reads, never writes, and the number stays on this phone.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = { finish() }) { Text("OK") }
                    }
                }
            }
        }
    }
}
