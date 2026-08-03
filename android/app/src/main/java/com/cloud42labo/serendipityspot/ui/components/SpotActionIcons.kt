package com.cloud42labo.serendipityspot.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cloud42labo.serendipityspot.data.Spot

/**
 * ストリートビュー・編集・削除の操作アイコン。一覧行と地図上カードの両方がこれを
 * 呼ぶことで、並び・アイコン・contentDescriptionが1箇所に定まり、片方だけ更新して
 * ずれる事故を防ぐ（STORY-04: 一覧・カードの操作を一貫させる）。
 */
@Composable
fun SpotActionIcons(
    spot: Spot,
    onStreetView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        IconButton(onClick = onStreetView) {
            Icon(
                // ストリートビューの目印はペグマン（人型）なので、人のアイコンをそのまま使う。
                imageVector = Icons.Filled.Person,
                contentDescription = "${spot.title} をストリートビューで見る",
            )
        }
        IconButton(onClick = onEdit) {
            Icon(imageVector = Icons.Filled.Edit, contentDescription = "${spot.title} を編集")
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "${spot.title} を削除",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
