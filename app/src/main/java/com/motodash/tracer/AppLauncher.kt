package com.motodash.tracer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent

/** เปิดแอปภายนอก (Maps / YouTube / เพลง) และส่งปุ่มควบคุมเพลง */
object AppLauncher {
    const val MAPS = "com.google.android.apps.maps"
    const val YOUTUBE = "com.google.android.youtube"
    const val YT_MUSIC = "com.google.android.apps.youtube.music"
    const val SPOTIFY = "com.spotify.music"

    fun open(ctx: Context, pkg: String, split: Boolean, uri: Uri? = null) {
        val intent = (if (uri != null) Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
        else ctx.packageManager.getLaunchIntentForPackage(pkg))
            ?: return openStore(ctx, pkg)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // แบ่งจอ: แอปที่เปิดจะอยู่ข้างๆ MotoDash (One UI รองรับ)
        if (split) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            openStore(ctx, pkg)
        }
    }

    /** นำทางด้วย Google Maps (mode=l = โหมดรถจักรยานยนต์ ถ้าพื้นที่นั้นรองรับ) */
    fun navigate(ctx: Context, destination: String, split: Boolean) {
        val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=l")
        open(ctx, MAPS, split, uri)
    }

    private fun openStore(ctx: Context, pkg: String) {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** ส่งปุ่ม ก่อนหน้า / เล่น-หยุด / ถัดไป ให้แอปเพลงที่กำลังเล่นอยู่ */
    fun mediaKey(ctx: Context, keyCode: Int) {
        val am = ctx.getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
