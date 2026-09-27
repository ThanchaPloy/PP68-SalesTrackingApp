package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

/**
 * ผลการค้นหาสถานที่ที่ backend proxy ส่งกลับมา (ไม่ใช่ payload ดิบของ Geoapify)
 *
 * ไม่มี place id เพราะไม่มีหน้าจอไหนใช้ — ข้อมูลจาก autocomplete พอสำหรับปักหมุดและบันทึกแล้ว
 * จึงไม่ต้องเรียก Place Details ซึ่งกิน credit เพิ่ม
 */
data class PlaceSuggestion(
    @SerializedName("name")              val name: String,
    @SerializedName("formatted_address") val formattedAddress: String,
    @SerializedName("area")              val area: String? = null,
    @SerializedName("latitude")          val latitude: Double,
    @SerializedName("longitude")         val longitude: Double
)
