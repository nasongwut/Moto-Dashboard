package com.motodash.tracer.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motodash.tracer.AppState
import com.motodash.tracer.Prefs
import com.motodash.tracer.camera.DashcamService
import com.motodash.tracer.obd.ObdData
import com.motodash.tracer.obd.ObdStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** ค่าที่เลือกแสดงในช่องของ Dashboard ได้ */
enum class Metric(val label: String, val unit: String) {
    SPEED("ความเร็ว", "km/h"),
    RPM("รอบเครื่อง", "rpm"),
    COOLANT("อุณหภูมิน้ำ", "°C"),
    BATTERY("แรงดันแบตเตอรี่", "V"),
    THROTTLE("ตำแหน่งคันเร่ง", "%"),
    GPS_SPEED("ความเร็ว GPS", "km/h"),
    ALTITUDE("ความสูง", "ม."),
    CLOCK("เวลา", ""),
}

private data class Reading(val text: String, val bar: Float? = null, val barColor: Color = C.Accent, val color: Color = C.Text)

private fun read(m: Metric, obd: ObdData, speed: Int?, now: Long): Reading {
    val loc = AppState.location.value
    return when (m) {
        Metric.SPEED -> Reading(speed?.toString() ?: "--")
        Metric.RPM -> Reading(obd.rpm?.let { "%,d".format(it) } ?: "--", obd.rpm?.let { it / 12000f },
            if ((obd.rpm ?: 0) >= 10000) C.Alert else C.Accent)
        Metric.COOLANT -> {
            val c = obd.coolantC
            val hot = (c ?: 0) >= 105
            Reading(c?.toString() ?: "--", c?.let { (it - 40) / 80f }, if (hot) C.Alert else C.Info, if (hot) C.Alert else C.Text)
        }
        Metric.BATTERY -> {
            val v = obd.batteryV
            Reading(v?.let { "%.1f".format(it) } ?: "--", color = if (v != null && v < 12f) C.Warn else C.Text)
        }
        Metric.THROTTLE -> Reading(obd.throttlePct?.toString() ?: "--", obd.throttlePct?.let { it / 100f })
        Metric.GPS_SPEED -> Reading(AppState.gpsSpeedKmh.value?.roundToInt()?.toString() ?: "--")
        Metric.ALTITUDE -> Reading(loc?.takeIf { it.hasAltitude() }?.altitude?.roundToInt()?.toString() ?: "--")
        Metric.CLOCK -> Reading(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)))
    }
}

/** แท็บแรก: Dashboard (ปรับแต่งได้ด้วยปุ่ม ✎) */
@Composable
fun DashboardTab(obd: ObdData, speed: Int?, riding: Boolean, service: DashcamService?) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var tiles by remember { mutableStateOf(prefs.dashTiles.map { runCatching { Metric.valueOf(it) }.getOrDefault(Metric.SPEED) }) }
    var count by remember { mutableIntStateOf(prefs.dashCount) }
    var gauge by remember { mutableStateOf(prefs.dashGauge) }
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableIntStateOf(-1) } // ช่องที่กำลังเลือกค่า (-2 = เกจ)
    var confirmClear by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    // อ่านตำแหน่งเพื่อให้ช่อง GPS/ความสูงอัปเดต
    AppState.location.collectAsStateWithLifecycle().value

    fun save() { prefs.dashTiles = tiles.map { it.name }; prefs.dashCount = count; prefs.dashGauge = gauge }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---------- เกจใหญ่ ----------
        Box(
            Modifier.weight(0.36f).fillMaxHeight().clip(PanelShape).background(C.Panel)
                .then(if (editing) Modifier.border(2.dp, C.Accent, PanelShape).clickable { picking = -2 } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when (gauge) {
                "SPEED" -> ArcGauge(speed, "km/h", Modifier.fillMaxSize(), max = 220, redFrom = null)
                "RPM" -> RpmGauge(obd.rpm, Modifier.fillMaxSize())
                // ค่าเริ่มต้น: วงโค้ง = รอบเครื่อง · ตัวเลขกลาง = ความเร็ว
                else -> ArcGauge(
                    obd.rpm, "km/h", Modifier.fillMaxSize(),
                    centerText = speed?.toString() ?: "--",
                    subText = "${obd.rpm?.let { "%,d".format(it) } ?: "--"} rpm",
                )
            }
            if (editing) EditBadge(Modifier.align(Alignment.TopEnd).padding(10.dp))
            else IconButton(onClick = { editing = true }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Filled.Tune, contentDescription = "ปรับแต่ง Dashboard", tint = C.Dim)
            }
        }

        Column(Modifier.weight(0.64f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ---------- ช่องค่า 2×2 หรือ 3×2 ----------
            val cols = if (count == 6) 3 else 2
            for (row in 0 until 2) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (col in 0 until cols) {
                        val i = row * cols + col
                        val m = tiles.getOrElse(i) { Metric.SPEED }
                        val r = read(m, obd, speed, now)
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .then(if (editing) Modifier.border(2.dp, C.Accent, PanelShape).clip(PanelShape).clickable { picking = i } else Modifier),
                        ) {
                            ValueTile(
                                m.label, r.text, m.unit, Modifier.fillMaxSize(),
                                bar = r.bar, barColor = r.barColor, valueColor = r.color,
                                valueSize = if (cols == 3) 36.sp else 44.sp,
                            )
                            if (editing) EditBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
                        }
                    }
                }
            }

            // ---------- แถบล่าง: โหมดแก้ไข หรือ สถานะโค้ด ----------
            Row(
                Modifier.fillMaxWidth().height(68.dp).clip(PanelShape).background(C.Panel).padding(start = 18.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing) {
                    Text("แตะช่องเพื่อเปลี่ยนค่า", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    DashButton("4 ช่อง", { count = 4 }, selected = count == 4)
                    Spacer(Modifier.width(8.dp))
                    DashButton("6 ช่อง", { count = 6 }, selected = count == 6)
                    Spacer(Modifier.width(8.dp))
                    DashButton("เสร็จ", { save(); editing = false }, icon = Icons.Filled.Check, primary = true)
                } else {
                    val (icon, tint, title) = when {
                        obd.status != ObdStatus.CONNECTED -> Triple(Icons.Filled.ErrorOutline, C.Dim, statusText(obd))
                        !obd.dtcChecked -> Triple(Icons.Filled.CheckCircle, C.Dim, "ยังไม่ได้อ่านโค้ด")
                        obd.dtcs.isEmpty() -> Triple(Icons.Filled.CheckCircle, C.Info, "ไม่พบโค้ดความผิดปกติ")
                        else -> Triple(Icons.Filled.Warning, C.Warn, "พบ ${obd.dtcs.size} โค้ด: ${obd.dtcs.joinToString(", ")}")
                    }
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (obd.status == ObdStatus.CONNECTED) "ELM327 · ${obd.link ?: ""} · CAN 500k" else (obd.error ?: "ตั้งค่า ELM327 ที่ปุ่มเฟือง"),
                            color = C.Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val canUse = obd.status == ObdStatus.CONNECTED && !obd.busy && service != null
                    DashButton("อ่านโค้ด", { service?.obd?.readDtc() }, enabled = canUse)
                    Spacer(Modifier.width(8.dp))
                    DashButton("ลบโค้ด", { confirmClear = true }, enabled = canUse && !riding && obd.dtcs.isNotEmpty())
                }
            }
        }
    }

    // ---------- ตัวเลือกค่า ----------
    if (picking != -1) {
        val options: List<Pair<String, () -> Unit>> = if (picking == -2) listOf(
            "วงรอบเครื่อง + ความเร็วกลาง" to { gauge = "COMBO" },
            "เกจรอบเครื่อง" to { gauge = "RPM" },
            "เกจความเร็ว" to { gauge = "SPEED" },
        ) else Metric.entries.map { m ->
            "${m.label}${if (m.unit.isNotEmpty()) " (${m.unit})" else ""}" to {
                val i = picking
                tiles = tiles.toMutableList().also { list -> while (list.size <= i) list += Metric.SPEED; list[i] = m }
            }
        }
        AlertDialog(
            onDismissRequest = { picking = -1 },
            title = { Text(if (picking == -2) "เลือกเกจใหญ่" else "เลือกค่าที่จะแสดง") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (label, action) ->
                                DashButton(label, { action(); picking = -1 }, modifier = Modifier.weight(1f))
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = -1 }) { Text("ปิด") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("ลบโค้ดความผิดปกติ?") },
            text = { Text("ไฟเตือนเครื่องยนต์จะดับ แต่ถ้าปัญหายังอยู่ โค้ดจะกลับมาอีก ควรจดโค้ดไว้ก่อน") },
            confirmButton = {
                TextButton(onClick = { service?.obd?.clearDtc(); confirmClear = false }) { Text("ลบ") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("ยกเลิก") } },
        )
    }
}

@Composable
private fun EditBadge(modifier: Modifier) {
    Box(modifier.size(28.dp).clip(RoundedCornerShapeFull).background(C.Accent), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Edit, contentDescription = null, tint = C.Bg, modifier = Modifier.size(16.dp))
    }
}

private val RoundedCornerShapeFull = androidx.compose.foundation.shape.RoundedCornerShape(50)

private fun statusText(obd: ObdData) = when (obd.status) {
    ObdStatus.NOT_CONFIGURED -> "ยังไม่ได้เลือก ELM327"
    ObdStatus.CONNECTING -> "กำลังเชื่อมต่อ ELM327…"
    ObdStatus.ERROR -> "เชื่อมต่อ ELM327 ไม่ได้ · กำลังลองใหม่"
    ObdStatus.CONNECTED -> "เชื่อมต่อแล้ว"
}
