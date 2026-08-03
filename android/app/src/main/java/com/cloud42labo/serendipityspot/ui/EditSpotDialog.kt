package com.cloud42labo.serendipityspot.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cloud42labo.serendipityspot.data.Spot
import com.cloud42labo.serendipityspot.ui.components.AppTextField
import com.cloud42labo.serendipityspot.ui.theme.Spacing

@Composable
fun EditSpotDialog(
    spot: Spot,
    onDismiss: () -> Unit,
    onSave: (title: String, memo: String) -> Unit,
) {
    var title by remember(spot.id) { mutableStateOf(spot.title) }
    var memo by remember(spot.id) { mutableStateOf(spot.memo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("スポットを編集") },
        text = {
            Column {
                AppTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名前") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                AppTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("ひとことメモ") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, memo) },
                enabled = title.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
