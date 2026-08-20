package com.cloud42labo.serendipityspot.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.cloud42labo.serendipityspot.data.HealthItem
import com.cloud42labo.serendipityspot.data.NotificationHealth

/**
 * 通知ヘルス診断（SPOT-03-S01）。バックグラウンド位置情報・通知権限・位置情報サービス・
 * バッテリー最適化の4項目を1回で読み、項目ごとに正しい誘導先設定画面を返す。
 *
 * MainActivityの`hasForegroundLocationPermission`等と同様に権限チェックはここでも
 * 単体で行っている（Contextだけで完結する純粋な読み取りのため、Activityの
 * private関数を再利用せずContextベースで独立させた）。
 */
object NotificationHealthChecker {

    fun check(context: Context): NotificationHealth {
        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            // Q未満はバックグラウンド位置情報が別権限として存在しない（付与済み扱い）。
            true
        }
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            // TIRAMISU未満は通知権限の概念自体が無い（付与済み扱い）。
            true
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationServicesEnabled = LocationManagerCompat.isLocationEnabled(locationManager)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isBatteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)

        return NotificationHealth(
            hasBackgroundLocation = hasBackgroundLocation,
            hasNotificationPermission = hasNotificationPermission,
            isLocationServicesEnabled = isLocationServicesEnabled,
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
        )
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
        HealthItem.BACKGROUND_LOCATION, HealthItem.NOTIFICATION ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        HealthItem.LOCATION_SERVICES ->
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        HealthItem.BATTERY_OPTIMIZATION ->
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
}
