package com.motodash.tracer

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.motodash.tracer.camera.DashcamService
import com.motodash.tracer.map.Navigator
import com.motodash.tracer.media.NowPlaying
import com.motodash.tracer.ui.DashboardScreen
import com.motodash.tracer.ui.MotoTheme
import com.motodash.tracer.ui.SettingsDialog
import com.motodash.tracer.ui.Tab
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private var service by mutableStateOf<DashcamService?>(null)
    private var tab by mutableStateOf(Tab.DASHBOARD)
    private var showSettings by mutableStateOf(false)
    private var settingsVersion by mutableIntStateOf(0)

    /** YouTube ในแอป: สร้างครั้งเดียว เสียงไม่หยุดเมื่อสลับแท็บ */
    private val youtube: WebView by lazy { createYouTubeView() }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? DashcamService.LocalBinder)?.service()
            val f = com.motodash.tracer.ui.CameraUi.front
            val r = com.motodash.tracer.ui.CameraUi.rear
            if (f != null && r != null) service?.attachPreviews(f.surfaceProvider, r.surfaceProvider)
        }
        override fun onServiceDisconnected(name: ComponentName?) { service = null }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            startDashcam()
            startGps()
        }

    private val locationListener = LocationListener { loc ->
        // ไม่เอาค่าจากเครือข่ายมาทับ GPS ที่แม่นกว่า (ภายใน 10 วินาที)
        val prev = AppState.location.value
        if (loc.provider == LocationManager.NETWORK_PROVIDER && prev?.provider == LocationManager.GPS_PROVIDER &&
            loc.time - prev.time < 10_000
        ) return@LocationListener
        AppState.location.value = loc
        if (loc.provider == LocationManager.GPS_PROVIDER) {
            AppState.gpsSpeedKmh.value = if (loc.hasSpeed()) loc.speed * 3.6f else null
        }
        Navigator.onLocation(loc) // นำทางต่อแม้อยู่แท็บอื่น
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // แนวนอนเสมอ (กลับหัว 180° ได้ ตามทิศที่ติดมือถือ)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        prefs = Prefs(this)

        // ตั้งค่าแผนที่ OpenStreetMap
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        com.motodash.tracer.map.Voice.init(this)
        com.motodash.tracer.ui.CameraUi.create(this)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        hideSystemBars()

        // เป็นหน้าจอหลัก: ปุ่มย้อนกลับใช้ย้อนหน้าใน YouTube อย่างเดียว
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    com.motodash.tracer.ui.MediaUi.fullscreen != null -> com.motodash.tracer.ui.MediaUi.exitFullscreen?.invoke()
                    tab == Tab.MEDIA && youtube.canGoBack() -> youtube.goBack()
                }
            }
        })

        setContent {
            MotoTheme {
                val v = settingsVersion
                key(v) {
                    DashboardScreen(
                        service = service,
                        prefs = prefs,
                        youtube = youtube,
                        tab = tab,
                        onTab = { tab = it },
                        onOpenSettings = { showSettings = true },
                    )
                }
                if (showSettings) {
                    SettingsDialog(prefs) { saved ->
                        showSettings = false
                        if (saved) {
                            service?.reloadSettings()
                            settingsVersion = v + 1
                        }
                    }
                }
            }
        }

        // จอติดค้างเมื่อมีไฟเข้า · ไม่มีไฟให้จอดับตามเวลาปกติ
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppState.externalPower.collect { on ->
                    if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        requestPermissionsIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, DashcamService::class.java), connection, BIND_AUTO_CREATE)
        startGps()
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        NowPlaying.start(this)
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        service = null
        stopGps()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * ปุ่มจากรีโมต Bluetooth หรือ ESP32 (BLE คีย์บอร์ด) ที่อ่านปุ่มบนแฮนด์:
     * ◀ ▶ / Page Up-Down = เลื่อนแท็บ · F1–F4 = Dashboard / กล้อง / แผนที่ / สื่อ
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val tabs = Tab.entries
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_TAB -> {
                tab = tabs[(tab.ordinal + 1) % tabs.size]; true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP -> {
                tab = tabs[(tab.ordinal - 1 + tabs.size) % tabs.size]; true
            }
            KeyEvent.KEYCODE_F1 -> { tab = Tab.DASHBOARD; true }
            KeyEvent.KEYCODE_F2 -> { tab = Tab.CAMERA; true }
            KeyEvent.KEYCODE_F3 -> { tab = Tab.MAP; true }
            KeyEvent.KEYCODE_F4 -> { tab = Tab.MEDIA; true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createYouTubeView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        webChromeClient = object : WebChromeClient() {
            private var callback: WebChromeClient.CustomViewCallback? = null
            // ปุ่มเต็มจอใน YouTube
            override fun onShowCustomView(view: android.view.View, cb: WebChromeClient.CustomViewCallback) {
                callback = cb
                com.motodash.tracer.ui.MediaUi.exitFullscreen = { onHideCustomView() }
                com.motodash.tracer.ui.MediaUi.fullscreen = view
            }
            override fun onHideCustomView() {
                com.motodash.tracer.ui.MediaUi.fullscreen = null
                callback?.onCustomViewHidden()
                callback = null
            }
        }
        webViewClient = object : WebViewClient() {
            // ไม่ให้เด้งไปแอป YouTube: เปิดเฉพาะลิงก์เว็บในแอปนี้
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return true
                return scheme != "http" && scheme != "https"
            }
            // ซ่อนแถบหัว / ปุ่ม "เปิดแอป" ของ YouTube ให้เหลือแต่เนื้อหา
            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(com.motodash.tracer.ui.YT_CLEAN_JS, null)
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                view.evaluateJavascript(com.motodash.tracer.ui.YT_CLEAN_JS, null)
            }
        }
        setBackgroundColor(0xFF07090B.toInt())
        loadUrl(Uri.parse("https://m.youtube.com/").toString())
    }

    private fun requestPermissionsIfNeeded() {
        val needed = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS,
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) startDashcam() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startDashcam() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            ContextCompat.startForegroundService(this, Intent(this, DashcamService::class.java))
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = getSystemService(LocationManager::class.java) ?: return
        // ตำแหน่งล่าสุดที่เครื่องจำไว้ → แผนที่ขึ้นทันทีไม่ต้องรอ GPS
        if (AppState.location.value == null) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER)
                .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let { AppState.location.value = it }
        }
        // GPS (แม่นยำ) + เครือข่าย/WiFi (เร็ว ใช้ในอาคาร) พร้อมกัน
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)) {
                runCatching { lm.requestLocationUpdates(p, 1000L, 0f, locationListener, mainLooper) }
            }
        }
    }

    private fun stopGps() {
        getSystemService(LocationManager::class.java)?.removeUpdates(locationListener)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
