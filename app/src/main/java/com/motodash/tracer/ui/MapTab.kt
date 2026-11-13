package com.motodash.tracer.ui

import android.graphics.Color as AColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.motodash.tracer.map.Voice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motodash.tracer.AppState
import com.motodash.tracer.Prefs
import com.motodash.tracer.map.Maneuver
import com.motodash.tracer.map.Navigator
import com.motodash.tracer.map.Place
import com.motodash.tracer.obd.ObdData
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** แผนที่ในแอป (OpenStreetMap) + ค้นหา + นำทางแบบบอกทางเลี้ยว */
@Composable
fun MapTab(prefs: Prefs, obd: ObdData, speed: Int?, riding: Boolean) {
    val ctx = LocalContext.current
    val location by AppState.location.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var follow by remember { mutableStateOf(true) }
    var recent by remember { mutableStateOf(prefs.recentDestinations.mapNotNull { Place.decode(it) }) }

    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            overlayManager.tilesOverlay.setColorFilter(android.graphics.ColorMatrixColorFilter(floatArrayOf(-1f,0f,0f,0f,255f,0f,-1f,0f,0f,255f,0f,0f,-1f,0f,255f,0f,0f,0f,1f,0f))) // แผนที่โทนมืด
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(13.7563, 100.5018))
        }
    }
    val me = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(ctx), map).apply { enableMyLocation() }
    }
    val routeLine = remember {
        Polyline(map).apply {
            outlinePaint.color = AColor.parseColor("#FFB020")
            outlinePaint.strokeWidth = 14f
        }
    }
    val destMarker = remember { Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) } }

    DisposableEffect(Unit) {
        map.overlays.add(routeLine)
        map.overlays.add(me)
        map.onResume()
        onDispose {
            me.disableMyLocation()
            map.onPause()
            map.onDetach()
        }
    }

    // ติดตามตำแหน่ง + อัปเดตการนำทาง
    LaunchedEffect(location, follow) {
        val l = location ?: return@LaunchedEffect
        Navigator.onLocation(l)
        if (follow) {
            map.controller.animateTo(GeoPoint(l.latitude, l.longitude))
            if (l.hasBearing() && (speed ?: 0) >= AppState.RIDING_KMH) map.mapOrientation = -l.bearing
        }
    }

    // วาดเส้นทาง / หมุดปลายทาง
    val route = Navigator.route
    val dest = Navigator.destination
    LaunchedEffect(route, dest) {
        routeLine.setPoints(route?.points ?: emptyList())
        map.overlays.remove(destMarker)
        if (dest != null) {
            destMarker.position = dest.point
            destMarker.title = dest.name
            map.overlays.add(destMarker)
        }
        if (route != null && !follow) map.zoomToBoundingBox(BoundingBox.fromGeoPoints(route.points), true, 80)
        map.invalidate()
    }

    fun go(p: Place) {
        prefs.addRecent(p.encode())
        recent = prefs.recentDestinations.mapNotNull { Place.decode(it) }
        query = ""
        follow = true
        Navigator.navigateTo(p, location)
    }

    Box(Modifier.fillMaxSize().clip(PanelShape)) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())

        // การ์ดบอกทางเลี้ยว
        Navigator.currentStep?.takeIf { route != null }?.let { step ->
            Row(
                Modifier.align(Alignment.TopStart).padding(12.dp).width(360.dp)
                    .clip(RoundedCornerShape(16.dp)).background(C.NavGreen).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(maneuverIcon(step.icon), contentDescription = null, tint = C.Text, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(distText(Navigator.distToStepM), color = C.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
                    Text(step.text, color = Color(0xFFC9F2E2), fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // ข้อมูลรถด้านขวา
        Column(
            Modifier.align(Alignment.TopEnd).padding(12.dp).width(150.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiniValue("ความเร็ว", speed?.toString() ?: "--", "km/h")
            MiniValue("รอบเครื่อง", obd.rpm?.let { "%,d".format(it) } ?: "--", "")
            MiniValue("อุณหภูมิน้ำ", obd.coolantC?.toString() ?: "--", "°C")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashButton("", { follow = true; location?.let { map.controller.animateTo(GeoPoint(it.latitude, it.longitude)) } },
                    icon = Icons.Filled.MyLocation, selected = follow)
                DashButton("", {
                    follow = false; map.mapOrientation = 0f
                    route?.let { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(it.points), true, 80) }
                }, icon = Icons.Filled.ZoomOutMap, enabled = route != null)
            }
        }

        // แถบล่าง: ระหว่างนำทาง = แถบเล็กมุมซ้ายล่าง (ไม่บังแผนที่) · ไม่ได้นำทาง = ช่องค้นหา
        if (route != null && dest != null) {
            val eta = remember(Navigator.remainingS.toInt() / 30) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + (Navigator.remainingS * 1000).toLong()))
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(12.dp)
                    .clip(RoundedCornerShape(50)).background(C.Bar.copy(alpha = 0.92f))
                    .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(IntrinsicSize.Max)) {
                    Text("ถึง $eta", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${distText(Navigator.remainingM)} · ${(Navigator.remainingS / 60).toInt()} นาที", color = C.Muted, fontSize = 13.sp)
                }
                Spacer(Modifier.width(12.dp))
                RoundIcon(
                    if (Voice.enabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    if (Voice.enabled) "ปิดเสียงนำทาง" else "เปิดเสียงนำทาง",
                    tint = if (Voice.enabled) C.Accent else C.Dim,
                ) { Voice.toggle() }
                Spacer(Modifier.width(6.dp))
                RoundIcon(Icons.Filled.Close, "หยุดนำทาง", tint = C.RecText, bg = C.RecBg) {
                    Navigator.stop(); map.mapOrientation = 0f
                }
            }
        } else {
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp).fillMaxWidth(0.62f)) {
                val showList = Navigator.results.isNotEmpty() || (query.isEmpty() && recent.isNotEmpty() && !riding)
                if (showList) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(14.dp)).background(C.Bar.copy(alpha = 0.95f))
                            .verticalScroll(rememberScrollState()).padding(6.dp),
                    ) {
                        val items = Navigator.results.ifEmpty { recent }
                        items.forEach { p ->
                            Row(
                                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp))
                                    .clickable { go(p) }.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(if (Navigator.results.isEmpty()) Icons.Filled.History else Icons.Filled.Place, null, tint = C.Muted, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(p.name, color = C.Text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Bar.copy(alpha = 0.95f)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; if (it.isEmpty()) Navigator.results = emptyList() },
                        enabled = !riding, // ห้ามพิมพ์ขณะรถวิ่ง
                        placeholder = { Text(if (riding) "จอดรถก่อนค้นหาปลายทาง" else "ค้นหาปลายทาง") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { Navigator.search(query, location) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = C.Accent, unfocusedBorderColor = C.BtnBorder,
                            focusedTextColor = C.Text, unfocusedTextColor = C.Text,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    DashButton(
                        if (Navigator.searching) "…" else "ค้นหา", { Navigator.search(query, location) },
                        icon = Icons.Filled.Search, primary = true, enabled = query.isNotBlank() && !riding,
                    )
                }
                Navigator.error?.let {
                    Spacer(Modifier.height(6.dp))
                    OverlayLabel(it, color = C.Warn)
                }
            }
        }

        // เครดิตแผนที่ (เงื่อนไขการใช้ OpenStreetMap)
        Text(
            "© OpenStreetMap contributors", color = C.Dim, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
        )
    }
}

/** ปุ่มกลมเล็ก (ไม่บังแผนที่) */
@Composable
private fun RoundIcon(icon: ImageVector, desc: String, tint: Color, bg: Color = C.Btn, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(bg).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun MiniValue(label: String, value: String, unit: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Bar.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(label, color = C.Muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
            if (unit.isNotEmpty()) Text(" $unit", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

private fun distText(m: Double) = if (m >= 1000) "%.1f กม.".format(m / 1000) else "${(m / 10).toInt() * 10} ม."

private fun maneuverIcon(m: Maneuver) = when (m) {
    Maneuver.LEFT -> Icons.Filled.TurnLeft
    Maneuver.RIGHT -> Icons.Filled.TurnRight
    Maneuver.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft
    Maneuver.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
    Maneuver.UTURN -> Icons.Filled.UTurnLeft
    Maneuver.ROUNDABOUT -> Icons.Filled.RoundaboutRight
    Maneuver.ARRIVE -> Icons.Filled.Flag
    Maneuver.STRAIGHT -> Icons.Filled.Straight
}
