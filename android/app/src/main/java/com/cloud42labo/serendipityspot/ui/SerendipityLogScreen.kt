package com.cloud42labo.serendipityspot.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
 * Serendipity Log 一覧画面（SPOT-04-S02-T01）。
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
 * 編集・削除・空状態の見た目調整はSPOT-04-S02-T02のスコープでここでは扱わない
 * （空状態は最低限の1行のみ）。
 */
private val LOG_DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerendipityLogScreen(
    visitLog: List<VisitRecord>,
    spots: List<Spot>,
    onBack: () -> Unit,
) {
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
        if (visitLog.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "まだ記録がありません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        // 新しい順（recordedAtの降順）。「発見の履歴」として最近の出来事から読めるように。
        val sorted = visitLog.sortedByDescending { it.recordedAt }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(sorted, key = { it.id }) { record ->
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
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }
    }
}
