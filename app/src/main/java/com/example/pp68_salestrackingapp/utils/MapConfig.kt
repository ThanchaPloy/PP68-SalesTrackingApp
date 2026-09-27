package com.example.pp68_salestrackingapp.utils

/**
 * ค่าคงที่ของแผนที่ทั้งแอป — style URL เขียนที่เดียวตรงนี้เท่านั้น ห้ามกระจายไปหน้าจออื่น
 *
 * OpenFreeMap ให้ vector tiles ฟรี ไม่ต้องใช้ API key และไม่จำกัดปริมาณแบบ tile server ของ OSM
 * (ต่างจาก tile.openstreetmap.org เดิมที่บล็อกแอปที่ส่ง traffic เยอะ)
 */
object MapConfig {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    /** กรุงเทพฯ — จุดเริ่มต้นเดิมของแอปตอนที่ยังไม่มีพิกัด */
    const val DEFAULT_LAT = 13.7563
    const val DEFAULT_LNG = 100.5018

    const val DEFAULT_ZOOM = 15.0

    /** ซูมตอนเลื่อนกล้องไปยังสถานที่ที่ผู้ใช้เลือกจากผลค้นหา */
    const val PLACE_SELECTED_ZOOM = 16.5
}
