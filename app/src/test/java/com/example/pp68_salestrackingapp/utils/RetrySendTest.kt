package com.example.pp68_salestrackingapp.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class RetrySendTest {

    private fun err(code: Int) =
        Response.error<String>(code, "boom".toResponseBody("text/plain".toMediaType()))

    // ── สาเหตุที่ทำฟีเจอร์นี้: สัญญาณกระตุกแวบเดียวแล้วครั้งที่สองผ่าน ────────────────────
    @Test
    fun `a transient failure is retried until it lands`() = runTest {
        var calls = 0
        val res = retrySend(idempotent = true) {
            calls++
            if (calls < 3) throw ConnectException("network unreachable") else Response.success("ok")
        }

        assertEquals(3, calls)
        assertTrue(res.isSuccessful)
    }

    @Test
    fun `a 500 is retried`() = runTest {
        var calls = 0
        val res = retrySend(idempotent = true) {
            calls++
            if (calls < 2) err(500) else Response.success("ok")
        }

        assertEquals(2, calls)
        assertTrue(res.isSuccessful)
    }

    // ── กันข้อมูลซ้ำ: ข้อที่ผิดพลาดแล้วเสียหายกว่าไม่ retry เลย ──────────────────────────
    // POST ที่ timeout อาจสร้างแถวบนเซิร์ฟเวอร์ไปแล้วจริง แค่คำตอบหายกลางทาง
    // ถ้ายิงซ้ำจะได้ลูกค้า/นัดหมายซ้ำสองแถว ซึ่งแก้ยากกว่าการให้ผู้ใช้กดเซฟใหม่
    @Test
    fun `a POST that timed out is never retried`() = runTest {
        var calls = 0
        var thrown: Exception? = null
        try {
            retrySend<String>(idempotent = false) {
                calls++
                throw SocketTimeoutException("timeout")
            }
        } catch (e: Exception) { thrown = e }

        assertEquals(1, calls)
        assertTrue(thrown is SocketTimeoutException)
    }

    // ต่อไม่ติดเลย = คำขอไม่เคยออกจากเครื่อง ยิงซ้ำ POST จึงปลอดภัย ไม่เกิดแถวซ้ำ
    @Test
    fun `a POST that never left the device is retried`() = runTest {
        var calls = 0
        val res = retrySend(idempotent = false) {
            calls++
            if (calls < 2) throw ConnectException("refused") else Response.success("ok")
        }

        assertEquals(2, calls)
        assertTrue(res.isSuccessful)
    }

    // PATCH ยิงซ้ำได้ผลเหมือนเดิม จึง retry ตอน timeout ได้
    @Test
    fun `a PATCH that timed out is retried`() = runTest {
        var calls = 0
        val res = retrySend(idempotent = true) {
            calls++
            if (calls < 2) throw SocketTimeoutException("timeout") else Response.success("ok")
        }

        assertEquals(2, calls)
        assertTrue(res.isSuccessful)
    }

    // ── 4xx: ยิงซ้ำก็ได้คำตอบเดิม และผู้เรียกต้องได้ response ไปจัดการเอง ───────────────
    // ถ้า retry ตรงนี้ จะหน่วงผู้ใช้เปล่า ๆ และ markBlocked/บันทึกการปฏิเสธถาวรจะช้าไปด้วย
    @Test
    fun `a 403 is returned immediately without retrying`() = runTest {
        var calls = 0
        val res = retrySend(idempotent = true) { calls++; err(403) }

        assertEquals(1, calls)
        assertEquals(403, res.code())
    }

    @Test
    fun `a 404 is returned immediately without retrying`() = runTest {
        var calls = 0
        val res = retrySend(idempotent = true) { calls++; err(404) }

        assertEquals(1, calls)
        assertEquals(404, res.code())
    }

    // บั๊กของโค้ดเอง (cast พลาด, JSON เพี้ยน) ยิงซ้ำกี่รอบก็พังเหมือนเดิม ต้องโผล่ทันที
    @Test
    fun `a bug in our own code is not retried`() = runTest {
        var calls = 0
        var thrown: Exception? = null
        try {
            retrySend<String>(idempotent = true) {
                calls++
                throw IllegalStateException("bad cast")
            }
        } catch (e: Exception) { thrown = e }

        assertEquals(1, calls)
        assertTrue(thrown is IllegalStateException)
    }

    // ยิงครบโดยไม่สำเร็จ ต้องคืน response ตัวสุดท้ายให้ผู้เรียก ไม่ใช่โยน exception
    // ไม่งั้น catch ของผู้เรียกจะกลืนเป็น "ออฟไลน์" แล้วผู้ใช้ไม่รู้ว่าเซิร์ฟเวอร์ตอบ 500
    @Test
    fun `exhausting all attempts returns the last server response`() = runTest {
        var calls = 0
        val res = retrySend(idempotent = true) { calls++; err(503) }

        assertEquals(3, calls)
        assertEquals(503, res.code())
    }
}
