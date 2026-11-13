package com.motodash.tracer

import android.content.Context

/** ค่าตั้งค่าของแอป (เก็บในเครื่อง) */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("motodash", Context.MODE_PRIVATE)

    /** MAC address ของ ELM327 (Bluetooth Classic / SPP) */
    var elmMac: String?
        get() = sp.getString("elm_mac", null)
        set(v) = sp.edit().putString("elm_mac", v).apply()

    /** วิธีต่อ ELM327: Bluetooth / สาย USB (OTG) / WiFi */
    var elmLink: com.motodash.tracer.obd.ElmLink
        get() = runCatching { com.motodash.tracer.obd.ElmLink.valueOf(sp.getString("elm_link", "BLUETOOTH")!!) }
            .getOrDefault(com.motodash.tracer.obd.ElmLink.BLUETOOTH)
        set(v) = sp.edit().putString("elm_link", v.name).apply()

    /** ELM327 แบบ WiFi */
    var elmHost: String
        get() = sp.getString("elm_host", "192.168.0.10") ?: "192.168.0.10"
        set(v) = sp.edit().putString("elm_host", v.trim()).apply()

    var elmPort: Int
        get() = sp.getInt("elm_port", 35000)
        set(v) = sp.edit().putInt("elm_port", v).apply()

    var elmName: String?
        get() = sp.getString("elm_name", null)
        set(v) = sp.edit().putString("elm_name", v).apply()

    /** URL กล้องหลัง เช่น rtsp://192.168.1.10:554/live */
    var rtspUrl: String
        get() = sp.getString("rtsp_url", "") ?: ""
        set(v) = sp.edit().putString("rtsp_url", v.trim()).apply()

    /** พื้นที่สูงสุดสำหรับคลิป (GB) ก่อนวนทับ */
    var quotaGb: Int
        get() = sp.getInt("quota_gb", 20)
        set(v) = sp.edit().putInt("quota_gb", v.coerceIn(2, 200)).apply()

    /** เปิด Maps / YouTube / เพลง แบบแบ่งจอข้างแอปเรา */
    var splitScreen: Boolean
        get() = sp.getBoolean("split", true)
        set(v) = sp.edit().putBoolean("split", v).apply()

    /** ปลายทางล่าสุด (สูงสุด 5) */
    var recentDestinations: List<String>
        get() = (sp.getString("recent_dest", "") ?: "").split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("recent_dest", v.take(5).joinToString("\n")).apply()

    fun addRecent(dest: String) {
        recentDestinations = listOf(dest) + recentDestinations.filter { it != dest }
    }

    val quotaBytes: Long get() = quotaGb * 1024L * 1024L * 1024L

    /** เปิดกล้องหลังมือถือ + กล้องเซลฟี่พร้อมกัน (ปิดได้ถ้าเครื่องมีปัญหา) */
    var dualCamera: Boolean
        get() = sp.getBoolean("dual_camera", true)
        set(v) = sp.edit().putBoolean("dual_camera", v).apply()

    /** เสียงนำทาง */
    var voiceNav: Boolean
        get() = sp.getBoolean("voice_nav", true)
        set(v) = sp.edit().putBoolean("voice_nav", v).apply()

    // ---------- Dashboard ที่ปรับแต่งได้ ----------

    /** ค่าที่แสดงในแต่ละช่อง (เก็บ 6 ช่องเสมอ ใช้ตาม dashCount) */
    var dashTiles: List<String>
        get() = (sp.getString("dash_tiles", null) ?: "SPEED,COOLANT,BATTERY,THROTTLE,GPS_SPEED,CLOCK").split(',')
        set(v) = sp.edit().putString("dash_tiles", v.joinToString(",")).apply()

    /** จำนวนช่อง: 4 (2×2) หรือ 6 (3×2) */
    var dashCount: Int
        get() = sp.getInt("dash_count", 4)
        set(v) = sp.edit().putInt("dash_count", if (v == 6) 6 else 4).apply()

    /** เกจใหญ่: COMBO (วง = รอบเครื่อง, กลาง = ความเร็ว), RPM หรือ SPEED */
    var dashGauge: String
        get() = sp.getString("dash_gauge", "COMBO") ?: "COMBO"
        set(v) = sp.edit().putString("dash_gauge", v).apply()
}
