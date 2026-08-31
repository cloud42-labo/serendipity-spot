package com.cloud42labo.serendipityspot.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.cloud42labo.serendipityspot.data.Spot
import com.cloud42labo.serendipityspot.data.VisitRecord
import com.cloud42labo.serendipityspot.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serendipity Log 一覧画面（SPOT-04-S02-T01・T02）。
 * 通知の「寄った」アクションから記録した[VisitRecord]を新しい順に表示する。
 * 「通常のタスク完了一覧ではなく発見の履歴として見える」というACに対応するため、
 * チェックリストではなく1件ずつカードのように読める並びにする。
 *
 * Approach Decisionどおり新しいデータストアは持たない。[VisitRecord]自体は
 * 記録時点の[VisitRecord.spotTitle]だけを保持し「保存した理由」（[Spot.memo]）は
 * 持たないため、表示のたびに[spots]（現在のスプレッドシートの内容）から
 * [VisitRecord.spotId]で引き直す。スポットが削除・改名されていれば、記録時点の
 * spotTitleは残るが理由は分からなくなる（[VisitRecord]のクラスコメント参照）。
 *
 * 「修正」について（SPOT-04-S02-T02のAC）: [VisitRecord]はspotId/spotTitle/recordedAtの
 * みで、ユーザーが書き換えられる内容を持たない。誤って記録した1件を正す手段は
 * 「削除して、必要なら通知から改めて記録し直す」の1本だけであり、これは通知側の
 * 「取り消す」アクション（[VisitLogPolicy.removeVisitRecord]）と同じ操作。ここでは
 * その削除操作を一覧画面からも行えるようにする。
 */
private val LOG_DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerendipityLogScreen(
    visitLog: List<VisitRecord>,
    spots: List<Spot>,
    onBack: () -> Unit,
    onDeleteRecord: (String) -> Unit,
) {
    var deletingRecord by remember { mutableStateOf<VisitRecord?>(null) }
    deletingRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { deletingRecord = null },
            title = { Text("記録を削除") },
            text = { Text("「${record.spotTitle}」への記録（${LOG_DATE_FORMAT.format(Date(record.recordedAt))}）を削除します。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRecord(record.id)
                    deletingRecord = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingRecord = null }) { Text("キャンセル") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Serendipity Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "履歴を閉じる")
                    }
                },
            )
        },
    ) { padding ->
        // 履歴0件（初回・全件削除後のどちらも同じ状態）。スポット一覧の空状態
        // （SpotListSheetの「地図をタップ、または〜」）と同じく、次に何をすればよいかを
        // 添える。
        if (visitLog.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "まだ記録がありません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        "スポットに近づいたときの通知で「寄った」を押すと、ここに記録されます",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        // 新しい順（recordedAtの降順）。「発見の履歴」として最近の出来事から読めるように。
        val sorted = visitLog.sortedByDescending { it.recordedAt }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(sorted, key = { it.id }) { record ->
                // スポット本体が削除されていてもrecord自体は残り続けるため、
                // currentSpot==nullでも一覧・削除操作が破綻しないことがACの対象。
                val currentSpot = spots.firstOrNull { it.id == record.spotId }
                val reason = currentSpot?.memo?.takeIf { it.isNotBlank() }
                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = {
                        Text(
                            record.spotTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            buildString {
                                append(LOG_DATE_FORMAT.format(Date(record.recordedAt)))
                                append("　")
                                append(
                                    when {
                                        reason != null -> reason
                                        currentSpot == null -> "（このスポットは削除・変更されています）"
                                        else -> "（保存理由の入力はありません）"
                                    },
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { deletingRecord = record }) {
                            Icon(Icons.Filled.Delete, contentDescription = "「${record.spotTitle}」の記録を削除")
                        }
                    },
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }
    }
}
