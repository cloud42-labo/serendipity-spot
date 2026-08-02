package com.cloud42labo.serendipityspot.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.cloud42labo.serendipityspot.data.Spot

@Composable
fun DeleteSpotDialog(
    spot: Spot,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("スポットを削除") },
        text = { Text("「${spot.title}」を削除します。スプレッドシートからも消えます。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("削除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
