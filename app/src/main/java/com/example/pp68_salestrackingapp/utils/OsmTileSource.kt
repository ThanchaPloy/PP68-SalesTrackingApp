package com.example.pp68_salestrackingapp.utils

import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource

/**
 * เหมือน [org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK] ทุกอย่าง ยกเว้นไม่ตั้ง
 * FLAG_USER_AGENT_NORMALIZED
 *
 * MAPNIK ตั้ง flag นั้นไว้ ทำให้ TileDownloader ใช้ "normalized user agent" (= packageName/versionCode
 * → "com.example.pp68_salestrackingapp/3") แล้ว **ไม่เคยอ่าน Configuration.userAgentValue ที่เราตั้งเลย**
 * ซึ่ง OSM บล็อก User-Agent ที่ขึ้นต้นด้วย com.example. (package ตั้งต้นของ Android Studio) ตอบ 403
 * "App is not following the tile usage policy" กลับมาเป็นรูป tile ทุกใบ
 *
 * พอไม่ตั้ง flag นี้ TileDownloader จะ fallback มาใช้ Configuration.userAgentValue ที่ระบุชื่อแอปจริง
 * พร้อมอีเมลติดต่อ — ตรงตามที่ OSM tile usage policy กำหนดไว้
 *
 * ข้อจำกัดอื่นตาม policy คงไว้ครบ: สูงสุด 2 connection พร้อมกัน, ห้าม bulk download,
 * ห้ามโหลดดักล่วงหน้า และยังบังคับว่า User-Agent ต้องมีความหมาย (ไม่ใช่ค่า default)
 */
val OsmMapnikTileSource: XYTileSource = XYTileSource(
    "Mapnik",
    0, 19, 256, ".png",
    arrayOf("https://tile.openstreetmap.org/"),
    "© OpenStreetMap contributors",
    TileSourcePolicy(
        2,
        TileSourcePolicy.FLAG_NO_BULK
            or TileSourcePolicy.FLAG_NO_PREVENTIVE
            or TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
    )
)
