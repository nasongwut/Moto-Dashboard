package com.motodash.tracer

import android.location.Location
import com.motodash.tracer.obd.ObdData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

/** สถานะการบันทึกกล้อง */
data class RecState(
    val recording: Boolean = false,
    /** เปิด 2 กล้องพร้อมกันได้ (กล้องหลังมือถือ + กล้องเซลฟี่) */
    val dual: Boolean = false,
    val cameraReady: Boolean = false,
    val currentStamp: String? = null,
    val usedBytes: Long = 0,
    val quotaBytes: Long = 0,
    val message: String? = null,
)

/** ศูนย์กลางข้อมูลที่ทุกหน้าจอใช้ร่วมกัน */
object AppState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** ความเร็วที่ถือว่า "รถกำลังวิ่ง" (ล็อกวิดีโอ / Playback / การพิมพ์) */
    const val RIDING_KMH = 5

    val obd = MutableStateFlow(ObdData())
    val rec = MutableStateFlow(RecState())
    val gpsSpeedKmh = MutableStateFlow<Float?>(null)
    val location = MutableStateFlow<Location?>(null)
    val externalPower = MutableStateFlow(false)

    /** ใช้ความเร็วจาก OBD ก่อน ถ้าไม่มีค่อยใช้ GPS */
    val speedKmh: StateFlow<Int?> = combine(obd, gpsSpeedKmh) { o, g ->
        o.speedKmh ?: g?.roundToInt()
    }.stateIn(scope, SharingStarted.Eagerly, null)
}
