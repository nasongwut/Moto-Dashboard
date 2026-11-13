package com.motodash.tracer.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.motodash.tracer.Prefs
import com.motodash.tracer.obd.ElmLink

private data class BtDevice(val name: String, val mac: String)

@SuppressLint("MissingPermission")
@Composable
fun SettingsDialog(prefs: Prefs, onDismiss: (saved: Boolean) -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val devices = remember(refresh) {
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!ok) emptyList() else
            ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
                .map { BtDevice(it.name ?: it.address, it.address) }.sortedBy { it.name }
    }
    val usbName = remember(refresh) {
        val usb = ctx.getSystemService(UsbManager::class.java)
        usb?.let { UsbSerialProber.getDefaultProber().findAllDrivers(it).firstOrNull() }
            ?.let { d -> d.device.productName ?: d.javaClass.simpleName.removeSuffix("SerialDriver") }
    }
    var link by remember { mutableStateOf(prefs.elmLink) }
    var mac by remember { mutableStateOf(prefs.elmMac) }
    var host by remember { mutableStateOf(prefs.elmHost) }
    var port by remember { mutableStateOf(prefs.elmPort.toString()) }
    var dual by remember { mutableStateOf(prefs.dualCamera) }
    var keepOn by remember { mutableStateOf(prefs.keepScreenOn) }
    var bright by remember { mutableStateOf(prefs.maxBrightness) }
    var quota by remember { mutableFloatStateOf(prefs.quotaGb.toFloat()) }

    fun open(intent: Intent) {
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    AlertDialog(
        onDismissRequest = { onDismiss(false) },
        title = { Text("ตั้งค่า") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // ---------- การเชื่อมต่อของเครื่อง ----------
                Text("การเชื่อมต่อ", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // แผงตั้งค่า WiFi ลอยบนแอป (ไม่ออกจากแอป)
                    DashButton("ตั้งค่า WiFi", { open(Intent(Settings.Panel.ACTION_WIFI)) }, icon = Icons.Filled.Wifi, modifier = Modifier.weight(1f))
                    DashButton("ตั้งค่า Bluetooth", { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }, icon = Icons.Filled.Bluetooth, modifier = Modifier.weight(1f))
                }

                HorizontalDivider()

                // ---------- ELM327 ----------
                Text("ELM327 ต่อกับมือถือแบบไหน", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashButton("สาย USB", { link = ElmLink.USB; refresh++ }, icon = Icons.Filled.Usb, selected = link == ElmLink.USB, modifier = Modifier.weight(1f))
                    DashButton("Bluetooth", { link = ElmLink.BLUETOOTH; refresh++ }, icon = Icons.Filled.Bluetooth, selected = link == ElmLink.BLUETOOTH, modifier = Modifier.weight(1f))
                    DashButton("WiFi", { link = ElmLink.WIFI }, icon = Icons.Filled.Wifi, selected = link == ElmLink.WIFI, modifier = Modifier.weight(1f))
                }
                when (link) {
                    ElmLink.USB -> {
                        Text(
                            if (usbName != null) "พบสาย: $usbName ✓" else "ยังไม่พบสาย — เสียบ ELM327 USB ผ่านหัวแปลง OTG แล้วกด \"ค้นหาใหม่\"",
                            fontSize = 14.sp, color = if (usbName != null) C.Info else C.Muted,
                        )
                        Text(
                            "มือถือมีช่อง USB-C ช่องเดียว ถ้าต้องชาร์จไปด้วย ใช้หัวแปลง OTG ที่มีช่องชาร์จ (USB-C hub แบบ PD)",
                            fontSize = 13.sp, color = C.Dim,
                        )
                        TextButton(onClick = { refresh++ }) { Text("ค้นหาใหม่") }
                    }
                    ElmLink.BLUETOOTH -> {
                        Text("จับคู่ ELM327 ในตั้งค่า Bluetooth ก่อน แล้วเลือกจากรายการ", fontSize = 13.sp, color = C.Dim)
                        if (devices.isEmpty()) Text("ไม่พบอุปกรณ์ที่จับคู่ไว้", color = C.Dim)
                        Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                            devices.forEach { d ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = mac == d.mac, onClick = { mac = d.mac })
                                    Text("${d.name}  (${d.mac})", fontSize = 14.sp)
                                }
                            }
                        }
                        TextButton(onClick = { refresh++ }) { Text("โหลดรายการใหม่") }
                    }
                    ElmLink.WIFI -> {
                        Text("ต่อ WiFi ของ ELM327 ก่อน (ปุ่ม \"ตั้งค่า WiFi\" ด้านบน)", fontSize = 13.sp, color = C.Dim)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(host, { host = it }, label = { Text("IP") }, singleLine = true, modifier = Modifier.weight(2f))
                            OutlinedTextField(
                                port, { port = it.filter(Char::isDigit) }, label = { Text("พอร์ต") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                HorizontalDivider()

                // ---------- หน้าจอ ----------
                Text("หน้าจอ", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("จอติดตลอด ไม่หรี่ ไม่ดับ", modifier = Modifier.weight(1f))
                    Switch(checked = keepOn, onCheckedChange = { keepOn = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ความสว่างสูงสุด (อ่านกลางแดด)", modifier = Modifier.weight(1f))
                    Switch(checked = bright, onCheckedChange = { bright = it })
                }

                HorizontalDivider()

                // ---------- กล้อง ----------
                Text("กล้อง", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("เปิด 2 กล้องพร้อมกัน (หลัง + เซลฟี่)", modifier = Modifier.weight(1f))
                    Switch(checked = dual, onCheckedChange = { dual = it })
                }
                Text("พื้นที่เก็บคลิปสูงสุด: ${quota.toInt()} GB")
                Slider(value = quota, onValueChange = { quota = it }, valueRange = 10f..100f, steps = 17)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.elmLink = link
                prefs.elmMac = mac
                prefs.elmName = devices.firstOrNull { it.mac == mac }?.name
                prefs.elmHost = host
                prefs.elmPort = port.toIntOrNull() ?: 35000
                prefs.quotaGb = quota.toInt()
                prefs.dualCamera = dual
                prefs.keepScreenOn = keepOn
                prefs.maxBrightness = bright
                onDismiss(true)
            }) { Text("บันทึก") }
        },
        dismissButton = { TextButton(onClick = { onDismiss(false) }) { Text("ยกเลิก") } },
    )
}
