package com.example.pp68_salestrackingapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.pp68_salestrackingapp.data.model.StagedAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

object AttachmentFileStore {
    private const val MAX_INPUT_BYTES = 15L * 1024L * 1024L
    private const val MAX_OUTPUT_BYTES = 500 * 1024
    private const val ENCODE_TARGET_BYTES = MAX_OUTPUT_BYTES - 16 * 1024
    private const val MAX_DIMENSION = 1600
    private const val DIRECTORY = "pending_attachments"

    suspend fun stage(context: Context, source: Uri): StagedAttachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val operationId = UUID.randomUUID().toString()
        val dir = File(context.filesDir, DIRECTORY).apply {
            check(exists() || mkdirs()) { "ไม่สามารถสร้างพื้นที่เก็บรูปที่รอซิงค์ได้" }
        }
        val sourceCopy = File(dir, "$operationId.source")
        val destination = File(dir, "$operationId.jpg")
        var total = 0L
        try {
            resolver.openInputStream(source)?.use { input ->
                sourceCopy.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_INPUT_BYTES) throw IllegalArgumentException("รูปมีขนาดเกิน 15 MB")
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IllegalArgumentException("ไม่สามารถอ่านไฟล์รูปภาพได้")
            check(total > 0L) { "ไฟล์รูปภาพว่างเปล่า" }

            val sourceExif = runCatching { ExifInterface(sourceCopy.absolutePath) }.getOrNull()
            val make = sourceExif?.getAttribute(ExifInterface.TAG_MAKE)
            val model = sourceExif?.getAttribute(ExifInterface.TAG_MODEL)
            check(!make.isNullOrBlank() || !model.isNullOrBlank()) {
                "รูปภาพต้องมีข้อมูลกล้อง กรุณาถ่ายรูปจากกล้องในแอป"
            }

            val bitmap = decodeSampled(sourceCopy)
            val oriented = orient(bitmap, sourceExif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL)
            val bounded = scaleToFit(oriented, MAX_DIMENSION)
            val encoded = encodeBoundedJpeg(bounded)
            destination.writeBytes(encoded)
            if (bounded !== oriented) bounded.recycle()
            if (oriented !== bitmap) oriented.recycle()
            bitmap.recycle()

            val outputExif = ExifInterface(destination.absolutePath)
            make?.let { outputExif.setAttribute(ExifInterface.TAG_MAKE, it) }
            model?.let { outputExif.setAttribute(ExifInterface.TAG_MODEL, it) }
            sourceExif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let {
                outputExif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, it)
            }
            outputExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            outputExif.saveAttributes()
            check(destination.length() <= MAX_OUTPUT_BYTES) { "ไม่สามารถลดขนาดรูปให้ไม่เกิน 500 KB ได้" }

            val digest = MessageDigest.getInstance("SHA-256")
            destination.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            StagedAttachment(
                operationId = operationId,
                localPath = destination.absolutePath,
                mimeType = "image/jpeg",
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                sizeBytes = destination.length()
            )
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            sourceCopy.delete()
        }
    }

    private fun decodeSampled(source: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "ไฟล์ที่เลือกไม่ใช่รูปภาพที่รองรับ" }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_DIMENSION * 2) {
            sample *= 2
        }
        return requireNotNull(
            BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        ) { "ไม่สามารถอ่านรูปภาพได้" }
    }

    private fun orient(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun scaleToFit(source: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(source.width, source.height)
        if (largest <= maxDimension) return source
        val ratio = maxDimension.toFloat() / largest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).roundToInt().coerceAtLeast(1),
            (source.height * ratio).roundToInt().coerceAtLeast(1),
            true
        )
    }

    private fun encodeBoundedJpeg(source: Bitmap): ByteArray {
        var bitmap = source
        var ownsBitmap = false
        try {
            repeat(5) {
                var quality = 85
                while (quality >= 55) {
                    val output = ByteArrayOutputStream()
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "บีบอัดรูปไม่สำเร็จ" }
                    val bytes = output.toByteArray()
                    if (bytes.size <= ENCODE_TARGET_BYTES) return bytes
                    quality -= 5
                }
                val next = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * 0.85f).roundToInt().coerceAtLeast(1),
                    (bitmap.height * 0.85f).roundToInt().coerceAtLeast(1),
                    true
                )
                if (ownsBitmap) bitmap.recycle()
                bitmap = next
                ownsBitmap = true
            }
            throw IllegalArgumentException("ไม่สามารถลดขนาดรูปให้ไม่เกิน 500 KB ได้")
        } finally {
            if (ownsBitmap) bitmap.recycle()
        }
    }

    suspend fun duplicate(source: StagedAttachment): StagedAttachment =
        withContext(Dispatchers.IO) {
            val sourceFile = File(source.localPath)
            check(sourceFile.isFile) { "ไม่พบไฟล์รูปที่รอซิงค์" }
            val operationId = UUID.randomUUID().toString()
            val extension = sourceFile.extension.ifBlank { "jpg" }
            val destination = File(sourceFile.parentFile, "$operationId.$extension")
            sourceFile.inputStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            source.copy(operationId = operationId, localPath = destination.absolutePath)
        }

    fun delete(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

    /** Removes only old, unreferenced staging files; a queued attachment is never retention-cleaned. */
    fun cleanupOrphans(context: Context, referencedPaths: Set<String>, nowMillis: Long = System.currentTimeMillis()) {
        val cutoff = nowMillis - 7L * 24L * 60L * 60L * 1000L
        File(context.filesDir, DIRECTORY).listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath !in referencedPaths && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }
}
