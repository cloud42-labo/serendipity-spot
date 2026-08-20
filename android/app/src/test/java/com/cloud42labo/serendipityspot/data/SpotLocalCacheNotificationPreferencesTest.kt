package com.cloud42labo.serendipityspot.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SPOT-03-S02のレビュー指摘: 通知曜日をすべてOFFにして保存した場合、
 * 保存→（アプリ再起動を模した）復元を経ても空集合のまま維持されることを固定する。
 * [SpotLocalCache.parseAllowedDaysCsv]は、SharedPreferencesへの読み書きを介さない
 * 純粋関数として切り出してあるため、Robolectric等のContext依存なしにテストできる。
 */
class SpotLocalCacheNotificationPreferencesTest {

    @Test
    fun `never-saved key (null) falls back to ALL_DAYS`() {
        assertEquals(NotificationPreferences.ALL_DAYS, SpotLocalCache.parseAllowedDaysCsv(null))
    }

    @Test
    fun `explicitly saved empty selection stays empty, not ALL_DAYS`() {
        // saveNotificationPreferences()は空集合を "" として保存する
        // (emptySet<Int>().sorted().joinToString(",") == "")。
        assertEquals(emptySet<Int>(), SpotLocalCache.parseAllowedDaysCsv(""))
    }

    @Test
    fun `round-trips a normal day selection`() {
        val saved = setOf(2, 3, 4, 5, 6).sorted().joinToString(",")
        assertEquals(setOf(2, 3, 4, 5, 6), SpotLocalCache.parseAllowedDaysCsv(saved))
    }

    @Test
    fun `round-trips a full week selection`() {
        val saved = NotificationPreferences.ALL_DAYS.sorted().joinToString(",")
        assertEquals(NotificationPreferences.ALL_DAYS, SpotLocalCache.parseAllowedDaysCsv(saved))
    }

    @Test
    fun `ignores malformed entries but keeps the rest`() {
        assertEquals(setOf(1, 3), SpotLocalCache.parseAllowedDaysCsv("1,,x,3"))
    }
}
