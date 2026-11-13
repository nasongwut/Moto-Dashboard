package com.motodash.tracer.ui

import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.motodash.tracer.camera.ClipRepository
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun PlaybackScreen(riding: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var clips by remember { mutableStateOf(ClipRepository.list(ctx)) }
    var selected by remember { mutableIntStateOf(if (clips.isNotEmpty()) 0 else -1) }
    var mode by remember { mutableStateOf(PbMode.FRONT) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    val vFront = remember { VideoView(ctx) }
    val vRear = remember { VideoView(ctx) }
    val clip = clips.getOrNull(selected)
    val hasRear = clip?.rear != null
    // วิดีโอที่กำลังแสดงอยู่ (ปุ่มควบคุมสั่งพร้อมกันทุกตัว)
    val active = when {
        mode == PbMode.BOTH && hasRear -> listOf(vFront, vRear)
        mode == PbMode.REAR && hasRear -> listOf(vRear)
        else -> listOf(vFront)
    }
    val main = active.first()
    val timeFmt = remember { SimpleDateFormat("dd MMM · HH:mm:ss", Locale("th", "TH")) }

    // ล็อก Playback ขณะรถวิ่ง
    LaunchedEffect(riding) { if (riding) { vFront.pause(); vRear.pause(); playing = false } }

    LaunchedEffect(selected, mode) {
        val c = clips.getOrNull(selected) ?: return@LaunchedEffect
        val at = main.currentPosition
        vFront.stopPlayback(); vRear.stopPlayback()
        val pairs = listOf(vFront to c.front, vRear to c.rear).filter { (v, f) -> v in active && f != null }
        pairs.forEach { (v, f) ->
            v.setVideoPath(f!!.absolutePath)
            v.setOnPreparedListener { mp -> if (v == main) duration = mp.duration; if (at > 0) v.seekTo(at) }
            if (!riding) v.start()
        }
        playing = !riding && pairs.isNotEmpty()
    }
    LaunchedEffect(main) {
        while (true) { position = main.currentPosition; delay(500) }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.width(300.dp).fillMaxHeight().clip(PanelShape).background(C.Panel).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DashButton("", onBack, icon = Icons.AutoMirrored.Filled.ArrowBack)
                Spacer(Modifier.width(10.dp))
                Text("คลิปทั้งหมด", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${clips.size} คลิป", color = C.Dim, fontSize = 13.sp)
            }
            if (clips.isEmpty()) {
                Text("ยังไม่มีคลิป", color = C.Dim, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(clips, key = { _, c -> c.stamp + c.locked }) { i, c ->
                    val active = i == selected
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (active) C.TabActive else C.Panel)
                            .border(2.dp, if (active) C.Accent else C.Panel, RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) { selected = i }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.startedAt?.let { timeFmt.format(it) } ?: c.stamp, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                (if (c.rear != null) "หน้า + หลัง" else "หน้า") + " · ${c.sizeMb} MB",
                                color = C.Dim, fontSize = 12.sp,
                            )
                        }
                        if (c.locked) Icon(Icons.Filled.Lock, contentDescription = "คลิปถูกล็อก", tint = C.Warn, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // เลือกกล้อง: หน้า / หลัง / ทั้งสอง
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PbMode.entries.forEach { m ->
                    DashButton(m.label, { mode = m }, selected = mode == m, enabled = m == PbMode.FRONT || hasRear)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(PanelShape).background(C.Placeholder), contentAlignment = Alignment.Center) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (vFront in active) Box(Modifier.weight(1f).fillMaxHeight()) {
                        VideoBox(vFront)
                        OverlayLabel("กล้องหน้า", Modifier.align(Alignment.TopStart).padding(10.dp))
                    }
                    if (vRear in active) Box(Modifier.weight(1f).fillMaxHeight()) {
                        VideoBox(vRear)
                        OverlayLabel("กล้องหลัง", Modifier.align(Alignment.TopStart).padding(10.dp))
                    }
                }
                if (riding) {
                    Box(Modifier.fillMaxSize().background(C.Bg.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                        Text("ดูย้อนหลังได้เมื่อรถจอดเท่านั้น", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            ProgressBar(if (duration > 0) position.toFloat() / duration else 0f, C.Accent, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${fmt(position)} / ${fmt(duration)}", color = C.Muted, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                DashButton("", {
                    val t = (main.currentPosition - 10_000).coerceAtLeast(0); active.forEach { it.seekTo(t) }
                }, icon = Icons.Filled.Replay10, enabled = !riding)
                DashButton(
                    "", {
                        if (playing) { active.forEach { it.pause() }; playing = false }
                        else { val t = main.currentPosition; active.forEach { it.seekTo(t); it.start() }; playing = true }
                    },
                    icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, primary = true, enabled = !riding && selected >= 0,
                )
                DashButton("", {
                    val t = main.currentPosition + 10_000; active.forEach { it.seekTo(t) }
                }, icon = Icons.Filled.Forward10, enabled = !riding)
                DashButton("ล็อก", {
                    clips.getOrNull(selected)?.let { ClipRepository.lock(ctx, it) }
                    clips = ClipRepository.list(ctx)
                }, icon = Icons.Filled.Lock, enabled = clips.getOrNull(selected)?.locked == false)
            }
        }
    }
}

private enum class PbMode(val label: String) { FRONT("กล้องหน้า"), REAR("กล้องหลัง"), BOTH("ทั้งสองกล้อง") }

/** ใส่ VideoView เดิมกลับเข้าไปใน layout ใหม่ได้ (ถอดออกจากที่เก่าก่อน) */
@Composable
private fun VideoBox(v: VideoView) {
    AndroidView(factory = { (v.parent as? ViewGroup)?.removeView(v); v }, modifier = Modifier.fillMaxSize())
}

private fun fmt(ms: Int): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}
