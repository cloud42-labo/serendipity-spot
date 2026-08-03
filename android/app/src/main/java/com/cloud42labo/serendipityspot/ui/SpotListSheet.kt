package com.cloud42labo.serendipityspot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloud42labo.serendipityspot.BuildConfig
import com.cloud42labo.serendipityspot.data.Spot
import com.cloud42labo.serendipityspot.ui.components.SpotActionIcons
import com.cloud42labo.serendipityspot.ui.theme.Spacing

/**
 * 通知（ジオフェンス）を張れる件数の上限。GeofenceHelper.MAX_GEOFENCES と同じ値で、
 * Android/Google Play 側の制限。これを超えた分は登録できても通知は届かない。
 */
private const val NOTIFIABLE_LIMIT = 100

@Composable
fun SpotListSheet(
    spots: List<Spot>,
    lastRegistration: String?,
    lastGeofenceEvent: String?,
    lastMapEvent: String?,
    onSpotClick: (Spot) -> Unit,
    onEditClick: (Spot) -> Unit,
    onDeleteClick: (Spot) -> Unit,
    onStreetViewClick: (Spot) -> Unit,
    onTestNotification: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
) {
    // シート全体を1つの LazyColumn にする。見出しと診断を外側の Column に置いて
    // 高さで頭打ちにすると、はみ出した分（テスト通知ボタン）が切り捨てられて
    // 永久に押せなくなる。実際 v0.10.x はその状態だった。
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .padding(bottom = Spacing.lg),
    ) {
        item {
            Text(
                text = "登録スポット（${spots.size} / $NOTIFIABLE_LIMIT）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.xxl),
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "地図をタップ、または右下の「＋」で中心に登録できます",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xxl),
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            val over = spots.size - NOTIFIABLE_LIMIT
            Text(
                text = if (over > 0) {
                    "通知できるのは新しい $NOTIFIABLE_LIMIT 件までです（Androidの上限）。" +
                        "古い $over 件は一覧に残りますが、近づいても通知は届きません。"
                } else {
                    "通知できるのは $NOTIFIABLE_LIMIT 件までです（Androidの上限）。" +
                        "超えた分は古い方から通知の対象外になります。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (over > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = Spacing.xxl),
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider()
        }

        if (spots.isEmpty()) {
            item {
                Text(
                    text = "まだ登録がありません。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.xxl),
                )
            }
        } else {
            items(spots, key = { it.id }) { spot ->
                ListItem(
                    headlineContent = {
                        // カード（SelectedSpotCard）と同じtitleMedium/bodySmallに揃え、
                        // 一覧・カードで同じ情報が同じ見え方になるようにする（STORY-04）。
                        Text(
                            spot.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        if (spot.memo.isNotBlank()) {
                            Text(
                                spot.memo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        // カードと同じ並び・アイコンの共通コンポーネント（STORY-04）。
                        SpotActionIcons(
                            spot = spot,
                            onStreetView = { onStreetViewClick(spot) },
                            onEdit = { onEditClick(spot) },
                            onDelete = { onDeleteClick(spot) },
                        )
                    },
                    modifier = Modifier.clickable { onSpotClick(spot) },
                )
            }
        }

        item {
            HorizontalDivider()
            DiagnosticsBlock(
                lastRegistration = lastRegistration,
                lastGeofenceEvent = lastGeofenceEvent,
                lastMapEvent = lastMapEvent,
                onTestNotification = onTestNotification,
                onRefresh = onRefreshDiagnostics,
            )
        }
    }
}

/**
 * 通知が来ないときの切り分け用。
 * 「ジオフェンスを登録できているか」と「イベントが端末に届いているか」を分けて見る。
 */
@Composable
private fun DiagnosticsBlock(
    lastRegistration: String?,
    lastGeofenceEvent: String?,
    lastMapEvent: String?,
    onTestNotification: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl, vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "診断",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            // 「更新できたのか」を利用者が自力で確かめられるようにする。
            // 配布URLは latest 固定で中身だけ差し替わるため、これが無いと
            // 手元のAPKがどの版か分からない。
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "登録: ${lastRegistration ?: "まだ記録なし"}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "受信: ${lastGeofenceEvent ?: "まだ一度も受け取っていない"}",
            style = MaterialTheme.typography.bodySmall,
        )
        // 「タップしたのに登録画面が出ない」ときに、地図に届いたのか旗に吸われたのかを
        // ここで見分ける。旗タップと出るなら当たり判定の問題。
        Text(
            text = "最後の操作: ${lastMapEvent ?: "まだ地図に触れていない"}",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Row {
            TextButton(onClick = onTestNotification) { Text("テスト通知") }
            Spacer(modifier = Modifier.width(Spacing.sm))
            TextButton(onClick = onRefresh) { Text("更新") }
        }
    }
}
