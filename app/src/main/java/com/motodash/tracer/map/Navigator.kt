package com.motodash.tracer.map

import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Place(val name: String, val lat: Double, val lon: Double) {
    val point get() = GeoPoint(lat, lon)
    fun encode() = "$name|$lat|$lon"

    companion object {
        fun decode(s: String): Place? = s.split('|').takeIf { it.size == 3 }?.let {
            val lat = it[1].toDoubleOrNull() ?: return null
            val lon = it[2].toDoubleOrNull() ?: return null
            Place(it[0], lat, lon)
        }
    }
}

data class Step(val point: GeoPoint, val text: String, val icon: Maneuver, val distanceM: Double, val durationS: Double)

enum class Maneuver { STRAIGHT, LEFT, RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, UTURN, ROUNDABOUT, ARRIVE }

data class Route(val points: List<GeoPoint>, val steps: List<Step>, val distanceM: Double, val durationS: Double)

/**
 * นำทางในแอป: ค้นหาด้วย Nominatim (OpenStreetMap) และหาเส้นทางด้วย OSRM
 * เก็บสถานะไว้ที่นี่ เพื่อให้สลับแท็บแล้วการนำทางยังอยู่
 */
object Navigator {
    private const val UA = "MotoDash/0.2 (Android; personal motorcycle dashboard)"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var results by mutableStateOf<List<Place>>(emptyList())
    var searching by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var destination by mutableStateOf<Place?>(null)
    var route by mutableStateOf<Route?>(null)
    var stepIndex by mutableIntStateOf(0)
    var remainingM by mutableStateOf(0.0)
    var remainingS by mutableStateOf(0.0)
    var distToStepM by mutableStateOf(0.0)
    private var lastReroute = 0L
    // จำว่าพูดขั้นตอนไหนไปแล้ว (กันพูดซ้ำ)
    private var saidStart = -1
    private var saidFar = -1
    private var saidNear = -1
    private var startDist = 0.0

    val currentStep: Step? get() = route?.steps?.getOrNull(stepIndex)

    fun search(query: String, near: Location?) {
        if (query.isBlank()) return
        searching = true; error = null
        scope.launch {
            try {
                results = withContext(Dispatchers.IO) { nominatim(query, near) }
                if (results.isEmpty()) error = "ไม่พบสถานที่"
            } catch (e: Exception) {
                error = "ค้นหาไม่ได้ (ตรวจอินเทอร์เน็ต)"
            } finally {
                searching = false
            }
        }
    }

    fun navigateTo(place: Place, from: Location?) {
        destination = place
        results = emptyList()
        if (from == null) { error = "รอสัญญาณ GPS… จะหาเส้นทางให้อัตโนมัติเมื่อจับตำแหน่งได้"; return }
        requestRoute(from, place)
    }

    fun stop() {
        destination = null; route = null; stepIndex = 0; error = null
        resetSpeech()
    }

    private fun resetSpeech() { saidStart = -1; saidFar = -1; saidNear = -1 }

    private fun requestRoute(from: Location, to: Place) {
        if (route != null) Voice.speak("กำลังหาเส้นทางใหม่")
        lastReroute = System.currentTimeMillis()
        searching = true; error = null
        scope.launch {
            try {
                route = withContext(Dispatchers.IO) { osrm(GeoPoint(from.latitude, from.longitude), to.point) }
                stepIndex = if ((route?.steps?.size ?: 0) > 1) 1 else 0
                resetSpeech()
                onLocation(from)
            } catch (e: Exception) {
                error = "หาเส้นทางไม่ได้ (ตรวจอินเทอร์เน็ต)"
            } finally {
                searching = false
            }
        }
    }

    /** เรียกทุกครั้งที่ตำแหน่งเปลี่ยน: เลื่อนขั้นตอน, คำนวณระยะ, หาเส้นทางใหม่เมื่อออกนอกเส้น */
    fun onLocation(loc: Location) {
        // ตั้งปลายทางไว้ก่อน GPS จับได้ → หาเส้นทางทันทีเมื่อได้ตำแหน่งแรก
        val pending = destination
        if (route == null && pending != null && !searching && System.currentTimeMillis() - lastReroute > 5_000) {
            requestRoute(loc, pending)
            return
        }
        val r = route ?: return
        val here = GeoPoint(loc.latitude, loc.longitude)
        while (stepIndex < r.steps.lastIndex && here.distanceToAsDouble(r.steps[stepIndex].point) < 30) stepIndex++
        val step = r.steps.getOrNull(stepIndex) ?: return
        distToStepM = here.distanceToAsDouble(step.point)
        val after = r.steps.drop(stepIndex)
        remainingM = distToStepM + after.sumOf { it.distanceM }
        remainingS = after.sumOf { it.durationS } + distToStepM / 10.0
        // เสียงนำทาง: บอกเมื่อเริ่มช่วงใหม่, เมื่อเหลือ ~200 ม., และตอนถึงจุดเลี้ยว
        when {
            saidStart != stepIndex -> {
                saidStart = stepIndex
                startDist = distToStepM
                if (distToStepM > 60) Voice.speak("อีก ${Voice.spoken(distToStepM)} ${step.text}")
            }
            distToStepM in 80.0..250.0 && saidFar != stepIndex && startDist > 400 -> {
                saidFar = stepIndex
                Voice.speak("อีก ${Voice.spoken(distToStepM)} ${step.text}")
            }
            distToStepM <= 50 && saidNear != stepIndex -> {
                saidNear = stepIndex
                Voice.speak(step.text)
            }
        }
        if (step.icon == Maneuver.ARRIVE && distToStepM < 30) {
            Voice.speak("ถึงปลายทางแล้ว")
            error = "ถึงปลายทางแล้ว"
            route = null
            destination = null
            return
        }
        // ออกนอกเส้นทาง > 80 ม. → หาเส้นทางใหม่ (ไม่ถี่กว่า 20 วินาที)
        val off = r.points.minOfOrNull { it.distanceToAsDouble(here) } ?: 0.0
        val dest = destination
        if (off > 80 && dest != null && System.currentTimeMillis() - lastReroute > 20_000) requestRoute(loc, dest)
    }

    // ---------------- เครือข่าย ----------------

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "th,en")
        c.connectTimeout = 8000; c.readTimeout = 10000
        try {
            if (c.responseCode != 200) throw Exception("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private fun nominatim(q: String, near: Location?): List<Place> {
        var url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&countrycodes=th&q=" +
            URLEncoder.encode(q, "UTF-8")
        if (near != null) {
            val d = 0.5 // ให้ความสำคัญกับบริเวณใกล้ ±0.5°
            url += "&viewbox=${near.longitude - d},${near.latitude + d},${near.longitude + d},${near.latitude - d}"
        }
        val arr = JSONArray(get(url))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val name = o.optString("name").ifBlank { o.optString("display_name").substringBefore(',') }
            val area = o.optString("display_name").split(',').drop(1).take(2).joinToString(",").trim()
            Place(if (area.isNotBlank()) "$name · $area" else name, o.getString("lat").toDouble(), o.getString("lon").toDouble())
        }
    }

    private fun osrm(from: GeoPoint, to: GeoPoint): Route {
        val url = "https://router.project-osrm.org/route/v1/driving/" +
            "${from.longitude},${from.latitude};${to.longitude},${to.latitude}" +
            "?overview=full&geometries=polyline&steps=true"
        val root = JSONObject(get(url))
        if (root.optString("code") != "Ok") throw Exception(root.optString("code"))
        val r = root.getJSONArray("routes").getJSONObject(0)
        val steps = mutableListOf<Step>()
        val legs = r.getJSONArray("legs")
        for (li in 0 until legs.length()) {
            val ss = legs.getJSONObject(li).getJSONArray("steps")
            for (si in 0 until ss.length()) {
                val s = ss.getJSONObject(si)
                val m = s.getJSONObject("maneuver")
                val loc = m.getJSONArray("location")
                val (text, icon) = instruction(m, s.optString("name"))
                steps += Step(GeoPoint(loc.getDouble(1), loc.getDouble(0)), text, icon, s.optDouble("distance"), s.optDouble("duration"))
            }
        }
        return Route(decodePolyline(r.getString("geometry")), steps, r.optDouble("distance"), r.optDouble("duration"))
    }

    /** แปลงคำสั่ง OSRM เป็นภาษาไทย */
    private fun instruction(m: JSONObject, road: String): Pair<String, Maneuver> {
        val type = m.optString("type")
        val mod = m.optString("modifier")
        val onRoad = if (road.isNotBlank()) " · $road" else ""
        if (type == "arrive") return "ถึงปลายทาง" to Maneuver.ARRIVE
        if (type == "depart") return "ออกเดินทาง$onRoad" to Maneuver.STRAIGHT
        if (type == "roundabout" || type == "rotary") {
            val exit = m.optInt("exit", 0)
            return (if (exit > 0) "เข้าวงเวียน ออกทางที่ $exit$onRoad" else "เข้าวงเวียน$onRoad") to Maneuver.ROUNDABOUT
        }
        val (word, icon) = when (mod) {
            "left" -> "เลี้ยวซ้าย" to Maneuver.LEFT
            "right" -> "เลี้ยวขวา" to Maneuver.RIGHT
            "sharp left" -> "เลี้ยวซ้ายหักศอก" to Maneuver.LEFT
            "sharp right" -> "เลี้ยวขวาหักศอก" to Maneuver.RIGHT
            "slight left" -> (if (type == "fork") "ชิดซ้ายที่ทางแยก" else "เบี่ยงซ้าย") to Maneuver.SLIGHT_LEFT
            "slight right" -> (if (type == "fork") "ชิดขวาที่ทางแยก" else "เบี่ยงขวา") to Maneuver.SLIGHT_RIGHT
            "uturn" -> "กลับรถ" to Maneuver.UTURN
            else -> (if (type == "merge") "รวมเลน" else "ตรงไป") to Maneuver.STRAIGHT
        }
        return "$word$onRoad" to icon
    }

    private fun decodePolyline(enc: String): List<GeoPoint> {
        val out = ArrayList<GeoPoint>()
        var i = 0; var lat = 0; var lng = 0
        while (i < enc.length) {
            var shift = 0; var result = 0; var b: Int
            do { b = enc[i++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            shift = 0; result = 0
            do { b = enc[i++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            out += GeoPoint(lat / 1e5, lng / 1e5)
        }
        return out
    }
}
