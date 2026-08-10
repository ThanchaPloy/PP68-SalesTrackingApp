package com.example.pp68_salestrackingapp.utils

fun formatPhotoUrl(rawUrl: String?): String? {
    if (rawUrl.isNullOrBlank()) return null
    val url = rawUrl.trim()
    return when {
        url.startsWith("http://") || url.startsWith("https://") || url.startsWith("content://") -> url
        url.startsWith("file://") -> url
        url.startsWith("/") && (url.contains("/storage/") || url.contains("/data/")) -> "file://$url"
        url.startsWith("/") -> "http://192.168.15.182:8080$url"
        else -> "http://192.168.15.182:8080/$url"
    }
}
