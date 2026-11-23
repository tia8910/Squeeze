package com.squeeze.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shown once a Google Play update has downloaded in the background: one tap restarts the app
 * into the new version. Sits above the bottom navigation so it never hides a tab.
 */
@Composable
fun UpdateReadyCard(onRestart: () -> Unit, modifier: Modifier = Modifier) {
    BrandCard(
        modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
            .fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Update ready", fontWeight = FontWeight.Bold)
                Text("Restart Squeeze to finish installing it.")
            }
            Button(onClick = onRestart) { Text("Restart") }
        }
    }
}
