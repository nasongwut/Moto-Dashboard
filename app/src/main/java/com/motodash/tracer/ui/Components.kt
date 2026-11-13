package com.motodash.tracer.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PanelShape = RoundedCornerShape(16.dp)

/** กล่องค่าตัวเลขใหญ่ (ความเร็ว, อุณหภูมิน้ำ, แบต, คันเร่ง) */
@Composable
fun ValueTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    bar: Float? = null,
    barColor: Color = C.Accent,
    valueColor: Color = C.Text,
    valueSize: TextUnit = 44.sp,
) {
    Column(
        modifier.clip(PanelShape).background(C.Panel).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = C.Muted, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = valueColor, fontSize = valueSize, fontWeight = FontWeight.Bold, lineHeight = valueSize * 1.05f, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text(unit, color = C.Muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (bar != null) {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.Line)) {
                Box(
                    Modifier.fillMaxWidth(bar.coerceIn(0f, 1f)).fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp)).background(barColor)
                )
            }
        }
    }
}

/** เกจโค้ง 270° พร้อมโซนแดง (ใช้กับรอบเครื่องหรือความเร็ว) */
@Composable
fun ArcGauge(
    value: Int?,
    unitLabel: String,
    modifier: Modifier = Modifier,
    max: Int = 12000,
    redFrom: Int? = 10000,
    /** ตัวเลขตรงกลาง (null = ใช้ค่าเดียวกับวงโค้ง) */
    centerText: String? = null,
    /** บรรทัดเล็กใต้ตัวเลข เช่น รอบเครื่อง */
    subText: String? = null,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val stroke = size.minDimension * 0.07f
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)
            drawArc(C.Line, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke))
            if (redFrom != null) {
                val red = 270f * redFrom / max
                drawArc(C.Alert.copy(alpha = 0.75f), 135f + red, 270f - red, false, topLeft, arcSize, style = Stroke(stroke))
            }
            val v = 270f * (value ?: 0).coerceIn(0, max) / max
            if (v > 0f) {
                drawArc(
                    if (redFrom != null && (value ?: 0) >= redFrom) C.Alert else C.Accent,
                    135f, v, false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                centerText ?: value?.let { "%,d".format(it) } ?: "--",
                color = C.Text, fontSize = if (centerText != null) 72.sp else 52.sp, fontWeight = FontWeight.Bold,
                lineHeight = if (centerText != null) 74.sp else 54.sp,
            )
            Text(unitLabel, color = C.Muted, fontSize = 15.sp)
            if (subText != null) Text(subText, color = C.Accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun RpmGauge(rpm: Int?, modifier: Modifier = Modifier) = ArcGauge(rpm, "รอบ/นาที", modifier)

/** ปุ่มใหญ่ (สูง ≥ 56dp กดได้ทั้งที่ใส่ถุงมือ) */
@Composable
fun DashButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = when {
        !enabled -> C.Btn.copy(alpha = 0.5f)
        primary -> C.Accent
        selected -> Color(0xFF262D36)
        else -> C.Btn
    }
    val fg = when {
        !enabled -> C.Dim
        primary -> C.Bg
        selected -> C.Accent
        else -> C.Text
    }
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(bg)
            .then(if (primary) Modifier else Modifier.border(1.dp, if (selected) C.Accent else C.BtnBorder, shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        if (text.isNotEmpty()) Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** แถบความคืบหน้า/พื้นที่ */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.Line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
    }
}

/** ป้ายลอยบนภาพกล้อง */
@Composable
fun OverlayLabel(text: String, modifier: Modifier = Modifier, color: Color = C.Text) {
    Text(
        text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xBF07090B))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
