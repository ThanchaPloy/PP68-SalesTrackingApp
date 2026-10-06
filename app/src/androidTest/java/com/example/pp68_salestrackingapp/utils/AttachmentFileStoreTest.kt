package com.example.pp68_salestrackingapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class AttachmentFileStoreTest {
    @Test
    fun stageCompressesToBoundPreservesCameraExifAndHashesFinalFile() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "large-camera-source.jpg")
        val bitmap = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height) {
            val value = Random.nextInt()
            0xff000000.toInt() or (value and 0x00ffffff)
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it) }
        bitmap.recycle()
        ExifInterface(source.absolutePath).apply {
            setAttribute(ExifInterface.TAG_MAKE, "Test Camera")
            setAttribute(ExifInterface.TAG_MODEL, "Model 1")
            saveAttributes()
        }

        val staged = AttachmentFileStore.stage(context, Uri.fromFile(source))
        val output = File(staged.localPath)
        try {
            assertTrue(staged.sizeBytes <= 500L * 1024L)
            assertEquals("image/jpeg", staged.mimeType)
            assertEquals("Test Camera", ExifInterface(output.absolutePath).getAttribute(ExifInterface.TAG_MAKE))
            val actualHash = MessageDigest.getInstance("SHA-256")
                .digest(output.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertEquals(actualHash, staged.sha256)
        } finally {
            source.delete()
            output.delete()
        }
    }
}
