package com.cloud42labo.serendipityspot.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cloud42labo.serendipityspot.MainActivity
import com.cloud42labo.serendipityspot.R
import com.cloud42labo.serendipityspot.data.Spot

object NotificationHelper {
    // 通知ヘルス診断（NotificationHealthChecker）がこのチャネルの有効性を見る必要があるため
    // internal公開（SPOT-03-S01: Codexレビュー指摘対応）。
    internal const val CHANNEL_ID = "serendipity_spot_proximity"
    private val VIBRATE_PATTERN = longArrayOf(0, 500, 200, 500)

    private const val DEFAULT_BODY = "近くに来ました！"

    /**
     * 通知本文の上限文字数（SPOT-03-S03-T01）。BigTextStyleは技術的にはもっと長い文字列も
     * 描画できるが、OEMの通知シェード実装によっては極端に長い本文でレイアウトが乱れる
     * ことがあるため、常識的な長さで打ち切る。
     */
    private const val MAX_BODY_LENGTH = 200

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            enableVibration(true)
            vibrationPattern = VIBRATE_PATTERN
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * @param isNudge 圏内に留まっているときの二度目の合図。一度目を見逃した場合に効く。
     *   通知IDは一度目と同じにして、シェードに積まずに鳴らし直す。
     */
    fun notifyNearby(context: Context, spot: Spot, isNudge: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        vibrate(context)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_FOCUS_SPOT_ID, spot.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            spot.id.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = bodyFor(spot.memo)
        val title = titleFor(spot.title, isNudge)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // OS標準の汎用アイコンではなく、地図の目印と同じ旗アイコンを使う
            // （STORY-06: 通知だけアプリのブランドと違う見た目になっていた）。
            // 白一色の透過シルエットなので、ステータスバー用途にそのまま使える。
            .setSmallIcon(R.drawable.ic_flag_pin)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(VIBRATE_PATTERN)
            .build()

        NotificationManagerCompat.from(context).notify(spot.id.hashCode(), notification)
    }

    /**
     * 通知本文（保存理由）を組み立てる（SPOT-03-S03-T01）。メモが無ければ既定文へ
     * フォールバックし、長文は[MAX_BODY_LENGTH]で打ち切って省略記号を付ける。
     * Contextに依存しない純粋関数として切り出し、境界ケース
     * （メモなし・短文・長文・改行や記号を含む文）をユニットテストで固定できるようにした
     * （SPOT-03-S03-T02）。
     *
     * 単純な`String.take(n)`はUTF-16のサロゲートペア境界（絵文字など、コードポイントが
     * 2コード単位で表現される文字）をちょうど分断しうる。上位サロゲートだけを残すと
     * 不正な文字列になり、代替文字（�相当）で表示されうるため、その場合は
     * 上位サロゲートごと1文字分手前で打ち切る（Codexレビュー指摘）。
     */
    internal fun bodyFor(memo: String): String {
        val text = memo.ifBlank { DEFAULT_BODY }
        if (text.length <= MAX_BODY_LENGTH) return text
        var cut = MAX_BODY_LENGTH
        if (Character.isHighSurrogate(text[cut - 1])) {
            cut -= 1
        }
        return text.substring(0, cut) + "…"
    }

    /**
     * 通知タイトルを組み立てる。施設名（[spotTitle]）は常にどこかに含まれるため、
     * 本文（[bodyFor]）がどんな内容・長さでも施設名の識別性は失われない（SPOT-03-S03-T02のAC）。
     */
    internal fun titleFor(spotTitle: String, isNudge: Boolean): String =
        if (isNudge) "まだ近くです: $spotTitle" else spotTitle

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(VIBRATE_PATTERN, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VIBRATE_PATTERN, -1)
        }
    }
}
