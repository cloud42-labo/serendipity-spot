package com.cloud42labo.serendipityspot.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cloud42labo.serendipityspot.ui.theme.Spacing

/**
 * 権限を拒否した後の復帰導線。OSは一度拒否された権限を再度ダイアログでは
 * 求め直せないため、アプリ内から設定画面へ橋渡しする（STORY-05: 権限拒否後の復帰）。
 *
 * 呼び出し元（[MainActivity]）ではMapScreenのScaffold外側に重ねて表示するため、
 * Scaffold内蔵のinsets処理を経由しない。[statusBarsPadding]を自前で入れないと、
 * カード本文がステータスバー（時刻・アイコン行）の裏に潜り込む（BUG-05-2）。
 */
@Composable
fun PermissionRecoveryHint(message: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxWidth().statusBarsPadding().padding(Spacing.md)) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.padding(top = 8.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }) {
                        Text("設定を開く")
                    }
                }
            }
        }
    }
}
