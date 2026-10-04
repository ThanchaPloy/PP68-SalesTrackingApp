package com.example.pp68_salestrackingapp.utils

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AttachmentFileStoreTest {
    @Test
    fun `cleanup never deletes referenced pending file and removes only old orphan`() {
        val root = Files.createTempDirectory("attachment-store-test").toFile()
        try {
            val directory = root.resolve("pending_attachments").apply { mkdirs() }
            val referenced = directory.resolve("referenced.jpg").apply { writeText("pending") }
            val oldOrphan = directory.resolve("old-orphan.jpg").apply { writeText("old") }
            val recentOrphan = directory.resolve("recent-orphan.jpg").apply { writeText("recent") }
            val now = System.currentTimeMillis()
            referenced.setLastModified(now - 30L * 24L * 60L * 60L * 1000L)
            oldOrphan.setLastModified(now - 8L * 24L * 60L * 60L * 1000L)
            recentOrphan.setLastModified(now - 24L * 60L * 60L * 1000L)
            val context = mockk<Context>()
            every { context.filesDir } returns root

            AttachmentFileStore.cleanupOrphans(context, setOf(referenced.absolutePath), now)

            assertTrue(referenced.exists())
            assertFalse(oldOrphan.exists())
            assertTrue(recentOrphan.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
