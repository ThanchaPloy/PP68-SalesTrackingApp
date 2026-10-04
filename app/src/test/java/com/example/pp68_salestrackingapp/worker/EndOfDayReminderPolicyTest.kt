package com.example.pp68_salestrackingapp.worker

import com.example.pp68_salestrackingapp.utils.SyncStatusNavigation
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndOfDayReminderPolicyTest {
    private val bangkok = ZoneId.of("Asia/Bangkok")
    private fun at(hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 10, 4, hour, minute, 0, 0, bangkok)

    @Test fun `does not notify before ten pm Bangkok time`() {
        assertFalse(EndOfDayReminderPolicy.shouldNotify(at(21, 59), 1, null))
    }

    @Test fun `notifies at ten pm when work is pending`() {
        assertTrue(EndOfDayReminderPolicy.shouldNotify(at(22), 1, null))
    }

    @Test fun `does not notify twice on the same Bangkok date`() {
        assertFalse(EndOfDayReminderPolicy.shouldNotify(at(23), 2, "2026-10-04"))
    }

    @Test fun `does not notify when nothing is pending`() {
        assertFalse(EndOfDayReminderPolicy.shouldNotify(at(22), 0, null))
    }

    @Test fun `notification navigation opens sync status`() {
        assertEquals(SyncStatusNavigation.SCREEN_SYNC_STATUS, SyncStatusNavigation.destination(true))
        assertNull(SyncStatusNavigation.destination(false))
    }
}
