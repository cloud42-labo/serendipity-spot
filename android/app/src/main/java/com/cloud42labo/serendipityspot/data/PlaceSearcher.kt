package com.cloud42labo.serendipityspot.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** 検索結果の1件。 */
data class PlaceResult(
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double,
)

/**
 * 住所・駅名・施設名から座標を引く。
 *
 * Android 標準の [Geocoder] を使う。Google Play 開発者サービスが入っている端末では
 * Google のジオコーディングを叩くため、別途 API キーを発行する必要がない。
 * Places API の方が候補の質は高いが、有効化と課金設定が増えるので使わない。
 *
 * Geocoder は住所解決が本来の用途で、施設名（POI）の網羅は端末のバックエンド次第。
 * 公式ドキュメントも結果を "best guess" とし、正確性を保証していない。そのため
 * 「引けなかった」ことを握りつぶさず [PlaceSearchOutcome] で呼び出し側へ返す。
 */
class PlaceSearcher(private val context: Context) {

    /**
     * [nearLat]/[nearLng] を中心にした矩形内を優先して探す。指定しないと日本全国が
     * 対象になり、同名の地名が離れた県でヒットする（例:「マリンピア」で新潟県がヒット）。
     */
    suspend fun search(
        query: String,
        nearLat: Double? = null,
        nearLng: Double? = null,
    ): PlaceSearchOutcome = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext PlaceSearchOutcome.Success(emptyList())
        // バックエンドが無い端末では何度試しても空が返る。「該当なし」とは違うので分ける。
        if (!Geocoder.isPresent()) return@withContext PlaceSearchOutcome.Unavailable

        val geocoder = Geocoder(context, Locale.JAPAN)
        try {
            val results = resolveWithFallback(
                bounded = {
                    if (nearLat == null || nearLng == null) null
                    else lookup(geocoder, query, nearLat, nearLng)
                },
                unbounded = { lookup(geocoder, query, null, null) },
            )
            PlaceSearchOutcome.Success(results)
        } catch (e: Exception) {
            PlaceSearchOutcome.Failed(e)
        }
    }

    // 33+ にコールバック版があるが、こちらも動く。分岐を増やす利点が薄いので統一する。
    @Suppress("DEPRECATION")
    private fun lookup(
        geocoder: Geocoder,
        query: String,
        nearLat: Double?,
        nearLng: Double?,
    ): List<PlaceResult> {
        val addresses = if (nearLat != null && nearLng != null) {
            geocoder.getFromLocationName(
                query,
                MAX_RESULTS,
                nearLat - SEARCH_BOUNDS_DEGREES,
                nearLng - SEARCH_BOUNDS_DEGREES,
                nearLat + SEARCH_BOUNDS_DEGREES,
                nearLng + SEARCH_BOUNDS_DEGREES,
            )
        } else {
            geocoder.getFromLocationName(query, MAX_RESULTS)
        }
        return addresses.orEmpty().mapNotNull(::toPlaceResult)
    }

    private fun toPlaceResult(address: Address): PlaceResult? {
        val line = address.getAddressLine(0)
        val name = address.featureName ?: line ?: return null
        return PlaceResult(
            name = name,
            subtitle = line.orEmpty(),
            lat = address.latitude,
            lng = address.longitude,
        )
    }

    companion object {
        private const val MAX_RESULTS = 8

        /** 検索の中心から見た矩形の半幅（度）。約55kmを想定。 */
        private const val SEARCH_BOUNDS_DEGREES = 0.5

        /**
         * 範囲を絞った検索が0件なら、範囲指定なしでもう一度だけ探す。
         *
         * 矩形指定はあくまで「絞り込み」なので、外すことで結果が減ることはない。
         * BUG-SPOT-03-01 では画面内の施設でも0件になっており矩形が主原因とは考えて
         * いないが、矩形付き検索が空を返す端末でも取りこぼさないための保険として置く。
         * ここを通っても外部APIは増えない（同じ Geocoder をもう一度呼ぶだけ）。
         *
         * [bounded] が null を返す場合は、そもそも範囲指定なしで呼ばれている。
         */
        internal fun resolveWithFallback(
            bounded: () -> List<PlaceResult>?,
            unbounded: () -> List<PlaceResult>,
        ): List<PlaceResult> {
            val boundedResults = bounded()
            if (boundedResults != null && boundedResults.isNotEmpty()) return boundedResults
            return unbounded()
        }
    }
}
