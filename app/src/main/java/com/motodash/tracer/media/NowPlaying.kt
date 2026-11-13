package com.motodash.tracer.media

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat

/**
 * อ่านและควบคุมเพลงที่กำลังเล่นจากแอปอื่น (YouTube Music, Spotify ฯลฯ)
 * ผ่าน MediaSession โดยไม่ต้องสลับไปหน้าแอปนั้น
 */
object NowPlaying {
    var title by mutableStateOf<String?>(null)
    var artist by mutableStateOf<String?>(null)
    var art by mutableStateOf<Bitmap?>(null)
    var playing by mutableStateOf(false)
    var source by mutableStateOf<String?>(null)
    var durationMs by mutableStateOf(0L)
    var permission by mutableStateOf(false)

    private var controller: MediaController? = null
    private var manager: MediaSessionManager? = null
    private lateinit var listenerName: ComponentName
    private val main = Handler(Looper.getMainLooper())

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = readMeta(metadata)
        override fun onPlaybackStateChanged(state: PlaybackState?) { playing = state?.state == PlaybackState.STATE_PLAYING }
        override fun onSessionDestroyed() { attach(null) }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    fun hasPermission(ctx: Context) =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** เรียกตอนเปิดแอป / กลับมาที่แอป */
    fun start(ctx: Context) {
        permission = hasPermission(ctx)
        if (!permission) return
        listenerName = ComponentName(ctx, MediaListenerService::class.java)
        val m = ctx.getSystemService(MediaSessionManager::class.java) ?: return
        if (manager == null) {
            manager = m
            m.addOnActiveSessionsChangedListener(sessionsListener, listenerName, main)
        }
        pick(m.getActiveSessions(listenerName))
    }

    private fun pick(list: List<MediaController>) {
        // เลือกตัวที่กำลังเล่นก่อน
        val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        attach(c)
    }

    private fun attach(c: MediaController?) {
        if (controller?.sessionToken == c?.sessionToken && c != null) return
        controller?.unregisterCallback(callback)
        controller = c
        source = c?.packageName
        if (c == null) {
            title = null; artist = null; art = null; playing = false; durationMs = 0
            return
        }
        c.registerCallback(callback, main)
        readMeta(c.metadata)
        playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
    }

    private fun readMeta(m: MediaMetadata?) {
        title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
        artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        durationMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
    }

    /** ตำแหน่งเพลงปัจจุบัน (คำนวณจากความเร็วการเล่น) */
    fun positionMs(): Long {
        val s = controller?.playbackState ?: return 0
        if (s.state != PlaybackState.STATE_PLAYING) return s.position
        return s.position + ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong()
    }

    fun playPause() {
        val c = controller ?: return
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }
    fun pause() { if (playing) controller?.transportControls?.pause() }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
    val hasController get() = controller != null

    /**
     * เล่นเพลงจากแอปที่เลือก โดยไม่สลับไปหน้าแอปนั้น:
     *  1. ถ้าแอปเปิดอยู่แล้ว (มี session) → สั่งเล่นเลย
     *  2. ถ้ายังไม่เปิด → ส่ง "ปุ่ม Play" ไปที่แอปนั้นเบื้องหลัง (เหมือนกดปุ่มบนหูฟัง) แอปจะเล่นเพลงล่าสุดต่อ
     *  3. ถ้ายังไม่เล่นใน 3 วินาที → เปิดแอปแป๊บหนึ่งแล้วดึง MotoDash กลับมา
     */
    fun playFrom(activity: Activity, pkg: String) {
        sessionOf(pkg)?.let { attach(it); it.transportControls.play(); return }

        sendPlayButton(activity, pkg)
        main.postDelayed({
            val s = sessionOf(pkg)
            if (s != null) {
                attach(s)
                if (s.playbackState?.state != PlaybackState.STATE_PLAYING) s.transportControls.play()
                return@postDelayed
            }
            // ทางสำรอง: เปิดแอปเบื้องหลังแล้วกลับมา
            val launch = activity.packageManager.getLaunchIntentForPackage(pkg) ?: return@postDelayed
            activity.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            main.postDelayed({
                activity.startActivity(
                    Intent(activity, activity.javaClass)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
                main.postDelayed({
                    sessionOf(pkg)?.let { attach(it); it.transportControls.play() }
                        ?: sendPlayButton(activity, pkg)
                }, 1500)
            }, 2500)
        }, 3000)
    }

    private fun sessionOf(pkg: String): MediaController? =
        manager?.getActiveSessions(listenerName)?.firstOrNull { it.packageName == pkg }

    /** ส่งปุ่ม Play (MEDIA_BUTTON) ตรงไปที่แอปเพลง */
    private fun sendPlayButton(ctx: Context, pkg: String) {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            ctx.sendBroadcast(
                Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY))
            )
        }
    }
}
