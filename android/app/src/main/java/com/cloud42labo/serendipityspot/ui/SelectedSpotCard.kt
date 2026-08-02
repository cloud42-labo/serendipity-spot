package com.cloud42labo.serendipityspot.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloud42labo.serendipityspot.data.RouteInfo
import com.cloud42labo.serendipityspot.data.Spot
import com.cloud42labo.serendipityspot.ui.components.AppCard
import com.cloud42labo.serendipityspot.ui.components.SpotActionIcons
import com.cloud42labo.serendipityspot.ui.theme.Spacing

/**
 * 地図上でタップ、または通知から選ばれた登録済みスポットの情報と主要アクションを
 * まとめて出す。一覧シートを開かなくても、その場で経路確認・ストリートビュー・
 * 編集・削除に進める（STORY-03: 選択地点の情報と主要アクションを地図上カードで示す）。
 */
@Composable
fun SelectedSpotCard(
    spot: Spot,
    route: RouteInfo?,
    isLoadingRoute: Boolean,
    onRequestRoute: () -> Unit,
    onStreetView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier.fillMaxWidth().padding(Spacing.md),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        spot.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (spot.memo.isNotBlank()) {
                        // メモは複数行・無制限長で保存されうる。上限を付けないと
                        // 長文や大きいフォント設定で下のボタン列が画面外へ押し出される
                        // （Codexレビュー指摘）。
                        Text(
                            spot.memo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "選択を閉じる")
                }
            }
            when {
                isLoadingRoute -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("経路を確認中…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                route != null -> {
                    Text(
                        "徒歩 ${route.durationText}・${route.distanceText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (route == null && !isLoadingRoute) {
                    TextButton(onClick = onRequestRoute) { Text("経路") }
                }
                Spacer(modifier = Modifier.weight(1f))
                SpotActionIcons(
                    spot = spot,
                    onStreetView = onStreetView,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        }
    }
}
