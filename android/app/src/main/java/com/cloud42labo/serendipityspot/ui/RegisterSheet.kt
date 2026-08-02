package com.cloud42labo.serendipityspot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cloud42labo.serendipityspot.ui.components.AppTextField
import com.cloud42labo.serendipityspot.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterSheet(
    initialTitle: String,
    onDismiss: () -> Unit,
    onSave: (title: String, memo: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var memo by remember(initialTitle) { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.xxl)) {
            Text("スポット保存", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(Spacing.lg))
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
            Spacer(modifier = Modifier.height(Spacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("キャンセル") }
                Spacer(modifier = Modifier.width(Spacing.sm))
                ElevatedButton(
                    onClick = { onSave(title, memo) },
                    enabled = title.isNotBlank(),
                ) { Text("保存") }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
    }
}
