package com.cloud42labo.serendipityspot.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cloud42labo.serendipityspot.ui.components.AppCard
import com.cloud42labo.serendipityspot.ui.theme.Spacing

@Composable
fun SignInOverlay(onSignInClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AppCard(
            modifier = Modifier.padding(32.dp),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Text("ついでにスポット", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    "Googleアカウントでログインすると、スポットがあなたのスプレッドシートに保存されます",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.xl))
                Button(onClick = onSignInClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Googleでログイン")
                }
            }
        }
    }
}
