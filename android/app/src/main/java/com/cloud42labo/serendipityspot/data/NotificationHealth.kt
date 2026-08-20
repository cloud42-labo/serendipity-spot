package com.cloud42labo.serendipityspot.data

/**
 * 通知が確実に届くために必要な端末状態を1つにまとめた診断結果（SPOT-03-S01-T01）。
 * 既存の[com.cloud42labo.serendipityspot.ui.components.PermissionRecoveryHint]は
 * 権限が拒否された直後の復帰導線（地図上のブロッキングなバナー）であり、こちらは
 * それとは別に「今、通知を受け取れる状態か」をまとめて確認できる非ブロッキングな
 * 診断表示（[com.cloud42labo.serendipityspot.ui.SpotListSheet]の診断欄）に使う。
 */
data class NotificationHealth(
    val hasFineLocation: Boolean,
    val hasBackgroundLocation: Boolean,
    val isNotificationDeliveryHealthy: Boolean,
    val isLocationServicesEnabled: Boolean,
    val isBatteryOptimizationIgnored: Boolean,
) {
    /** 問題がある項目だけを表示順で返す。空なら全項目健全。 */
    val issues: List<HealthItem>
        get() = buildList {
            if (!hasFineLocation) add(HealthItem.FINE_LOCATION)
            if (!hasBackgroundLocation) add(HealthItem.BACKGROUND_LOCATION)
            if (!isNotificationDeliveryHealthy) add(HealthItem.NOTIFICATION)
            if (!isLocationServicesEnabled) add(HealthItem.LOCATION_SERVICES)
            if (!isBatteryOptimizationIgnored) add(HealthItem.BATTERY_OPTIMIZATION)
        }

    val allHealthy: Boolean
        get() = issues.isEmpty()
}

/**
 * 診断項目ごとに表示ラベルと誘導先設定画面が異なるため、判定結果を種類として持つ。
 */
enum class HealthItem(val label: String) {
    FINE_LOCATION("正確な位置情報（「おおよそ」ではなく「正確」の許可が必要）"),
    BACKGROUND_LOCATION("バックグラウンド位置情報"),
    NOTIFICATION("通知（権限・チャネル）"),
    LOCATION_SERVICES("位置情報サービス（端末設定）"),
    BATTERY_OPTIMIZATION("バッテリー最適化の対象外設定"),
}
