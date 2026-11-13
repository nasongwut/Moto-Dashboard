package com.motodash.tracer.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motodash.tracer.AppState
import com.motodash.tracer.RecState
import com.motodash.tracer.camera.DashcamService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** ตำแหน่งที่ตัวแบ่งจอ "ดูด" เข้าหา: 0 = กล้องหลังเต็มจอ, 1 = กล้องหน้าเต็มจอ */
private val SNAPS = listOf(0f, 0.3f, 0.5f, 0.7f, 1f)

/** จำสัดส่วนไว้ข้ามการสลับแท็บ */
private var savedSplit = 0.5f

@Composable
fun CameraTab(service: DashcamService?, rec: RecState, speed: Int?, riding: Boolean) {
    var showPlayback by remember { mutableStateOf(false) }
    if (showPlayback) {
        PlaybackScreen(riding = riding, onBack = { showPlayback = false })
        return
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SplitCameras(service, rec, speed, Modifier.weight(1f).fillMaxWidth())
        BottomBar(service, rec) { showPlayback = true }
    }
}

/** ภาพสด 2 กล้อง: ลากซ้าย–ขวาเพื่อปรับว่ากล้องไหนใหญ่ */
@Composable
private fun SplitCameras(service: DashcamService?, rec: RecState, speed: Int?, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val split = remember { Animatable(if (rec.dual) savedSplit else 1f) }
    var dragging by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(rec.dual) }
    LaunchedEffect(rec.dual) {
        if (!rec.dual) split.snapTo(1f)
        else { split.snapTo(savedSplit); showHint = true; delay(4000); showHint = false }
    }

    BoxWithConstraints(
        modifier.pointerInput(rec.dual) {
            if (!rec.dual) return@pointerInput
            detectHorizontalDragGestures(
                onDragStart = { dragging = true; showHint = false },
                onDragEnd = {
                    dragging = false
                    val target = SNAPS.minBy { kotlin.math.abs(it - split.value) }
                    savedSplit = target
                    scope.launch { split.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 400f)) }
                },
                onDragCancel = { dragging = false },
            ) { change, dx ->
                change.consume()
                scope.launch { split.snapTo((split.value + dx / size.width).coerceIn(0f, 1f)) }
            }
        },
    ) {
        val gap = 6.dp
        val total = maxWidth
        val f = split.value
        val frontW: Dp = ((total - gap) * f).coerceAtLeast(0.dp)
        val rearW: Dp = ((total - gap) * (1f - f)).coerceAtLeast(0.dp)

        Row(Modifier.fillMaxSize()) {
            if (frontW > 2.dp) {
                CameraPane(
                    service, front = true, rec = rec, speed = speed,
                    title = "กล้องหน้า", subtitle = if (rec.dual) "ถนน · 720p" else "ถนน · 1080p",
                    icon = Icons.Filled.TwoWheeler, compact = frontW < 260.dp,
                    modifier = Modifier.width(frontW).fillMaxHeight(),
                )
            }
            if (rec.dual) Spacer(Modifier.width(gap))
            if (rec.dual && rearW > 2.dp) {
                CameraPane(
                    service, front = false, rec = rec, speed = null,
                    title = "กล้องหลัง", subtitle = "เซลฟี่ · 720p",
                    icon = Icons.Filled.FaceRetouchingNatural, compact = rearW < 260.dp,
                    modifier = Modifier.width(rearW).fillMaxHeight(),
                )
            }
        }

        if (rec.dual) {
            // ที่จับตัวแบ่ง
            val handleX = (total - gap) * f + gap / 2
            Box(
                Modifier.align(Alignment.CenterStart).offset(x = handleX - 4.dp).size(8.dp, 48.dp)
                    .clip(RoundedCornerShape(50)).background(if (dragging) C.Accent else C.Accent.copy(alpha = 0.6f)),
            )

            // จุดบอกตำแหน่ง
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(50)).background(C.Bar.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val nearest = SNAPS.minBy { kotlin.math.abs(it - f) }
                SNAPS.forEach { s ->
                    Box(
                        Modifier.size(if (s == nearest) 12.dp else 5.dp, 5.dp).clip(RoundedCornerShape(50))
                            .background(if (s == nearest) C.Accent else C.Dim),
                    )
                }
            }

            AnimatedVisibility(
                visible = showHint,
                enter = fadeIn(tween(300)), exit = fadeOut(tween(600)),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            ) {
                Text(
                    "ลากซ้าย–ขวาเพื่อเลือกกล้องที่จะดูใหญ่",
                    color = C.Text, fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(C.Bar.copy(alpha = 0.85f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        if (rec.message != null && !showHint) {
            Text(
                rec.message, color = C.Warn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                    .clip(RoundedCornerShape(50)).background(C.Bar.copy(alpha = 0.9f)).padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun CameraPane(
    service: DashcamService?,
    front: Boolean,
    rec: RecState,
    speed: Int?,
    title: String,
    subtitle: String,
    icon: ImageVector,
    compact: Boolean,
    modifier: Modifier,
) {
    // พื้นที่โปร่งใส: ภาพกล้องจริงวาดโดย CameraHost (อยู่ชั้นล่าง) ตรงตำแหน่งนี้
    Box(modifier.clip(PanelShape).onGloballyPositioned {
        if (front) CameraUi.frontBounds = it.boundsInRoot() else CameraUi.rearBounds = it.boundsInRoot()
    }) {
        DisposableEffect(front) {
            onDispose { if (front) CameraUi.frontBounds = null else CameraUi.rearBounds = null }
        }
        CameraStatus(service, if (front) CameraUi.front else CameraUi.rear)
        // ป้ายชื่อกล้อง
        Row(
            Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0xCC07090B)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = C.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (!compact) {
                Spacer(Modifier.width(6.dp))
                Text(subtitle, color = C.Muted, fontSize = 12.sp)
            }
        }
        if (rec.recording) RecBadge(Modifier.align(Alignment.TopEnd).padding(10.dp))
        if (speed != null && !compact) {
            Text(
                "$speed km/h", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC07090B)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** ป้าย REC กะพริบ */
@Composable
private fun RecBadge(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "rec")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recAlpha")
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xCC07090B)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).alpha(a).clip(CircleShape).background(C.Alert))
        Spacer(Modifier.width(6.dp))
        Text("REC", color = C.RecText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** แถบล่าง: สถานะบันทึก · พื้นที่ · ปุ่ม */
@Composable
private fun BottomBar(service: DashcamService?, rec: RecState, onPlayback: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val start = remember(rec.currentStamp) {
        rec.currentStamp?.let { runCatching { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(it)?.time }.getOrNull() }
    }
    val elapsed = start?.let { ((now - it) / 1000).coerceIn(0, 600) } ?: 0
    val segFrac = elapsed / 600f

    Row(
        Modifier.fillMaxWidth().height(72.dp).clip(PanelShape).background(C.Panel).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // สถานะบันทึก + ความคืบหน้าไฟล์ 10 นาที
        Column(Modifier.width(230.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (rec.recording) C.Alert else C.Dim))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (rec.recording) "กำลังบันทึก · %d:%02d / 10:00".format(elapsed / 60, elapsed % 60) else "ไม่ได้บันทึก",
                    color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(if (rec.recording) segFrac else 0f, C.Alert, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(20.dp))
        // พื้นที่เก็บ
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SdStorage, contentDescription = null, tint = C.Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("%.1f / %.0f GB".format(rec.usedBytes / 1e9, rec.quotaBytes / 1e9), color = C.Muted, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(rec.message ?: "วนทับอัตโนมัติ", color = C.Dim, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(if (rec.quotaBytes > 0) rec.usedBytes.toFloat() / rec.quotaBytes else 0f, C.Info, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(16.dp))
        DashButton("ล็อกคลิป", { service?.lockCurrent() }, icon = Icons.Filled.Lock, enabled = rec.recording)
        Spacer(Modifier.width(8.dp))
        DashButton("ดูย้อนหลัง", onPlayback, icon = Icons.Filled.History, primary = true)
    }
}

/** ข้อความสถานะเมื่อยังไม่มีภาพ (พื้นหลังทึบ ปิดเมื่อภาพมาแล้ว) */
@Composable
private fun CameraStatus(service: DashcamService?, view: PreviewView?) {
    var streaming by remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val obs = androidx.lifecycle.Observer<PreviewView.StreamState> { streaming = it == PreviewView.StreamState.STREAMING }
        view?.previewStreamState?.observeForever(obs)
        onDispose { view?.previewStreamState?.removeObserver(obs) }
    }
    if (!streaming) {
        Box(Modifier.fillMaxSize().background(C.Placeholder), contentAlignment = Alignment.Center) {
            Text(
                when {
                    service == null -> "กำลังเชื่อมต่อบริการกล้อง…"
                    !AppState.rec.collectAsStateWithLifecycle().value.cameraReady -> "กำลังเปิดกล้อง…"
                    else -> "รอภาพจากกล้อง…"
                },
                color = C.Dim, fontSize = 15.sp,
            )
        }
    }
}

/** ตำแหน่งภาพกล้องบนจอ + ตัว PreviewView ที่อยู่ตลอด (ไม่สร้างใหม่ทุกครั้งที่สลับแท็บ) */
object CameraUi {
    var front: PreviewView? = null
    var rear: PreviewView? = null
    var frontBounds by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    var rearBounds by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    fun create(ctx: android.content.Context) {
        if (front != null) return
        fun make() = PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        front = make(); rear = make()
    }
}

/**
 * วางภาพกล้องไว้ในหน้าจอตลอด (ชั้นล่างสุด): แท็บกล้อง = ตรงกรอบภาพ, แท็บอื่น = 1 จุด
 * ทำให้กล้องมีที่ส่งภาพตั้งแต่เปิดแอป ไม่ต้องตั้งค่าใหม่ระหว่างบันทึก
 */
@Composable
fun CameraHost() {
    val d = androidx.compose.ui.platform.LocalDensity.current
    @Composable
    fun place(v: PreviewView?, b: androidx.compose.ui.geometry.Rect?) {
        v ?: return
        val mod = if (b != null) {
            Modifier.offset { androidx.compose.ui.unit.IntOffset(b.left.toInt(), b.top.toInt()) }
                .size(with(d) { b.width.toDp() }, with(d) { b.height.toDp() })
                .clip(PanelShape)
        } else Modifier.size(1.dp)
        AndroidView(factory = { (v.parent as? android.view.ViewGroup)?.removeView(v); v }, modifier = mod)
    }
    place(CameraUi.front, CameraUi.frontBounds)
    place(CameraUi.rear, CameraUi.rearBounds)
}
