package com.cloud42labo.serendipityspot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 範囲を絞った検索が0件だったときに、範囲指定なしでもう一度探すかどうかを固定する。
 *
 * 矩形指定は絞り込みでしかないため、外して結果が減ることはない。BUG-SPOT-03-01 では
 * 画面内の施設でも0件になっており矩形が主原因とは考えていないが、矩形付き検索が空を
 * 返す端末で取りこぼさないための保険として入れている。
 */
class PlaceSearcherFallbackTest {

    private val tokyoTower = PlaceResult("東京タワー", "東京都港区", 35.6586, 139.7454)
    private val faraway = PlaceResult("東京タワー(全国検索)", "東京都港区", 35.6586, 139.7454)

    @Test
    fun `範囲内で見つかったら範囲指定なしの検索は行わない`() {
        // 近くで見つかるならそれを優先する（遠方の同名地名を混ぜない）
        var unboundedCalled = false
        val results = PlaceSearcher.resolveWithFallback(
            bounded = { listOf(tokyoTower) },
            unbounded = { unboundedCalled = true; listOf(faraway) },
        )
        assertEquals(listOf(tokyoTower), results)
        assertTrue("範囲内で見つかったのに全国検索が走っている", !unboundedCalled)
    }

    @Test
    fun `範囲内が0件なら範囲指定なしで探し直す`() {
        // 矩形付き検索が空を返す端末でも取りこぼさないこと
        val results = PlaceSearcher.resolveWithFallback(
            bounded = { emptyList() },
            unbounded = { listOf(faraway) },
        )
        assertEquals(listOf(faraway), results)
    }

    @Test
    fun `そもそも範囲指定なしで呼ばれたら全国検索の結果をそのまま返す`() {
        // 共有からのコールド起動など、地図中心が信用できない場面の経路
        val results = PlaceSearcher.resolveWithFallback(
            bounded = { null },
            unbounded = { listOf(faraway) },
        )
        assertEquals(listOf(faraway), results)
    }

    @Test
    fun `どちらも0件なら空を返す`() {
        // 「該当なし」は握りつぶさず空のまま返し、呼び出し側が0件として扱えること
        val results = PlaceSearcher.resolveWithFallback(
            bounded = { emptyList() },
            unbounded = { emptyList() },
        )
        assertTrue(results.isEmpty())
    }
}
