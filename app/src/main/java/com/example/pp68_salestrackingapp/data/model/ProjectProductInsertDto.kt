package com.example.pp68_salestrackingapp.data.model

import com.google.gson.annotations.SerializedName

data class ProjectProductInsertDto(
    @SerializedName("project_id")   val projectId:  String,
    @SerializedName("product_id")   val productId:  String,
    @SerializedName("quantity")     val quantity:   Double,
    // ✅ เปลี่ยนจาก wanted_date เป็น desired_date ให้ตรงกับฐานข้อมูลตามที่ API ระบุ
    @SerializedName("desired_date") val desiredDate: String?
)
