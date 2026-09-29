package com.example.pp68_salestrackingapp.utils

import com.example.pp68_salestrackingapp.BuildConfig

fun formatPhotoUrl(rawUrl: String): String {
    if (rawUrl.isBlank()) return rawUrl
    val url = rawUrl.trim()
    val uploadBase = BuildConfig.UPLOAD_URL.removeSuffix("/")
    // Server App (uploads folder) lives on 177 / uploadBase, while 182 is DB-only
    // ⚠️ ค่านี้เป็น IP วง LAN ของเครื่อง dev คนเดิม ใช้ได้เฉพาะตอน local.properties ชี้ไป localhost
    // (กรณี dev รันเซิร์ฟเวอร์ในเครื่องแล้วเทสบนมือถือจริง) — ไม่ใช่ fallback สำหรับ production
    // ถ้าเจอ path นี้บนเครื่อง dev อื่นที่ IP ไม่ตรง ต้องรู้ทันทีว่าทำไมรูปไม่ขึ้น ไม่ใช่เงียบไปเฉยๆ
    val defaultBase = if (uploadBase.isNotBlank() && !uploadBase.contains("localhost")) {
        uploadBase
    } else {
        android.util.Log.w("UrlUtils", "UPLOAD_URL ว่างหรือชี้ไป localhost — ใช้ fallback IP วง LAN ของ dev เดิม (192.168.15.177) ถ้าไม่ใช่เครื่องเดียวกัน รูปจะโหลดไม่ขึ้น ตรวจ local.properties")
        "http://192.168.15.177:8080"
    }

    return when {
        url.startsWith("http://") || url.startsWith("https://") || url.startsWith("content://") -> url
        url.startsWith("file://") -> url
        url.startsWith("/") && (url.contains("/storage/") || url.contains("/data/")) -> "file://$url"
        url.startsWith("/") -> "$defaultBase$url"
        else -> "$defaultBase/$url"
    }
}
