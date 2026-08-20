package com.cloud42labo.serendipityspot.location

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.cloud42labo.serendipityspot.data.HealthItem
import com.cloud42labo.serendipityspot.data.NotificationHealth
import com.cloud42labo.serendipityspot.notification.NotificationHelper

/**
 * 通知ヘルス診断（SPOT-03-S01）。正確な位置情報・バックグラウンド位置情報・通知・
 * 位置情報サービス・バッテリー最適化の5項目を1回で読み、項目ごとに正しい誘導先
 * 設定画面を返す。
 *
 * MainActivityの`hasForegroundLocationPermission`等と同様に権限チェックはここでも
 * 単体で行っている（Contextだけで完結する純粋な読み取りのため、Activityの
 * private関数を再利用せずContextベースで独立させた）。
 */
object NotificationHealthChecker {

    fun check(context: Context): NotificationHealth {
        // ジオフェンス登録（GeofencingClient.addGeofences）は正確な位置情報を要求する。
        // Android 12+ ではおおよその位置情報＋バックグラウンドの組み合わせでも
        // ACCESS_FINE_LOCATIONが拒否されていることがあり、その場合はバックグラウンド
        // 位置情報が「付与済み」に見えてもジオフェンス登録自体が失敗する
        // （Codexレビュー指摘対応）。
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            // Q未満はバックグラウンド位置情報が別権限として存在しない（付与済み扱い）。
            true
        }

        val isNotificationDeliveryHealthy = isNotificationDeliveryHealthy(context)

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationServicesEnabled = LocationManagerCompat.isLocationEnabled(locationManager)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isBatteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)

        return NotificationHealth(
            hasFineLocation = hasFineLocation,
            hasBackgroundLocation = hasBackgroundLocation,
            isNotificationDeliveryHealthy = isNotificationDeliveryHealthy,
            isLocationServicesEnabled = isLocationServicesEnabled,
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
        )
    }

    /**
     * 通知が実際に画面へ出せる状態かをまとめて判定する（Codexレビュー指摘対応）。
     *
     * `POST_NOTIFICATIONS`権限（TIRAMISU+）だけでは不十分で、次の2ケースも
     * 「権限は付与済みだが実際には通知が出ない」状態になりうる。
     * - OS設定でアプリの通知そのものがOFFにされている
     *   （Android 8〜12は実行時権限が無いため、この設定だけでOFFにできる）
     * - アプリの唯一の通知チャネル（`NotificationHelper.CHANNEL_ID`）が
     *   個別に無効化されている（Android 8+、`POST_NOTIFICATIONS`は許可されたまま）
     */
    private fun isNotificationDeliveryHealthy(context: Context): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            // TIRAMISU未満は通知権限の概念自体が無い（付与済み扱い）。
            true
        }
        if (!hasPermission) return false

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = manager?.getNotificationChannel(NotificationHelper.CHANNEL_ID)
            // チャネルが未作成（初回起動でensureChannel未実施等）の場合はまだ判定材料が
            // 無いため健全扱いにする。作成済みでIMPORTANCE_NONEなら個別に無効化されている。
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }

        return true
    }

    /**
     * 診断項目ごとの誘導先（SPOT-03-S01-T02）。
     *
     * バッテリー最適化は`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`（個別の除外を
     * その場でダイアログ確認する画面）の方がタップ数は少ないが、その利用には
     * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`権限のマニフェスト追加が要り、Google Playの
     * ポリシー審査対象になる。現時点でこのアプリはその権限を持たないため、
     * マニフェストを変更しない`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`
     * （対象外アプリの一覧画面。そこから手動で本アプリを探して切り替える）を使う。
     * 個別リクエストへ変えるかはPlay審査への影響を含む製品判断のため、ここでは選ばない。
     */
    fun settingsIntentFor(context: Context, item: HealthItem): Intent = when (item) {
        HealthItem.FINE_LOCATION, HealthItem.BACKGROUND_LOCATION, HealthItem.NOTIFICATION ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        HealthItem.LOCATION_SERVICES ->
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        HealthItem.BATTERY_OPTIMIZATION ->
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
}
