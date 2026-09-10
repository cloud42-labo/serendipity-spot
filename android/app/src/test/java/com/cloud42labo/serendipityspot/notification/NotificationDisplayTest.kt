package com.cloud42labo.serendipityspot.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import com.cloud42labo.serendipityspot.data.Spot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * SPOT-03-S03-T03: 「実機で確認する」だったHuman Taskを自動テストへ移管する
 * （2026-09-06 Approach Review再分析）。[NotificationHelperTest]がbodyFor/titleForという
 * 純粋関数の境界ケースを固定しているのに対し、こちらは[NotificationHelper.notifyNearby]を
 * 実際に呼び出し、OSに渡される`Notification`オブジェクトの中身（`extras`のタイトル・本文
 * 文字列）をRobolectric（JVM上、emulator不要。[OnboardingIntroLayoutTest]と同じ既存の
 * 代替手段）で検証する。ジオフェンスの実発火は「モック/注入」の対象
 * （[GeofenceBroadcastReceiver]がnotifyNearby()を呼ぶ入口そのもの）なので、GMSの
 * GeofencingEventを介さずここから直接起動する。
 *
 * **保証範囲の明示（Codexレビュー指摘、PR #31）**: ここで固定するのは`Notification`へ
 * 渡される文字列ペイロードそのものであり、OSの通知シェードに実際に描画されるピクセル・
 * OEM独自スキンでの見た目・バックグラウンドのジオフェンス経由での実配信は検証しない
 * （Robolectricの`ShadowNotificationManager`が保持するのはpostされた`Notification`
 * インスタンスの中身までで、実描画やOS配信経路の再現ではない）。位置情報・ジオフェンス・
 * バックグラウンド通知の変更はAGENTS.mdの実機確認ルール（L45-52）に該当するが、
 * 本Taskの範囲（文字列組み立て・打ち切り・通知ペイロード）はAI/自動化可能と2026-09-06の
 * Approach Reviewで判定済みであり、そのDone条件はこのテストで満たす。OEM固有描画の
 * 確認は、その判定どおり任意の実機デモ観察に委ね、Acceptance Gateにはしない。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationDisplayTest {

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun grantNotificationPermission() {
        // 実機・実OSでは通知権限の許諾UIを人が操作するが、その許諾自体はSPOT-03-S03-T03の
        // 検証対象（通知本文の組み立て）ではないため、Robolectric上では最初から許諾済みとして
        // 固定する（Android 13+のPOST_NOTIFICATIONS実行時権限、notifyNearby()のガード節）。
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun postedNotificationFor(spot: Spot): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).getNotification(spot.id.hashCode())
        assertNotNull("通知が実際に発行されていること: ${spot.id}", notification)
        return notification
    }

    private fun titleOf(notification: Notification): String =
        notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun bodyOf(notification: Notification): String =
        // BigTextStyleを使っているため、通知シェードに実際に描画される本文はEXTRA_BIG_TEXT。
        // EXTRA_TEXT（折りたたみ時の1行）と両方setContentText(body)/bigText(body)で
        // 同じbodyを渡しているため、シェードの表示内容としてはどちらも一致するはず。
        (notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: notification.extras.getCharSequence(Notification.EXTRA_TEXT)).toString()

    private fun spot(memo: String, title: String = "いい感じの喫茶店") = Spot(
        id = "spot-${title.hashCode()}-${memo.hashCode()}",
        lat = 35.0,
        lng = 139.0,
        title = title,
        memo = memo,
    )

    // --- ①打ち切りロジック ---

    @Test
    fun `long memo is truncated with an ellipsis in the actually posted notification`() {
        val target = spot(memo = "あ".repeat(300))
        NotificationHelper.notifyNearby(context, target)

        val body = bodyOf(postedNotificationFor(target))
        assertEquals(201, body.length) // 200文字 + 省略記号1文字
        assertTrue(body.endsWith("…"))
        assertFalse(body.contains('�')) // 代替文字（文字化け）が出ていない
    }

    // --- ②サロゲートペア/Unicode境界 ---

    @Test
    fun `emoji at the truncation boundary is not split into a mangled character`() {
        val target = spot(memo = "あ".repeat(199) + "😀")
        NotificationHelper.notifyNearby(context, target)

        val body = bodyOf(postedNotificationFor(target))
        assertFalse(body.contains('�'))
        assertTrue(body.none { Character.isHighSurrogate(it) || Character.isLowSurrogate(it) })
        assertEquals("あ".repeat(199) + "…", body)
    }

    // --- ③入場通知 ---

    @Test
    fun `enter notification title is exactly the facility name, no nudge prefix`() {
        val target = spot(memo = "お気に入りのパン屋", title = "いい感じの喫茶店")
        NotificationHelper.notifyNearby(context, target, isNudge = false)

        val title = titleOf(postedNotificationFor(target))
        assertEquals("いい感じの喫茶店", title)
    }

    // --- ④滞在通知(isNudge) ---

    @Test
    fun `dwell nudge notification title is prefixed and still contains the facility name`() {
        val target = spot(memo = "お気に入りのパン屋", title = "いい感じの喫茶店")
        NotificationHelper.notifyNearby(context, target, isNudge = true)

        val title = titleOf(postedNotificationFor(target))
        assertEquals("まだ近くです: いい感じの喫茶店", title)
    }

    // --- ⑤施設名が保持される（長文・絵文字境界を含む本文でも） ---

    @Test
    fun `facility name survives in the title regardless of how long or unusual the memo body is`() {
        val longMemoEnter = spot(memo = "あ".repeat(300), title = "いい感じの喫茶店")
        NotificationHelper.notifyNearby(context, longMemoEnter, isNudge = false)
        assertTrue(titleOf(postedNotificationFor(longMemoEnter)).contains("いい感じの喫茶店"))

        val emojiBoundaryNudge = spot(memo = "あ".repeat(199) + "😀", title = "駅前の花屋")
        NotificationHelper.notifyNearby(context, emojiBoundaryNudge, isNudge = true)
        assertTrue(titleOf(postedNotificationFor(emojiBoundaryNudge)).contains("駅前の花屋"))
    }
}
