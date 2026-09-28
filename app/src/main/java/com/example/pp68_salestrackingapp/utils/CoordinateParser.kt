package com.example.pp68_salestrackingapp.utils

/**
 * แปลงข้อความที่ผู้ใช้วางในช่องค้นหาให้เป็นพิกัด ถ้ามันเป็นพิกัดจริง
 *
 * มีไว้เพราะสถานที่ส่วนใหญ่ของลูกค้า (บริษัท SME) ไม่มีใน OpenStreetMap ซึ่งเป็นแหล่งข้อมูลของ
 * Geoapify — วิธีที่เซลส์ใช้จริงคือเปิด Google Maps หาที่ตั้งลูกค้าแล้วคัดลอกพิกัดมาวาง
 *
 * ทำที่เครื่องทั้งหมด ไม่ยิง API: ได้พิกัดตรงเป๊ะไม่โดน snap ไปจุดใกล้เคียง (Geoapify คลาดไป ~30 ม.),
 * ขึ้นทันทีไม่ต้องรอ debounce, ไม่กิน credit และใช้ได้แม้ไม่มีเน็ต
 */
object CoordinateParser {

    // รับรูปแบบที่ Google Maps คัดลอกมาจริง: "13.540690, 100.613096" / "13.54069,100.613096"
    // และแบบเว้นวรรคเฉย ๆ "13.5407 100.6131"
    private val PATTERN = Regex(
        """^\s*(-?\d{1,3}(?:\.\d+)?)\s*[,\s]\s*(-?\d{1,3}(?:\.\d+)?)\s*$"""
    )

    /**
     * คืนพิกัดถ้าข้อความเป็นพิกัดที่ใช้ได้จริง ไม่ใช่ก็คืน null แล้วปล่อยให้ค้นหาตามปกติ
     *
     * กรองค่าที่เป็นไปไม่ได้ออก (lat เกิน ±90, lon เกิน ±180) และกรณีที่ดูเหมือนพิกัดแต่ไม่ใช่
     * เช่น "128, 160" (บ้านเลขที่) จะไม่ผ่านเพราะ 128 อยู่นอกช่วง latitude
     */
    fun parse(text: String): Pair<Double, Double>? {
        // บาง IME/คีย์บอร์ดคัดลอกมาด้วย non-breaking space ( ) แทนช่องว่างปกติ — \s ใน regex
        // ปกติไม่จับตัวนี้ ทำให้ parse พังเงียบ ๆ แล้ว fallback ไปค้นหาข้อความแทนโดยไม่บอกผู้ใช้เลย
        val normalized = text.replace(' ', ' ')
        val match = PATTERN.find(normalized) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null

        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        // ต้องมีทศนิยมอย่างน้อยฝั่งหนึ่ง — เลขกลม ๆ อย่าง "10, 20" มักเป็นอย่างอื่นมากกว่าพิกัด
        if (!match.groupValues[1].contains('.') && !match.groupValues[2].contains('.')) return null
        // 0.0 ทั้งคู่หมายถึง "ยังไม่ตั้งพิกัด" ในโค้ดฝั่ง UI (MapPickerField) — ถ้าปล่อยให้พิกัดจริง
        // ที่บังเอิญมีแกนใดแกนหนึ่งเป็น 0.0 ผ่านมา จะโดน LaunchedEffect(lat, lng) เข้าใจผิดว่ายังไม่ตั้ง
        // แล้วล้างค่าที่เพิ่งเลือกทิ้งทันที (ไม่มีที่ไหนในไทยที่พิกัดจะเป็น 0.0 อยู่แล้วจริง ๆ)
        if (lat == 0.0 || lon == 0.0) return null

        return lat to lon
    }

    /** พิกัดอยู่ในกรอบประเทศไทยคร่าว ๆ หรือไม่ — ใช้เตือนผู้ใช้ ไม่ได้ใช้บล็อก */
    fun isWithinThailand(lat: Double, lon: Double): Boolean =
        lat in 5.0..21.0 && lon in 97.0..106.0
}
