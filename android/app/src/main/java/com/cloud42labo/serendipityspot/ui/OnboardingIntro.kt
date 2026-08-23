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

/**
 * 起動して最初に一度だけ出す説明。OSの権限ダイアログより先に見せることで、
 * 「なぜ位置情報・通知の許可を聞かれるのか」を分かった状態で答えてもらう
 * （STORY-05: 説明なしのいきなりの権限ダイアログは誤って「許可しない」を
 * 選ばれやすい）。
 */
@Composable
fun OnboardingIntro(onStart: () -> Unit) {
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
                    "行ってみたいけど、そこ自体が目的地ではない場所をピン留めしておくと、" +
                        "別の用事でたまたま近くに来たときに教えます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                Text(
                    "登録したスポットへの接近を検知して通知するため、アプリを閉じているときや" +
                        "使用していないときにも位置情報を利用します。近接判定は端末上で行われ、" +
                        "この判定のための位置情報が開発者へ送信されることはありません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    "次に、位置情報（バックグラウンド利用を含む）と通知の許可を確認します。" +
                        "許可しない場合、近づいたときの通知は利用できません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.xl))
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("はじめる")
                }
            }
        }
    }
}
