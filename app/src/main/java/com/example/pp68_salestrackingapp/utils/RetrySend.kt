package com.example.pp68_salestrackingapp.utils

import android.util.Log
import kotlinx.coroutines.delay
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * ยิงซ้ำให้อัตโนมัติตอนผู้ใช้กดเซฟ — สาเหตุที่พบบ่อยที่สุดของ "ส่งไม่ขึ้น" คือสัญญาณกระตุกแวบเดียว
 * ซึ่งยิงซ้ำอีกครั้งก็ผ่าน เดิมแอปยิงครั้งเดียวแล้วเลิก ({@link com.example.pp68_salestrackingapp.di.NetworkModule}
 * ไม่ได้ตั้ง retry ระดับแอปไว้) ความล้มเหลวชั่วคราวจึงกลายเป็น error ที่ผู้ใช้ต้องเจอทุกครั้ง
 *
 * ใช้เฉพาะเส้นทางที่ "ผู้ใช้กดแล้วนั่งรอ" เท่านั้น ไม่ใช้ใน SyncManager.doSync() เพราะ outbox มี
 * backoff ของ WorkManager คุมอยู่แล้ว ใส่ซ้ำจะยิ่งทำให้รอบ sync ยาวขึ้นเปล่า ๆ
 */
private const val MAX_ATTEMPTS = 3

/** หน่วงครั้งแรกก่อนยิงซ้ำ แล้วคูณสองไปเรื่อย ๆ (400ms → 800ms) */
private const val FIRST_DELAY_MS = 400L

/**
 * เพดานเวลารวมของการยิงซ้ำทั้งหมด
 *
 * นี่คือกันชนที่สำคัญที่สุดในไฟล์นี้: OkHttp ตั้ง timeout ไว้ 60 วินาที ถ้าปล่อยให้ยิงซ้ำ 3 ครั้ง
 * โดยไม่มีเพดาน กรณี timeout จะทำให้ผู้ใช้จ้องวงกลมหมุนอยู่ "3 นาที" ก่อนได้คำตอบ
 * มีเพดานนี้แล้วผลที่ได้คือ พลาดแบบเร็ว (ต่อไม่ติด/DNS ล่ม) จะถูกยิงซ้ำ 2-3 ครั้งภายใน 6 วินาที
 * ส่วนพลาดแบบช้า (timeout) จะได้แค่ครั้งเดียวแล้วคืนผลทันที ซึ่งเป็นพฤติกรรมที่ต้องการพอดี
 */
private const val BUDGET_MS = 6_000L

/**
 * @param idempotent ยิงซ้ำแล้วผลเหมือนเดิมหรือไม่ — `true` สำหรับ PATCH/PUT, `false` สำหรับ POST
 *
 * ตัวนี้สำคัญเรื่องความถูกต้องของข้อมูล ไม่ใช่แค่ performance: POST ที่ timeout อาจถึงเซิร์ฟเวอร์
 * และสร้างแถวไปแล้วจริง แค่คำตอบหายกลางทาง ถ้ายิงซ้ำจะได้ลูกค้า/นัดหมายซ้ำสองแถว จึงยิงซ้ำ POST
 * เฉพาะตอนที่ "แน่ใจว่าคำขอไม่เคยออกจากเครื่อง" เท่านั้น
 */
suspend fun <T> retrySend(
    idempotent: Boolean,
    tag: String = "retrySend",
    block: suspend () -> Response<T>
): Response<T> {
    val deadline = System.currentTimeMillis() + BUDGET_MS
    var delayMs = FIRST_DELAY_MS
    var lastResponse: Response<T>? = null
    var lastError: Exception? = null

    for (attempt in 1..MAX_ATTEMPTS) {
        try {
            val response = block()
            // 2xx = จบ, 3xx/4xx = เซิร์ฟเวอร์ตัดสินแล้ว ยิงซ้ำก็ได้คำตอบเดิม ต้องคืนให้ผู้เรียกจัดการเอง
            // (เช่น 403 ต้องไป markBlocked, 404 ต้องบันทึกเป็นการปฏิเสธถาวร) เหลือแค่ 5xx ที่คุ้มยิงซ้ำ
            if (response.isSuccessful || response.code() < 500) return response
            lastResponse = response
            lastError = null
        } catch (e: Exception) {
            if (!isWorthRetrying(e, idempotent)) throw e
            lastError = e
            lastResponse = null
        }

        if (attempt == MAX_ATTEMPTS) break
        // ถ้าหน่วงแล้วจะเกินเพดาน ก็ไม่ต้องหน่วงให้ผู้ใช้รอเปล่า ๆ — คืนผลที่มีอยู่เลย
        if (System.currentTimeMillis() + delayMs >= deadline) break
        Log.w(tag, "ส่งไม่สำเร็จ (ครั้งที่ $attempt) จะลองใหม่ในอีก ${delayMs}ms — ${lastError?.message ?: "HTTP ${lastResponse?.code()}"}")
        delay(delayMs)
        delayMs *= 2
    }

    lastResponse?.let { return it }
    throw lastError ?: IOException("ส่งข้อมูลไม่สำเร็จ")
}

/**
 * ลำดับของ `is` สำคัญ — UnknownHostException/ConnectException/SocketTimeoutException ล้วนเป็น
 * ลูกของ IOException ถ้าเอา IOException ขึ้นก่อนจะกลืนทุกเคสแล้ว POST จะถูกยิงซ้ำตอน timeout ด้วย
 */
private fun isWorthRetrying(e: Exception, idempotent: Boolean): Boolean = when (e) {
    // ต่อไม่ติด / หา DNS ไม่เจอ = คำขอไม่เคยออกจากเครื่องแน่นอน ยิงซ้ำปลอดภัยแม้เป็น POST
    is UnknownHostException, is ConnectException -> true
    // timeout หรือ connection ถูกตัดกลางทาง = คำขออาจถึงเซิร์ฟเวอร์แล้ว ยิงซ้ำ POST เสี่ยงข้อมูลซ้ำ
    is SocketTimeoutException -> idempotent
    is IOException -> idempotent
    // JsonSyntaxException, ClassCastException ฯลฯ — เป็นบั๊กของโค้ด ยิงซ้ำกี่รอบก็พังเหมือนเดิม
    else -> false
}
