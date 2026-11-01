package com.example.pp68_salestrackingapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogRepositoryTest {

    @Test
    fun `a call to a number not matching any contact is dropped entirely`() {
        val rows = listOf(RawCallLogRow(number = "0899999999", dateMs = 1_000L, duration = 30))
        val entries = CallLogRepository.buildEntries(rows, contactPhoneMap = mapOf("0812345678" to "C1"))
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `a call to a known contact's number is kept and normalized`() {
        val rows = listOf(RawCallLogRow(number = "081-234-5678", dateMs = 1_700_000_000_000L, duration = 120))
        val entries = CallLogRepository.buildEntries(rows, contactPhoneMap = mapOf("0812345678" to "C1"))

        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals("C1", entry.custId)
        assertEquals("0812345678", entry.phoneNumber) // ไม่มีขีด/เว้นวรรคแล้ว
        assertEquals(120, entry.duration)
    }

    @Test
    fun `matching normalizes both sides so a dashed contact number still matches a plain device number`() {
        val rows = listOf(RawCallLogRow(number = "0812345678", dateMs = 1_000L, duration = 10))
        val entries = CallLogRepository.buildEntries(rows, contactPhoneMap = mapOf("081-234-5678" to "C1"))
        assertEquals(1, entries.size)
    }

    @Test
    fun `log_id is deterministic for the same number and start time, so re-reading the device log never duplicates`() {
        val rows = listOf(RawCallLogRow(number = "0812345678", dateMs = 1_700_000_000_000L, duration = 60))
        val map = mapOf("0812345678" to "C1")

        val first  = CallLogRepository.buildEntries(rows, map).first().logId
        val second = CallLogRepository.buildEntries(rows, map).first().logId

        assertEquals(first, second)
    }

    @Test
    fun `different calls get different log_ids`() {
        val rows = listOf(
            RawCallLogRow(number = "0812345678", dateMs = 1_700_000_000_000L, duration = 60),
            RawCallLogRow(number = "0812345678", dateMs = 1_700_000_100_000L, duration = 10)
        )
        val entries = CallLogRepository.buildEntries(rows, mapOf("0812345678" to "C1"))

        assertEquals(2, entries.size)
        assertTrue(entries[0].logId != entries[1].logId)
    }
}
