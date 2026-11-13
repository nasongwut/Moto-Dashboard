package com.motodash.tracer.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** เพลงในเครื่อง: ดึงไฟล์เพลงจาก MediaStore แล้วเล่นในแอป (เล่นต่อแม้สลับแท็บ) */
object LocalMusic {
    data class Track(val title: String, val artist: String, val durationMs: Long, val uri: android.net.Uri)

    var tracks by mutableStateOf<List<Track>>(emptyList())
    var index by mutableIntStateOf(-1)
    var playing by mutableStateOf(false)
    var shuffle by mutableStateOf(false)

    private var player: ExoPlayer? = null

    val permission: String
        get() = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    /** อ่านรายการเพลงในเครื่อง (เพลงเท่านั้น ไม่รวมเสียงแจ้งเตือน) */
    fun load(ctx: Context) {
        if (!hasPermission(ctx)) return
        val list = mutableListOf<Track>()
        val cols = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION,
        )
        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                list += Track(
                    title = c.getString(1) ?: "ไม่มีชื่อ",
                    artist = c.getString(2)?.takeIf { it != "<unknown>" } ?: "",
                    durationMs = c.getLong(3),
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                )
            }
        }
        tracks = list
    }

    private fun player(ctx: Context): ExoPlayer = player ?: ExoPlayer.Builder(ctx.applicationContext).build().also { p ->
        p.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { index = p.currentMediaItemIndex }
        })
        player = p
    }

    /** เล่นเพลงลำดับที่ i (โหลดทั้งรายการเป็นเพลย์ลิสต์) */
    fun play(ctx: Context, i: Int) {
        val p = player(ctx)
        if (p.mediaItemCount != tracks.size) {
            p.setMediaItems(tracks.map {
                MediaItem.Builder().setUri(it.uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).build())
                    .build()
            })
        }
        p.shuffleModeEnabled = shuffle
        p.seekTo(i, 0)
        p.prepare()
        p.play()
        index = i
    }

    fun playPause(ctx: Context) {
        val p = player ?: run { if (tracks.isNotEmpty()) play(ctx, 0); return }
        if (p.isPlaying) p.pause() else p.play()
    }
    fun pause() { player?.pause() }
    fun next() { player?.seekToNextMediaItem() }
    fun previous() { player?.seekToPreviousMediaItem() }
    fun toggleShuffle() { shuffle = !shuffle; player?.shuffleModeEnabled = shuffle }
    fun positionMs(): Long = player?.currentPosition ?: 0
    fun durationMs(): Long = player?.duration?.takeIf { it > 0 } ?: 0
    val current: Track? get() = tracks.getOrNull(index)
}
