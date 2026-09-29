package com.example.pp68_salestrackingapp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ใช้ตัดสินว่า "ส่งขึ้น server ไม่สำเร็จ" ครั้งนี้เป็นความผิดของเน็ตหรือของเซิร์ฟเวอร์
 *
 * เดิมทุก repository ถือว่าส่งไม่ขึ้น = ออฟไลน์เสมอ แล้วคืน success พร้อมโยนให้ outbox ตามส่ง
 * ผลคือเคสที่ "เน็ตดีแต่เซิร์ฟเวอร์ปฏิเสธ" ถูกกลบเป็น "บันทึกสำเร็จ" ทั้งที่ข้อมูลไม่มีวันขึ้นไป
 * ผู้ใช้จึงไม่มีทางรู้ตอนกดเซฟเลยว่าของขึ้นจริงหรือไม่ จนไปโผล่ตอน logout ไม่ได้
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * `NET_CAPABILITY_VALIDATED` คือหัวใจของตัวนี้ — มันแปลว่า Android ยิงทดสอบออกเน็ตได้จริงแล้ว
     * ไม่ใช่แค่ "ต่อ Wi-Fi อยู่" การเช็คแค่ว่ามี active network จะนับ Wi-Fi ที่ติด captive portal
     * หรือเน็ตบ้านที่หลุดว่าออนไลน์ ซึ่งจะทำให้เรากล่าวหาเซิร์ฟเวอร์ผิดตัว
     */
    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

}

/**
 * เรียกตรงจุดที่ "เขียนลงเครื่องแล้ว แต่ส่งขึ้น server ไม่สำเร็จ"
 *
 * - ไม่มีเน็ต → success: ของอยู่ในเครื่องและ is_synced = false แล้ว outbox จะตามส่งให้เอง
 * - มีเน็ต → failure: เซิร์ฟเวอร์ปฏิเสธหรือพัง ต้องบอกผู้ใช้ตรง ๆ ไม่ใช่เงียบไว้
 *
 * เป็น extension ไม่ใช่ member ของ NetworkMonitor โดยตั้งใจ — extension ผูกแบบ static จึงถูก
 * mockk ทับไม่ได้ เทสต์ที่ mock NetworkMonitor แบบ relaxed จะได้ logic ตัวจริงเสมอ (โดยมี
 * isOnline() คืน false = ออฟไลน์) ถ้าเป็น member ตัวนี้จะถูก stub ทิ้งแล้วเทสต์จะไม่ได้ทดสอบอะไรเลย
 *
 * ข้อความไม่มีชื่อการกระทำนำหน้า (ไม่มี "เช็คอินไม่สำเร็จ:") โดยตั้งใจ เพราะ ViewModel หลายตัว
 * เติมคำนำหน้าของตัวเองอยู่แล้ว เช่น ActivityDetailViewModel.confirmCheckin ถ้าใส่มาที่นี่ด้วย
 * ผู้ใช้จะเห็น "เช็คอินไม่สำเร็จ: เช็คอินไม่สำเร็จ: ..." และต้องบอกด้วยว่าข้อมูล "ไม่ได้หาย"
 * ไม่งั้นผู้ใช้จะกรอกใหม่ทั้งชุดทั้งที่ของอยู่ในเครื่องและ outbox กำลังตามส่งให้อยู่
 */
fun <T> NetworkMonitor.queuedOrFailed(value: T, detail: String? = null): kotlin.Result<T> =
    if (isOnline()) {
        kotlin.Result.failure(
            Exception(
                "ยังส่งขึ้นเซิร์ฟเวอร์ไม่ได้ (${detail ?: "เซิร์ฟเวอร์ไม่ตอบรับ"}) " +
                    "ข้อมูลถูกเก็บไว้ในเครื่องแล้ว ระบบจะส่งให้เองเมื่อเชื่อมต่อได้ " +
                    "ถ้าขึ้นข้อความนี้ซ้ำ ๆ แจ้งผู้ดูแลระบบ"
            )
        )
    } else {
        kotlin.Result.success(value)
    }
