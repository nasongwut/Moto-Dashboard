package com.motodash.tracer.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.motodash.tracer.AppLauncher
import com.motodash.tracer.AppState
import com.motodash.tracer.media.LocalMusic
import com.motodash.tracer.media.NowPlaying
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** ซ่อนแถบหัว, แถบล่าง และปุ่ม/แบนเนอร์ "เปิดในแอป" ของ YouTube ให้เหลือแต่เนื้อหา */
const val YT_CLEAN_JS = """
(function(){
  if (document.getElementById('motodash-style')) return;
  var s = document.createElement('style');
  s.id = 'motodash-style';
  s.textContent =
    'ytm-mobile-topbar-renderer, header.mobile-topbar-header, .mobile-topbar-header, #header-bar,' +
    'ytm-pivot-bar-renderer, ytm-open-app-button, .open-app-button, a[href^="intent:"],' +
    'ytm-app-promo-renderer, ytm-mealbar-promo-renderer, .mealbar-promo-renderer, ytm-statement-banner-renderer,' +
    'ytm-promoted-sparkles-web-renderer, ytm-companion-ad-renderer { display:none !important; }' +
    'ytm-app, #app, .page-container, ytm-browse { padding-top:0 !important; margin-top:0 !important; }' +
    'html, body { background:#07090B !important; }';
  (document.head || document.documentElement).appendChild(s);
})();
"""

private const val YT_PAUSE_JS = "document.querySelectorAll('video').forEach(function(v){v.pause()});"

enum class Source(val label: String, val pkg: String?, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    LOCAL("ในเครื่อง", null, Icons.Filled.LibraryMusic),
    YOUTUBE("YouTube", null, Icons.Filled.SmartDisplay),
    YT_MUSIC("YT Music", AppLauncher.YT_MUSIC, Icons.Filled.Album),
    SPOTIFY("Spotify", AppLauncher.SPOTIFY, Icons.Filled.Headphones),
}

/** ปุ่มเลือกแหล่งเสียงแบบเล็ก */
@Composable
private fun SourceChip(s: Source, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) C.TabActive else C.Panel)
            .border(1.5.dp, if (selected) C.Accent else C.Panel, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(s.icon, contentDescription = null, tint = if (selected) C.Accent else C.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(s.label, color = if (selected) C.Accent else C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** สถานะแท็บสื่อที่อยู่ได้ตลอด (ไม่หายเมื่อสลับแท็บ) */
object MediaUi {
    var source by mutableStateOf(Source.YT_MUSIC)
    /** ตำแหน่งที่จะวาด YouTube บนจอ (null = ซ่อน แต่ยังเล่นเสียงอยู่) */
    var ytBounds by mutableStateOf<Rect?>(null)
    /** วิดีโอโหมดเต็มจอของ YouTube (null = ไม่ได้เต็มจอ) */
    var fullscreen by mutableStateOf<android.view.View?>(null)
    /** สั่งออกจากโหมดเต็มจอ */
    var exitFullscreen: (() -> Unit)? = null
}

/** วิดีโอ YouTube แบบเต็มจอ (ทับทุกอย่าง) · ขณะรถวิ่งซ่อนภาพ เสียงเล่นต่อ */
@Composable
fun YouTubeFullscreen(riding: Boolean) {
    val v = MediaUi.fullscreen ?: return
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        androidx.compose.runtime.key(v) {
            AndroidView(factory = { (v.parent as? ViewGroup)?.removeView(v); v }, modifier = Modifier.fillMaxSize())
        }
        if (riding) {
            Column(
                Modifier.fillMaxSize().background(C.Bg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                Icon(Icons.Filled.VideocamOff, contentDescription = null, tint = C.Warn, modifier = Modifier.size(52.dp))
                Text("ซ่อนวิดีโอขณะรถวิ่ง · เสียงเล่นต่อ", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        DashButton(
            "ออกจากเต็มจอ", { MediaUi.exitFullscreen?.invoke() },
            icon = Icons.Filled.FullscreenExit,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        )
    }
}

/**
 * วาง YouTube ไว้ที่ชั้นบนสุดของหน้าจอเสมอ:
 * ตอนดู = ขนาดเท่ากรอบในแท็บสื่อ · ตอนไม่ดู = เล็ก 1 จุดโปร่งใส (เสียงเล่นต่อ)
 */
@Composable
fun YouTubeHost(web: WebView) {
    val b = MediaUi.ytBounds
    val d = LocalDensity.current
    val mod = if (b != null) {
        Modifier.offset { IntOffset(b.left.roundToInt(), b.top.roundToInt()) }
            .size(with(d) { b.width.toDp() }, with(d) { b.height.toDp() })
            .clip(RoundedCornerShape(16.dp))
    } else {
        Modifier.size(1.dp).alpha(0f)
    }
    AndroidView(factory = { (web.parent as? ViewGroup)?.removeView(web); web }, modifier = mod)
}

/** แท็บสื่อ: เลือกได้ทีละอย่าง เล่นต่อเนื่องแม้สลับแท็บ ไม่เด้งออกไปแอปอื่น */
@Composable
fun MediaTab(youtube: WebView, speed: Int?, riding: Boolean) {
    val ctx = LocalContext.current
    val source = MediaUi.source

    LaunchedEffect(Unit) { NowPlaying.start(ctx) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // เมนูเลือกแหล่งเสียง (เล็ก กะทัดรัด)
        Column(Modifier.width(132.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Source.entries.forEach { s ->
                SourceChip(s, selected = source == s) {
                    if (s == source) return@SourceChip
                    MediaUi.source = s
                    // เล่นได้ทีละอย่าง: หยุดแหล่งอื่นทั้งหมด
                    if (s != Source.YOUTUBE) youtube.evaluateJavascript(YT_PAUSE_JS, null)
                    if (s != Source.LOCAL) LocalMusic.pause()
                    if (s.pkg == null) NowPlaying.pause()
                    if (s.pkg != null && NowPlaying.source != s.pkg) {
                        NowPlaying.pause()
                        (ctx as? Activity)?.let { NowPlaying.playFrom(it, s.pkg) }
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (source) {
                Source.YOUTUBE -> YouTubePanel(youtube, speed, riding)
                Source.LOCAL -> LocalPanel()
                else -> MusicPanel(source)
            }
        }
    }
}

/** YouTube: แถบควบคุมเล็กของเรา + พื้นที่วิดีโอ · ขณะรถวิ่งซ่อนภาพ เสียงเล่นต่อ */
@Composable
private fun YouTubePanel(web: WebView, speed: Int?, riding: Boolean) {
    var rect by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(rect, riding) { MediaUi.ytBounds = if (riding) null else rect }
    DisposableEffect(Unit) { onDispose { MediaUi.ytBounds = null } }
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // แถบเล็ก: ย้อนกลับ · หน้าแรก · ค้นหา (แทนแถบหัวของ YouTube)
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(C.Panel).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SmallIcon(Icons.AutoMirrored.Filled.ArrowBack, "ย้อนกลับ") { if (web.canGoBack()) web.goBack() }
            SmallIcon(Icons.Filled.Home, "หน้าแรก YouTube") { web.loadUrl("https://m.youtube.com/") }
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(50)).background(C.Bg).padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) Text(if (riding) "จอดรถก่อนค้นหา" else "ค้นหาใน YouTube", color = C.Dim, fontSize = 14.sp)
                BasicTextField(
                    value = query, onValueChange = { query = it }, enabled = !riding, singleLine = true,
                    textStyle = TextStyle(color = C.Text, fontSize = 15.sp), cursorBrush = SolidColor(C.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (query.isNotBlank()) web.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode(query))
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SmallIcon(Icons.Filled.Search, "ค้นหา", enabled = !riding && query.isNotBlank()) {
                web.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode(query))
            }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().clip(PanelShape).background(C.Placeholder)
                .onGloballyPositioned { rect = it.boundsInRoot() },
        ) {
            if (riding) {
                Column(
                    Modifier.fillMaxSize().background(C.Bg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                ) {
                    Icon(Icons.Filled.VideocamOff, contentDescription = null, tint = C.Warn, modifier = Modifier.size(52.dp))
                    Text("ซ่อนวิดีโอขณะรถวิ่ง", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "เล่นต่อเป็นเสียงเท่านั้น · ภาพจะกลับมาเมื่อความเร็วต่ำกว่า ${AppState.RIDING_KMH} km/h",
                        color = C.Muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                    )
                    Text("${speed ?: "--"} km/h", color = C.Warn, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    DashButton("", onClick, icon = icon, enabled = enabled, modifier = Modifier.height(40.dp))
}

/** เพลงจาก YouTube Music / Spotify ที่เล่นอยู่เบื้องหลัง ควบคุมจากที่นี่ */
@Composable
private fun MusicPanel(source: Source) {
    val ctx = LocalContext.current
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { pos = NowPlaying.positionMs(); delay(500) } }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.weight(1f).fillMaxWidth().clip(PanelShape).background(C.Panel).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!NowPlaying.permission) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ต้องอนุญาตครั้งเดียว", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "ให้ MotoDash เห็นและควบคุมเพลงที่กำลังเล่นได้ (การเข้าถึงการแจ้งเตือน) " +
                            "หรือรัน ./build.sh install ระบบจะอนุญาตให้อัตโนมัติ",
                        color = C.Muted, fontSize = 15.sp,
                    )
                    DashButton("เปิดหน้าอนุญาต", {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, primary = true)
                }
            } else {
                Box(Modifier.size(180.dp).clip(RoundedCornerShape(16.dp)).background(C.Placeholder), contentAlignment = Alignment.Center) {
                    val art = NowPlaying.art
                    if (art != null) Image(art.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Filled.MusicNote, null, tint = C.Dim, modifier = Modifier.size(64.dp))
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        NowPlaying.title ?: "ยังไม่มีเพลงเล่นอยู่",
                        color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(NowPlaying.artist, appName(NowPlaying.source)).joinToString(" · ").ifEmpty { "กดเล่นเพื่อเริ่ม ${source.label}" },
                        color = C.Muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    val d = NowPlaying.durationMs
                    ProgressBar(if (d > 0) pos.toFloat() / d else 0f, C.Accent, Modifier.fillMaxWidth())
                    Row {
                        Text(fmtMs(pos), color = C.Dim, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Text(if (d > 0) fmtMs(d) else "", color = C.Dim, fontSize = 13.sp)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().height(84.dp).clip(PanelShape).background(C.Panel).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            val activity = ctx as? Activity
            DashButton("", { NowPlaying.previous() }, icon = Icons.Filled.SkipPrevious, enabled = NowPlaying.hasController)
            DashButton("", {
                when {
                    NowPlaying.hasController -> NowPlaying.playPause()
                    source.pkg != null && activity != null -> NowPlaying.playFrom(activity, source.pkg)
                }
            }, icon = if (NowPlaying.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, primary = true, modifier = Modifier.width(110.dp))
            DashButton("", { NowPlaying.next() }, icon = Icons.Filled.SkipNext, enabled = NowPlaying.hasController)
        }
    }
}

/** เพลงในเครื่อง: รายการเพลง + ตัวเล่น */
@Composable
private fun LocalPanel() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(LocalMusic.hasPermission(ctx)) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok -> granted = ok; if (ok) LocalMusic.load(ctx) }
    LaunchedEffect(granted) { if (granted && LocalMusic.tracks.isEmpty()) LocalMusic.load(ctx) }
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { pos = LocalMusic.positionMs(); delay(500) } }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().clip(PanelShape).background(C.Panel).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text("อนุญาตให้อ่านไฟล์เพลงในเครื่อง", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            DashButton("อนุญาต", { launcher.launch(LocalMusic.permission) }, primary = true)
        }
        return
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // รายการเพลง
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.weight(1f).fillMaxHeight().clip(PanelShape).background(C.Panel).padding(6.dp),
        ) {
            if (LocalMusic.tracks.isEmpty()) item {
                Text("ไม่พบไฟล์เพลงในเครื่อง", color = C.Dim, fontSize = 15.sp, modifier = Modifier.padding(14.dp))
            }
            items(LocalMusic.tracks.size) { i ->
                val t = LocalMusic.tracks[i]
                val active = i == LocalMusic.index
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (active) C.TabActive else C.Panel)
                        .clickable { LocalMusic.play(ctx, i) }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.MusicNote, null, tint = if (active) C.Accent else C.Dim, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = if (active) C.Accent else C.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (t.artist.isNotEmpty()) Text(t.artist, color = C.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(fmtMs(t.durationMs), color = C.Dim, fontSize = 12.sp)
                }
            }
        }
        // กำลังเล่น
        Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().clip(PanelShape).background(C.Panel).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                val cur = LocalMusic.current
                Text(cur?.title ?: "เลือกเพลงจากรายการ", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(cur?.artist ?: "${LocalMusic.tracks.size} เพลงในเครื่อง", color = C.Muted, fontSize = 14.sp, maxLines = 1)
                val d = LocalMusic.durationMs()
                ProgressBar(if (d > 0) pos.toFloat() / d else 0f, C.Accent, Modifier.fillMaxWidth())
                Row {
                    Text(fmtMs(pos), color = C.Dim, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text(if (d > 0) fmtMs(d) else "", color = C.Dim, fontSize = 13.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().height(76.dp).clip(PanelShape).background(C.Panel).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                DashButton("", { LocalMusic.toggleShuffle() }, icon = Icons.Filled.Shuffle, selected = LocalMusic.shuffle)
                DashButton("", { LocalMusic.previous() }, icon = Icons.Filled.SkipPrevious)
                DashButton("", { LocalMusic.playPause(ctx) }, icon = if (LocalMusic.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, primary = true)
                DashButton("", { LocalMusic.next() }, icon = Icons.Filled.SkipNext)
            }
        }
    }
}

private fun appName(pkg: String?) = when (pkg) {
    AppLauncher.YT_MUSIC -> "YouTube Music"
    AppLauncher.SPOTIFY -> "Spotify"
    AppLauncher.YOUTUBE -> "YouTube"
    null -> null
    else -> pkg.substringAfterLast('.')
}

private fun fmtMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
