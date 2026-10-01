package com.example.pp68_salestrackingapp.utils

/**
 * ทำให้ "ชื่อที่แสดง" ในดรอปดาวน์ไม่ซ้ำกัน โดยต่อท้ายด้วยรหัสเฉพาะตัวที่ชื่อซ้ำ
 *
 * เรื่อง "กดแล้วได้ id ถูกตัว" SearchableIdDropdownField จัดการให้แล้ว (มันคืน id ของแถวที่กด
 * จริง ไม่ได้คืนชื่อแล้วให้ผู้เรียกค้นกลับ) ตัวนี้แก้คนละปัญหา: ถ้าสองแถวชื่อเหมือนกันเป๊ะ
 * ผู้ใช้มองไม่ออกว่าต้องกดอันไหน
 *
 * เคสจริง: customer กับ lead_customer ถูกรวมเป็น v_unified_customer ลูกค้ารายเดียวกันจึงมีได้
 * ทั้งแถว lead (LD-xxx) และแถวลูกค้าจริง ชื่อเดียวกัน
 */
fun List<Pair<String, String>>.withUniqueLabels(): List<Pair<String, String>> {
    val duplicated = groupingBy { it.second.trim() }.eachCount()
        .filterValues { it > 1 }.keys
    if (duplicated.isEmpty()) return this
    return map { (id, name) ->
        if (name.trim() in duplicated) id to "$name ($id)" else id to name
    }
}
