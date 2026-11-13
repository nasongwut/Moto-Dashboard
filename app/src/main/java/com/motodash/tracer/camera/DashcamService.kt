package com.motodash.tracer.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.motodash.tracer.AppState
import com.motodash.tracer.MainActivity
import com.motodash.tracer.Prefs
import com.motodash.tracer.R
import com.motodash.tracer.obd.Elm327Client
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Collections

/**
 * บริการเบื้องหลัง (Foreground) ที่:
 *  - เปิดกล้องหลังของมือถือ (มองถนน = "กล้องหน้า") และกล้องเซลฟี่ (= "กล้องหลัง") พร้อมกัน
 *  - บันทึกวนทับ ตัดไฟล์ทุก 10 นาที (ได้ 2 ไฟล์ต่อช่วง)
 *  - ต่อ ELM327 อ่านค่า OBD
 *  - เริ่ม/หยุดบันทึกตามไฟเข้า (ACC)
 */
class DashcamService : LifecycleService() {

    inner class LocalBinder : Binder() {
        fun service(): DashcamService = this@DashcamService
    }

    /** ตัวบันทึกของกล้องหนึ่งตัว */
    private class Channel(val name: String, quality: Quality) {
        val preview: Preview = Preview.Builder().build()
        val capture: VideoCapture<Recorder> = VideoCapture.withOutput(
            Recorder.Builder()
                .setQualitySelector(QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build()
        )
        var recording: Recording? = null
        var file: File? = null
        var done: CompletableDeferred<Unit>? = null
    }

    private val binder = LocalBinder()
    private lateinit var prefs: Prefs
    lateinit var obd: Elm327Client
        private set

    private var front: Channel? = null // กล้องหลังของมือถือ → มองถนนด้านหน้า
    private var rear: Channel? = null  // กล้องเซลฟี่ → มองไปด้านหลัง/คนขี่
    private val pendingLock = Collections.synchronizedSet(mutableSetOf<File>())

    private var loopJob: Job? = null
    private var powerOffJob: Job? = null
    private var started = false

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_POWER_CONNECTED -> onPower(true)
                Intent.ACTION_POWER_DISCONNECTED -> onPower(false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        obd = Elm327Client(applicationContext)
        createChannels()
        ContextCompat.registerReceiver(
            this, powerReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        AppState.externalPower.value = getSystemService(BatteryManager::class.java).isCharging
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification("กำลังบันทึกกล้อง"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
        )
        if (!started) {
            started = true
            bindCameras()
            obd.start(prefs)
        }
        return START_STICKY
    }

    fun reloadSettings() {
        obd.start(prefs)
        // เปลี่ยนโหมดกล้อง → หยุดไฟล์ปัจจุบันแล้วเปิดกล้องใหม่
        if (prefs.dualCamera != (rear != null)) {
            lifecycleScope.launch {
                loopJob?.cancel(); loopJob = null
                stopSegment()
                front = null; rear = null
                bindCameras()
            }
        }
        updateRecState()
    }

    /** ต่อภาพสดเข้ากับ PreviewView (null = ไม่แสดง) */
    private var frontProvider: Preview.SurfaceProvider? = null
    private var rearProvider: Preview.SurfaceProvider? = null

    private var boundWithoutSurfaces = false

    /**
     * ต่อจอภาพ (PreviewView ที่อยู่ตลอดในแอป) ครั้งเดียว
     * ถ้ากล้องเปิดไปก่อนแล้วโดยยังไม่มีจอ → เปิดกล้องใหม่ให้มีจอตั้งแต่ต้น (กันภาพไม่ขึ้น)
     */
    fun attachPreviews(f: Preview.SurfaceProvider, r: Preview.SurfaceProvider) {
        if (frontProvider === f && rearProvider === r) return
        frontProvider = f; rearProvider = r
        if (boundWithoutSurfaces) {
            boundWithoutSurfaces = false
            lifecycleScope.launch {
                watchdog?.cancel()
                loopJob?.cancel(); loopJob = null
                stopSegment()
                front = null; rear = null
                bindCameras()
            }
        } else {
            front?.preview?.setSurfaceProvider(f)
            rear?.preview?.setSurfaceProvider(r)
        }
    }

    /** ล็อกคลิปที่กำลังบันทึก + คลิปก่อนหน้า (เหตุการณ์มักเกิดก่อนกดปุ่ม) */
    fun lockCurrent() {
        listOfNotNull(front?.file, rear?.file).forEach { pendingLock += it }
        val current = AppState.rec.value.currentStamp
        ClipRepository.list(this).firstOrNull { !it.locked && it.stamp != current }?.let {
            ClipRepository.lock(this, it)
        }
        AppState.rec.update { it.copy(message = "ล็อกคลิปแล้ว (คลิปนี้ + คลิปก่อนหน้า)") }
    }

    @SuppressLint("RestrictedApi")
    private fun bindCameras(forceSingle: Boolean = false) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            provider.unbindAll()
            watchdog?.cancel()

            // หาคู่กล้อง หลัง+หน้า ที่เปิดพร้อมกันได้
            val pair = provider.availableConcurrentCameraInfos.firstNotNullOfOrNull { infos ->
                val back = infos.firstOrNull { it.lensFacing == CameraSelector.LENS_FACING_BACK }
                val selfie = infos.firstOrNull { it.lensFacing == CameraSelector.LENS_FACING_FRONT }
                if (back != null && selfie != null) back to selfie else null
            }

            var dual = false
            var error: String? = null
            var cameras: List<Camera> = emptyList()
            if (pair != null && prefs.dualCamera && !forceSingle) {
                try {
                    // โหมด 2 กล้อง: แต่ละกล้องสูงสุด 720p (ข้อจำกัดของ Android)
                    val f = Channel("front", Quality.HD)
                    val r = Channel("rear", Quality.HD)
                    frontProvider?.let { f.preview.setSurfaceProvider(it) }
                    rearProvider?.let { r.preview.setSurfaceProvider(it) }
                    val cc = provider.bindToLifecycle(
                        listOf(
                            ConcurrentCamera.SingleCameraConfig(
                                pair.first.cameraSelector,
                                UseCaseGroup.Builder().addUseCase(f.preview).addUseCase(f.capture).build(),
                                this,
                            ),
                            ConcurrentCamera.SingleCameraConfig(
                                pair.second.cameraSelector,
                                UseCaseGroup.Builder().addUseCase(r.preview).addUseCase(r.capture).build(),
                                this,
                            ),
                        )
                    )
                    cameras = cc.cameras
                    front = f; rear = r; dual = true
                    Log.i(TAG, "dual camera bound")
                } catch (e: Exception) {
                    Log.e(TAG, "dual camera bind failed", e)
                    error = "เปิด 2 กล้องไม่สำเร็จ · ใช้กล้องเดียวแทน"
                    provider.unbindAll()
                }
            } else {
                Log.i(TAG, "concurrent pair=${pair != null} dualSetting=${prefs.dualCamera} forceSingle=$forceSingle")
            }
            if (!dual) {
                // ใช้กล้องหลังมือถือตัวเดียวที่ 1080p
                try {
                    val f = Channel("front", Quality.FHD)
                    frontProvider?.let { f.preview.setSurfaceProvider(it) }
                    cameras = listOf(provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, f.preview, f.capture))
                    front = f; rear = null
                    Log.i(TAG, "single camera bound")
                } catch (e: Exception) {
                    Log.e(TAG, "single camera bind failed", e)
                    error = "เปิดกล้องไม่สำเร็จ: ${e.message}"
                }
            }

            // ติดตามสถานะกล้อง: แสดงสาเหตุบนจอ + ถ้า 2 กล้องเปิดไม่ขึ้นใน 6 วินาที → สลับเป็นกล้องเดียวเอง
            val opened = mutableSetOf<Int>()
            cameras.forEachIndexed { i, cam ->
                cam.cameraInfo.cameraState.observe(this) { st ->
                    Log.i(TAG, "camera[$i] state=${st.type} error=${st.error?.code}")
                    if (st.type == CameraState.Type.OPEN) opened += i
                    st.error?.let { err ->
                        AppState.rec.update { it.copy(message = "กล้องผิดพลาด: ${cameraErrorText(err.code)}") }
                    }
                }
            }
            if (dual) {
                watchdog = lifecycleScope.launch {
                    delay(6000)
                    if (opened.size < 2) {
                        Log.w(TAG, "dual camera did not open (opened=$opened) → fallback to single")
                        loopJob?.cancel(); loopJob = null
                        stopSegment()
                        front = null; rear = null
                        AppState.rec.update { it.copy(message = "เครื่องนี้เปิด 2 กล้องพร้อมกันไม่ขึ้น · ใช้กล้องหน้าอย่างเดียว") }
                        bindCameras(forceSingle = true)
                    }
                }
            }

            val msg = error ?: when {
                dual -> null
                forceSingle -> "เครื่องนี้เปิด 2 กล้องพร้อมกันไม่ขึ้น · ใช้กล้องหน้าอย่างเดียว"
                pair == null -> "เครื่องนี้ไม่รองรับ 2 กล้องพร้อมกัน · บันทึกกล้องหน้าอย่างเดียว"
                !prefs.dualCamera -> "ปิดโหมด 2 กล้องในตั้งค่า · บันทึกกล้องหน้าอย่างเดียว"
                else -> null
            }
            if (frontProvider == null) boundWithoutSurfaces = true
            AppState.rec.update { it.copy(dual = dual, cameraReady = true, message = msg) }
            if (front != null) startLoop()
        }, ContextCompat.getMainExecutor(this))
    }

    private var watchdog: Job? = null

    private fun cameraErrorText(code: Int) = when (code) {
        CameraState.ERROR_CAMERA_IN_USE -> "มีแอปอื่นใช้กล้องอยู่"
        CameraState.ERROR_MAX_CAMERAS_IN_USE -> "เปิดกล้องพร้อมกันเกินที่เครื่องรองรับ"
        CameraState.ERROR_CAMERA_DISABLED -> "กล้องถูกปิดโดยระบบ/นโยบาย"
        CameraState.ERROR_CAMERA_FATAL_ERROR -> "กล้องขัดข้อง (ลองรีสตาร์ทเครื่อง)"
        CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "ปิดโหมดห้ามรบกวนก่อน"
        CameraState.ERROR_STREAM_CONFIG -> "ตั้งค่าภาพไม่รองรับ"
        CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> "กำลังลองเปิดใหม่"
        else -> "รหัส $code"
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = lifecycleScope.launch {
            while (isActive) {
                startSegment()
                delay(SEGMENT_MS)
                stopSegment()
            }
        }
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
        lifecycleScope.launch { withContext(NonCancellable) { stopSegment() } }
    }

    private fun startSegment() {
        val channels = listOfNotNull(front, rear)
        if (channels.isEmpty() || channels.any { it.recording != null }) return
        val (fFile, rFile) = ClipRepository.newClipFiles(this)
        front?.let { start(it, fFile) }
        rear?.let { start(it, rFile) }
        AppState.rec.update { it.copy(currentStamp = fFile.nameWithoutExtension.substringAfter('_')) }
        updateRecState()
    }

    private fun start(ch: Channel, file: File) {
        ch.file = file
        val done = CompletableDeferred<Unit>()
        ch.done = done
        ch.recording = ch.capture.output
            .prepareRecording(this, FileOutputOptions.Builder(file).build())
            .start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED) {
                        AppState.rec.update { it.copy(message = "บันทึก${if (ch.name == "front") "กล้องหน้า" else "กล้องหลัง"}ผิดพลาด (code ${event.error})") }
                    }
                    if (pendingLock.remove(file)) ClipRepository.lockFile(this, file)
                    done.complete(Unit)
                }
            }
    }

    private suspend fun stopSegment() {
        val channels = listOfNotNull(front, rear).filter { it.recording != null }
        if (channels.isEmpty()) return
        channels.forEach { it.recording?.stop(); it.recording = null }
        channels.forEach { ch -> withTimeoutOrNull(5000) { ch.done?.await() } }
        channels.forEach { it.file = null }
        ClipRepository.enforceQuota(this, prefs.quotaBytes, keep = emptySet())
        AppState.rec.update { it.copy(currentStamp = null) }
        updateRecState()
    }

    private fun onPower(connected: Boolean) {
        AppState.externalPower.value = connected
        if (connected) {
            powerOffJob?.cancel()
            if (AppState.rec.value.cameraReady) startLoop()
            wakeScreen()
        } else {
            // รอ 10 วินาที กันไฟกระตุกตอนสตาร์ทเครื่อง
            powerOffJob = lifecycleScope.launch {
                delay(POWER_OFF_GRACE_MS)
                stopLoop()
            }
        }
    }

    private fun wakeScreen() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isInteractive) return
        val pi = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, CH_WAKE)
            .setSmallIcon(R.drawable.ic_stat_motodash)
            .setContentTitle("MotoDash")
            .setContentText("ไฟเข้าแล้ว · เริ่มบันทึก")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setTimeoutAfter(5000)
            .build()
        getSystemService(NotificationManager::class.java).notify(WAKE_ID, n)
    }

    private fun updateRecState() {
        AppState.rec.update {
            it.copy(
                recording = front?.recording != null,
                usedBytes = ClipRepository.usedBytes(this),
                quotaBytes = prefs.quotaBytes,
            )
        }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REC, "บันทึกกล้อง", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_WAKE, "ปลุกหน้าจอ", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CH_REC)
            .setSmallIcon(R.drawable.ic_stat_motodash)
            .setContentTitle("MotoDash")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        unregisterReceiver(powerReceiver)
        obd.stop()
        listOfNotNull(front, rear).forEach { it.recording?.stop(); it.recording = null }
        super.onDestroy()
    }

    companion object {
        const val SEGMENT_MS = 10 * 60 * 1000L // ไฟล์ละ 10 นาที
        private const val TAG = "MotoDash"
        const val POWER_OFF_GRACE_MS = 10_000L
        private const val NOTIF_ID = 42
        private const val WAKE_ID = 43
        private const val CH_REC = "dashcam"
        private const val CH_WAKE = "wake"
    }
}
