package com.cloud42labo.serendipityspot.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cloud42labo.serendipityspot.data.NotificationPreferences
import com.cloud42labo.serendipityspot.ui.theme.Spacing

private val COOLDOWN_PRESETS_MINUTES = listOf(30, 60, 180, 360, 720)

// Calendar.DAY_OF_WEEK は 日=1〜土=7。表示順を月始まりにするため並べ替えて持つ。
private val DAY_ORDER = listOf(2, 3, 4, 5, 6, 7, 1)
private val DAY_LABELS = mapOf(1 to "日", 2 to "月", 3 to "火", 4 to "水", 5 to "木", 6 to "金", 7 to "土")

/**
 * 再通知クールダウンと通知可能な曜日・時間帯を設定するダイアログ（SPOT-03-S02）。
 * 時間帯は時間単位（分単位ではない）に簡略化している。日をまたぐ区間
 * （例: 22時〜翌6時）も指定できる（[NotificationSuppressionPolicy]が解釈する）。
 */
@Composable
fun NotificationScheduleDialog(
    initial: NotificationPreferences,
    onDismiss: () -> Unit,
    onSave: (NotificationPreferences) -> Unit,
) {
    var cooldownMinutes by remember { mutableIntStateOf(initial.cooldownMinutes) }
    var allowedDays by remember { mutableStateOf(initial.allowedDays) }
    var startHour by remember { mutableIntStateOf(initial.startMinute / 60) }
    var endHour by remember { mutableIntStateOf(initial.endMinute / 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通知の設定") },
        text = {
            Column {
                Text(
                    "同じスポットを再通知しない時間",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                LazyRow {
                    items(COOLDOWN_PRESETS_MINUTES) { minutes ->
                        FilterChip(
                            selected = cooldownMinutes == minutes,
                            onClick = { cooldownMinutes = minutes },
                            label = { Text(cooldownLabel(minutes)) },
                            modifier = Modifier.padding(end = Spacing.xs),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                Text("通知してよい曜日", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(Spacing.xs))
                LazyRow {
                    items(DAY_ORDER) { day ->
                        FilterChip(
                            selected = day in allowedDays,
                            onClick = {
                                allowedDays = if (day in allowedDays) {
                                    allowedDays - day
                                } else {
                                    allowedDays + day
                                }
                            },
                            label = { Text(DAY_LABELS.getValue(day)) },
                            modifier = Modifier.padding(end = Spacing.xs),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                Text("通知してよい時間帯（時間単位）", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row {
                    HourStepper(label = "開始", hour = startHour, onChange = { startHour = it })
                    Spacer(modifier = Modifier.width(Spacing.lg))
                    HourStepper(label = "終了", hour = endHour, onChange = { endHour = it })
                }
                Text(
                    text = if (startHour == 0 && endHour == 24) {
                        "終日、制限なし"
                    } else if (startHour > endHour) {
                        "${startHour}時〜翌${endHour}時（日をまたぐ）"
                    } else {
                        "${startHour}時〜${endHour}時"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    NotificationPreferences(
                        cooldownMinutes = cooldownMinutes,
                        allowedDays = allowedDays.ifEmpty { NotificationPreferences.ALL_DAYS },
                        startMinute = startHour * 60,
                        endMinute = endHour * 60,
                    ),
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

/**
 * 0〜24時（24 = 「24:00」＝日の終わり）を1時間刻みで選ぶ。日をまたぐ区間の指定
 * （例: 開始22時・終了6時）を妨げないよう、範囲でのラップはせず単純にクランプする。
 */
@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Row {
            IconButton(onClick = { onChange((hour - 1).coerceIn(0, 24)) }) {
                Text("－")
            }
            Text(
                text = "%02d:00".format(hour),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            IconButton(onClick = { onChange((hour + 1).coerceIn(0, 24)) }) {
                Text("＋")
            }
        }
    }
}

private fun cooldownLabel(minutes: Int): String = when {
    minutes < 60 -> "${minutes}分"
    minutes % 60 == 0 -> "${minutes / 60}時間"
    else -> "${minutes / 60}時間${minutes % 60}分"
}
