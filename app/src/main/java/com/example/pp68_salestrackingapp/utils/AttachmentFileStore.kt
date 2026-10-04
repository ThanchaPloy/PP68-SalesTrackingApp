package com.example.pp68_salestrackingapp.utils

import android.content.Context
import android.net.Uri
import com.example.pp68_salestrackingapp.data.model.StagedAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object AttachmentFileStore {
    private const val MAX_BYTES = 15L * 1024L * 1024L
    private const val DIRECTORY = "pending_attachments"

    suspend fun stage(context: Context, source: Uri): StagedAttachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(source)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val extension = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val operationId = UUID.randomUUID().toString()
        val dir = File(context.filesDir, DIRECTORY).apply {
            check(exists() || mkdirs()) { "ไม่สามารถสร้างพื้นที่เก็บรูปที่รอซิงค์ได้" }
        }
        val destination = File(dir, "$operationId.$extension")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            resolver.openInputStream(source)?.use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) throw IllegalArgumentException("รูปมีขนาดเกิน 15 MB")
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IllegalArgumentException("ไม่สามารถอ่านไฟล์รูปภาพได้")
            check(total > 0L) { "ไฟล์รูปภาพว่างเปล่า" }
            StagedAttachment(
                operationId = operationId,
                localPath = destination.absolutePath,
                mimeType = mime,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                sizeBytes = total
            )
        } catch (error: Throwable) {
            destination.delete()
            throw error
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
