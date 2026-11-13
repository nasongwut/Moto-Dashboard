package com.motodash.tracer.obd

enum class ObdStatus { NOT_CONFIGURED, CONNECTING, CONNECTED, ERROR }

data class ObdData(
    val status: ObdStatus = ObdStatus.NOT_CONFIGURED,
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val coolantC: Int? = null,
    val throttlePct: Int? = null,
    val batteryV: Float? = null,
    val dtcs: List<String> = emptyList(),
    val dtcChecked: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    /** วิธีเชื่อมต่อ (Bluetooth / สาย USB / WiFi) */
    val link: String? = null,
    val updatedAt: Long = 0,
)
