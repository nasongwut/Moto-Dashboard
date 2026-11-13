package com.motodash.tracer.obd

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.motodash.tracer.AppState
import com.motodash.tracer.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** วิธีต่อ ELM327 กับมือถือ */
enum class ElmLink(val label: String) { BLUETOOTH("Bluetooth"), USB("สาย USB (OTG)"), WIFI("WiFi") }

/**
 * คุยกับ ELM327 ได้ 3 แบบ: Bluetooth (SPP), สาย USB (OTG), WiFi
 * Tracer 900GT 2018 ใช้ CAN 11bit 500kbps → ATSP6
 */
class Elm327Client(private val context: Context) {

    private enum class Request { READ_DTC, CLEAR_DTC }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Request>(Channel.UNLIMITED)
    private var job: Job? = null
    @Volatile private var conn: Closeable? = null

    /** เริ่มเชื่อมต่อตามค่าในหน้าตั้งค่า */
    fun start(prefs: Prefs) {
        stop()
        val link = prefs.elmLink
        if (link == ElmLink.BLUETOOTH && prefs.elmMac.isNullOrBlank()) {
            AppState.obd.update { ObdData(status = ObdStatus.NOT_CONFIGURED) }
            return
        }
        job = scope.launch { loop(link, prefs) }
    }

    fun stop() {
        job?.cancel()
        job = null
        closeConn()
    }

    fun readDtc() { requests.trySend(Request.READ_DTC) }
    fun clearDtc() { requests.trySend(Request.CLEAR_DTC) }

    private fun closeConn() {
        runCatching { conn?.close() }
        conn = null
    }

    private suspend fun loop(link: ElmLink, prefs: Prefs) {
        while (currentCoroutineContext().isActive) {
            try {
                AppState.obd.update { it.copy(status = ObdStatus.CONNECTING, error = null, link = link.label) }
                val io = when (link) {
                    ElmLink.BLUETOOTH -> openBluetooth(prefs.elmMac ?: throw IOException("ยังไม่ได้เลือก ELM327"))
                    ElmLink.USB -> openUsb()
                    ElmLink.WIFI -> openWifi(prefs.elmHost, prefs.elmPort)
                }
                val hello = io.send("ATZ", 3000)
                if (link == ElmLink.USB && !hello.contains("ELM", ignoreCase = true)) {
                    throw IOException("สาย USB ไม่ตอบ (ลองเสียบใหม่)")
                }
                for (c in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP6")) io.send(c)
                AppState.obd.update { it.copy(status = ObdStatus.CONNECTED) }

                var tick = 0
                while (currentCoroutineContext().isActive) {
                    requests.tryReceive().getOrNull()?.let { handle(io, it) }

                    val rpm = pid(io, "0C")?.let { if (it.size >= 2) (it[0] * 256 + it[1]) / 4 else null }
                    val speed = pid(io, "0D")?.firstOrNull()
                    var coolant: Int? = null
                    var throttle: Int? = null
                    var volt: Float? = null
                    if (tick % 3 == 0) {
                        coolant = pid(io, "05")?.firstOrNull()?.minus(40)
                        throttle = pid(io, "11")?.firstOrNull()?.let { it * 100 / 255 }
                    }
                    if (tick % 10 == 0) {
                        volt = io.send("ATRV").uppercase().replace("V", "").trim().toFloatOrNull()
                    }
                    AppState.obd.update { o ->
                        o.copy(
                            rpm = rpm ?: o.rpm,
                            speedKmh = speed ?: o.speedKmh,
                            coolantC = coolant ?: o.coolantC,
                            throttlePct = throttle ?: o.throttlePct,
                            batteryV = volt ?: o.batteryV,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                    tick++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                closeConn()
                AppState.obd.update {
                    it.copy(status = ObdStatus.ERROR, error = e.message ?: "เชื่อมต่อไม่สำเร็จ", busy = false)
                }
                delay(3000) // ลองใหม่อัตโนมัติ
            }
        }
    }

    // ---------------- วิธีเชื่อมต่อ ----------------

    @SuppressLint("MissingPermission")
    private fun openBluetooth(mac: String): Io {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            throw IOException("ยังไม่ได้อนุญาต Bluetooth")
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: throw IOException("เครื่องนี้ไม่มี Bluetooth")
        if (!adapter.isEnabled) throw IOException("Bluetooth ปิดอยู่")
        adapter.cancelDiscovery()
        val s = adapter.getRemoteDevice(mac).createRfcommSocketToServiceRecord(SPP_UUID)
        conn = s
        s.connect()
        return StreamIo(s.inputStream, s.outputStream)
    }

    /** ELM327 แบบสาย USB (ชิป FTDI / CH340 / PL2303 / CP210x) ผ่าน USB-OTG */
    private fun openUsb(): Io {
        val usb = context.getSystemService(UsbManager::class.java) ?: throw IOException("เครื่องนี้ไม่รองรับ USB OTG")
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usb).firstOrNull()
            ?: throw IOException("ไม่พบสาย ELM327 USB (เสียบสาย OTG แล้วหรือยัง)")
        if (!usb.hasPermission(driver.device)) {
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                PendingIntent.FLAG_MUTABLE,
            )
            usb.requestPermission(driver.device, pi)
            throw IOException("กด \"อนุญาต\" ที่หน้าต่าง USB บนจอ")
        }
        val connection = usb.openDevice(driver.device) ?: throw IOException("เปิดสาย USB ไม่ได้")
        val port = driver.ports.first()
        port.open(connection)
        conn = Closeable { runCatching { port.close() } }
        // ELM327 แบบสายส่วนใหญ่ใช้ 38400 บางรุ่น 115200
        port.setParameters(38400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        return UsbIo(port)
    }

    /** ELM327 แบบ WiFi (ปกติ 192.168.0.10:35000) — มือถือต้องต่อ WiFi ของ ELM327 ก่อน */
    private fun openWifi(host: String, port: Int): Io {
        val s = Socket()
        conn = s
        s.connect(InetSocketAddress(host, port), 5000)
        s.soTimeout = 2000
        return StreamIo(s.getInputStream(), s.getOutputStream())
    }

    // ---------------- คำสั่ง ----------------

    private suspend fun handle(io: Io, r: Request) {
        AppState.obd.update { it.copy(busy = true) }
        if (r == Request.CLEAR_DTC) io.send("04", 4000)
        val dtcs = parseDtc(io.send("03", 4000))
        AppState.obd.update { it.copy(dtcs = dtcs, dtcChecked = true, busy = false) }
    }

    /** ส่ง Mode 01 PID แล้วคืนค่า data bytes (A, B, ...) */
    private suspend fun pid(io: Io, pid: String): IntArray? {
        val key = "41$pid"
        val line = io.send("01$pid").uppercase()
            .split('\r', '\n').map { it.replace(" ", "") }
            .firstOrNull { it.contains(key) } ?: return null
        val hex = line.substring(line.indexOf(key) + key.length)
        return hex.chunked(2).mapNotNull { if (it.length == 2) it.toIntOrNull(16) else null }
            .toIntArray().takeIf { it.isNotEmpty() }
    }

    /** ช่องทางรับ-ส่งข้อมูล (Bluetooth / WiFi / USB ใช้รูปแบบเดียวกัน) */
    private abstract class Io {
        abstract fun write(b: ByteArray)
        /** อ่านข้อมูลที่มี (คืนค่าว่างถ้ายังไม่มี) */
        abstract fun readAvailable(): ByteArray

        suspend fun send(cmd: String, timeoutMs: Long = 1500): String {
            readAvailable() // ทิ้งข้อมูลค้าง
            write("$cmd\r".toByteArray())
            val sb = StringBuilder()
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (SystemClock.elapsedRealtime() < deadline) {
                val chunk = readAvailable()
                if (chunk.isEmpty()) { delay(5); continue }
                val text = String(chunk, Charsets.US_ASCII)
                val end = text.indexOf('>')
                if (end >= 0) { sb.append(text, 0, end); break }
                sb.append(text)
            }
            return sb.toString().replace("SEARCHING...", "").trim()
        }
    }

    private class StreamIo(private val input: InputStream, private val output: OutputStream) : Io() {
        override fun write(b: ByteArray) { output.write(b); output.flush() }
        override fun readAvailable(): ByteArray {
            val n = input.available()
            if (n <= 0) return ByteArray(0)
            val buf = ByteArray(n)
            val r = input.read(buf)
            if (r < 0) throw IOException("ELM327 ตัดการเชื่อมต่อ")
            return buf.copyOf(r)
        }
    }

    private class UsbIo(private val port: UsbSerialPort) : Io() {
        private val buf = ByteArray(256)
        override fun write(b: ByteArray) = port.write(b, 1000)
        override fun readAvailable(): ByteArray {
            val n = port.read(buf, 20)
            return if (n > 0) buf.copyOf(n) else ByteArray(0)
        }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val ACTION_USB_PERMISSION = "com.motodash.tracer.USB_PERMISSION"

        /** แปลงคำตอบ Mode 03 เป็นรหัส เช่น P0133 */
        fun parseDtc(resp: String): List<String> {
            val out = mutableListOf<String>()
            resp.uppercase().split('\r', '\n')
                .map { it.replace(" ", "") }
                .filter { it.startsWith("43") }
                .forEach { line ->
                    var data = line.substring(2)
                    if (data.length % 4 == 2) data = data.substring(2) // CAN: ไบต์แรกคือจำนวนโค้ด
                    data.chunked(4).filter { it.length == 4 && it != "0000" }.forEach { c ->
                        val b0 = c.substring(0, 2).toIntOrNull(16) ?: return@forEach
                        val letter = "PCBU"[b0 shr 6]
                        val d1 = (b0 shr 4) and 0x3
                        out += "$letter$d1${c.substring(1)}"
                    }
                }
            return out.distinct()
        }
    }
}
