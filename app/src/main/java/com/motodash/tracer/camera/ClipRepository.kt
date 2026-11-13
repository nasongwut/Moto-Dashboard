package com.motodash.tracer.camera

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * จัดการไฟล์คลิป: ตั้งชื่อ, ล็อก, วนทับเมื่อเต็ม
 * แต่ละช่วงเวลามี 2 ไฟล์: F_<เวลา>.mp4 (กล้องหน้า/ถนน) และ R_<เวลา>.mp4 (กล้องหลัง/เซลฟี่)
 */
object ClipRepository {
    private const val MIN_FREE_BYTES = 2L * 1024 * 1024 * 1024 // เหลือพื้นที่ว่างอย่างน้อย 2 GB
    private val nameFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /** คลิปหนึ่งช่วงเวลา (มีกล้องหน้า และ/หรือ กล้องหลัง) */
    data class Clip(val stamp: String, val front: File?, val rear: File?, val locked: Boolean) {
        val startedAt: Date? get() = runCatching { nameFmt.parse(stamp) }.getOrNull()
        val files: List<File> get() = listOfNotNull(front, rear)
        val sizeMb: Long get() = files.sumOf { it.length() } / (1024 * 1024)
    }

    private fun base(ctx: Context): File =
        ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: ctx.filesDir

    fun clipsDir(ctx: Context) = File(base(ctx), "clips").apply { mkdirs() }
    fun lockedDir(ctx: Context) = File(base(ctx), "locked").apply { mkdirs() }

    /** ไฟล์คู่ใหม่ (หน้า, หลัง) ที่ใช้เวลาเดียวกัน */
    fun newClipFiles(ctx: Context): Pair<File, File> {
        val stamp = nameFmt.format(Date())
        return File(clipsDir(ctx), "F_$stamp.mp4") to File(clipsDir(ctx), "R_$stamp.mp4")
    }

    private fun stampOf(f: File) = f.nameWithoutExtension.substringAfter('_')

    private fun videoFiles(dir: File): List<File> =
        dir.listFiles().orEmpty().filter { it.extension == "mp4" && it.length() > 0 }

    fun list(ctx: Context): List<Clip> {
        fun group(dir: File, locked: Boolean) = videoFiles(dir).groupBy { stampOf(it) }.map { (stamp, fs) ->
            Clip(
                stamp = stamp,
                front = fs.firstOrNull { it.name.startsWith("F_") },
                rear = fs.firstOrNull { it.name.startsWith("R_") },
                locked = locked,
            )
        }
        return (group(clipsDir(ctx), false) + group(lockedDir(ctx), true)).sortedByDescending { it.stamp }
    }

    fun usedBytes(ctx: Context): Long =
        (clipsDir(ctx).listFiles().orEmpty().toList() + lockedDir(ctx).listFiles().orEmpty().toList())
            .sumOf { it.length() }

    fun lockFile(ctx: Context, file: File): File {
        if (file.parentFile == lockedDir(ctx) || !file.exists()) return file
        val dest = File(lockedDir(ctx), file.name)
        return if (file.renameTo(dest)) dest else file
    }

    fun lock(ctx: Context, clip: Clip) {
        clip.files.forEach { lockFile(ctx, it) }
    }

    /** ลบคลิปเก่าสุด (ที่ไม่ได้ล็อก) ทีละช่วงเวลา จนกว่าจะอยู่ในโควตา */
    fun enforceQuota(ctx: Context, quotaBytes: Long, keep: Set<File>) {
        val dir = clipsDir(ctx)
        var used = usedBytes(ctx)
        val groups = dir.listFiles().orEmpty().groupBy { stampOf(it) }.toSortedMap()
        for ((_, files) in groups) {
            if (used <= quotaBytes && dir.usableSpace > MIN_FREE_BYTES) break
            if (files.any { it in keep }) continue
            files.forEach { used -= it.length(); it.delete() }
        }
    }
}
