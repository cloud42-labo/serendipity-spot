package com.cloud42labo.serendipityspot.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** 現在地からスポットまでの徒歩ルート。 */
data class RouteInfo(
    val points: List<LatLng>,
    val distanceText: String,
    val durationText: String,
)

/**
 * Google Directions API で徒歩ルートを引く。
 *
 * Maps SDK 用に埋め込み済みの APIキー（マニフェストの `com.google.android.geo.API_KEY`）を
 * そのまま流用する。Androidアプリ制限付きキーで Web Service API を呼ぶには
 * `X-Android-Package` / `X-Android-Cert` ヘッダーが必須（無いと REQUEST_DENIED になる）。
 * Cloud Console 側で、このキーの「APIの制限」に Directions API を追加し、
 * 実行に使う署名鍵の SHA-1 を「Androidアプリ」制限に登録しておく必要がある。
 */
class DirectionsRepository(private val context: Context) {

    suspend fun getWalkingRoute(origin: LatLng, destination: LatLng): RouteInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val apiKey = mapsApiKey() ?: return@withContext null
                val url = URL(
                    "https://maps.googleapis.com/maps/api/directions/json" +
                        "?origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}" +
                        "&mode=walking" +
                        "&language=${URLEncoder.encode("ja", "UTF-8")}" +
                        "&key=$apiKey",
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("X-Android-Package", context.packageName)
                connection.setRequestProperty("X-Android-Cert", signingCertSha1())

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                if (json.optString("status") != "OK") return@withContext null

                val route = json.getJSONArray("routes").getJSONObject(0)
                val leg = route.getJSONArray("legs").getJSONObject(0)
                val points = decodePolyline(
                    route.getJSONObject("overview_polyline").getString("points"),
                )

                RouteInfo(
                    points = points,
                    distanceText = leg.getJSONObject("distance").getString("text"),
                    durationText = leg.getJSONObject("duration").getString("text"),
                )
            }.getOrNull()
        }

    private fun mapsApiKey(): String? {
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        return appInfo.metaData?.getString("com.google.android.geo.API_KEY")
    }

    /** 実行中のAPK自身の署名証明書のSHA-1（コロン無し・大文字16進）。 */
    private fun signingCertSha1(): String {
        val signatureBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            info.signingInfo!!.apkContentsSigners.first().toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES,
            )
            @Suppress("DEPRECATION")
            info.signatures!!.first().toByteArray()
        }
        val digest = MessageDigest.getInstance("SHA-1").digest(signatureBytes)
        return digest.joinToString("") { "%02X".format(it) }
    }

    /** Googleのエンコード済みポリラインをデコードする標準アルゴリズム。 */
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var shift = 0
            var result = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }
}
