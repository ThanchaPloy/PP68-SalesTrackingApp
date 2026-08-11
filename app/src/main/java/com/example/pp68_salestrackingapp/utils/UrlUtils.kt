package com.example.pp68_salestrackingapp.utils

import com.example.pp68_salestrackingapp.BuildConfig

fun formatPhotoUrl(rawUrl: String): String {
    if (rawUrl.isBlank()) return rawUrl
    val url = rawUrl.trim()
    val uploadBase = BuildConfig.UPLOAD_URL.removeSuffix("/")
    // Server App (uploads folder) lives on 177 / uploadBase, while 182 is DB-only
    val defaultBase = if (uploadBase.isNotBlank() && !uploadBase.contains("localhost")) uploadBase else "http://192.168.15.177:8080"

    return when {
        url.startsWith("http://") || url.startsWith("https://") || url.startsWith("content://") -> url
        url.startsWith("file://") -> url
        url.startsWith("/") && (url.contains("/storage/") || url.contains("/data/")) -> "file://$url"
        url.startsWith("/") -> "$defaultBase$url"
        else -> "$defaultBase/$url"
    }
}
