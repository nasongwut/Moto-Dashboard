package com.motodash.tracer.media

import android.service.notification.NotificationListenerService

/**
 * ไม่ได้อ่านการแจ้งเตือน มีไว้เพื่อให้ Android อนุญาตให้แอป
 * เห็นและควบคุมเพลงที่กำลังเล่นจาก YouTube Music / Spotify (MediaSession)
 */
class MediaListenerService : NotificationListenerService()
