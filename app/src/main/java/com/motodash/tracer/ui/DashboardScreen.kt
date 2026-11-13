package com.motodash.tracer.ui

import android.webkit.WebView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motodash.tracer.AppState
import com.motodash.tracer.Prefs
import com.motodash.tracer.RecState
import com.motodash.tracer.camera.DashcamService
import com.motodash.tracer.obd.ObdData
import com.motodash.tracer.obd.ObdStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Tab(val label: String, val icon: ImageVector) {
    DASHBOARD("หน้าปัด", Icons.Filled.Speed),
    CAMERA("กล้อง", Icons.Filled.Videocam),
    MAP("แผนที่", Icons.Filled.Map),
    MEDIA("สื่อ", Icons.Filled.MusicNote),
}

@Composable
fun DashboardScreen(
    service: DashcamService?,
    prefs: Prefs,
    youtube: WebView,
    tab: Tab,
    onTab: (Tab) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val obd by AppState.obd.collectAsStateWithLifecycle()
    val rec by AppState.rec.collectAsStateWithLifecycle()
    val power by AppState.externalPower.collectAsStateWithLifecycle()
    val speed by AppState.speedKmh.collectAsStateWithLifecycle()
    val riding = (speed ?: 0) >= AppState.RIDING_KMH

    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().background(C.Bg))
    // ภาพกล้อง (ชั้นล่าง ใต้เนื้อหา) — แท็บกล้องเว้นพื้นที่โปร่งใสให้มองเห็น
    CameraHost()
    Column(Modifier.fillMaxSize()) {
        StatusBar(obd, rec, power, onOpenSettings)
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            when (tab) {
                Tab.DASHBOARD -> DashboardTab(obd, speed, riding, service)
                Tab.CAMERA -> CameraTab(service, rec, speed, riding)
                Tab.MAP -> MapTab(prefs, obd, speed, riding)
                Tab.MEDIA -> MediaTab(youtube, speed, riding)
            }
        }
        TabBar(tab, onTab)
    }
    // YouTube อยู่ในหน้าจอตลอด (ซ่อนเล็กๆ เมื่อไม่ได้ดู) → เสียงเล่นต่อแม้สลับแท็บ
    YouTubeHost(youtube)
    // ปุ่มเต็มจอของ YouTube → แสดงวิดีโอทับทั้งหน้าจอ
    YouTubeFullscreen(riding)
    }
}

@Composable
private fun StatusBar(obd: ObdData, rec: RecState, power: Boolean, onOpenSettings: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(5_000) }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }

    Row(
        Modifier.fillMaxWidth().height(44.dp).background(C.Bar).padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(14.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(if (rec.recording) C.RecBg else C.Btn)
                .padding(horizontal = 12.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (rec.recording) C.Alert else C.Dim))
            Spacer(Modifier.width(6.dp))
            Text(
                if (rec.recording) "REC กล้องหน้า" else "ไม่ได้บันทึก",
                color = if (rec.recording) C.RecText else C.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.weight(1f))
        StatusValue("แบต", obd.batteryV?.let { "%.1fV".format(it) } ?: "--")
        Spacer(Modifier.width(16.dp))
        StatusValue("น้ำ", obd.coolantC?.let { "$it°C" } ?: "--")
        Spacer(Modifier.width(14.dp))
        Icon(
            if (obd.status == ObdStatus.CONNECTED) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
            contentDescription = if (obd.status == ObdStatus.CONNECTED) "OBD เชื่อมต่อแล้ว" else "OBD ไม่ได้เชื่อมต่อ",
            tint = if (obd.status == ObdStatus.CONNECTED) C.Info else C.Dim,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.Filled.Power,
            contentDescription = if (power) "มีไฟเข้า" else "ไม่มีไฟเข้า",
            tint = if (power) C.Info else C.Dim,
            modifier = Modifier.size(20.dp),
        )
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "ตั้งค่า", tint = C.Muted)
        }
    }
}

@Composable
private fun StatusValue(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.Muted, fontSize = 14.sp)
        Spacer(Modifier.width(4.dp))
        Text(value, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TabBar(current: Tab, onTab: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).background(C.Bar)) {
        Tab.entries.forEach { t ->
            val active = t == current
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (active) C.TabActive else C.Bar)
                    .clickable(role = Role.Tab) { onTab(t) },
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(3.dp).background(C.Accent))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(t.icon, contentDescription = null, tint = if (active) C.Accent else C.Muted, modifier = Modifier.size(26.dp))
                    Text(
                        t.label, color = if (active) C.Accent else C.Muted,
                        fontSize = 17.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
