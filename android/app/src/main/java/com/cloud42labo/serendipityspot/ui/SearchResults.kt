package com.cloud42labo.serendipityspot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cloud42labo.serendipityspot.data.PlaceResult
import com.cloud42labo.serendipityspot.ui.components.AppCard
import com.cloud42labo.serendipityspot.ui.theme.Spacing

/**
 * 検索候補。地図の上に重ねるが、選ぶか閉じるかで即座に消える一時的なものなので
 * 常設の要素とは扱いが違う。
 */
@Composable
fun SearchResults(
    results: List<PlaceResult>,
    onPick: (PlaceResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier.fillMaxWidth().padding(Spacing.md),
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
            items(results) { result ->
                ListItem(
                    headlineContent = { Text(result.name) },
                    supportingContent = {
                        if (result.subtitle.isNotBlank() && result.subtitle != result.name) {
                            Text(result.subtitle)
                        }
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable { onPick(result) },
                )
            }
        }
    }
}
