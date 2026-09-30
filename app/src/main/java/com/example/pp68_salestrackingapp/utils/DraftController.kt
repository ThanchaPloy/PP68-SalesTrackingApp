package com.example.pp68_salestrackingapp.utils

/**
 * ส่วนที่ ViewModel ฟอร์มทั้ง 5 ตัวเคยคัดลอกไว้เหมือนกัน — เก็บ baseline, เก็บฉบับร่างที่รอกู้คืน,
 * และต่อสายไปยัง DraftStore ด้วย key ของฟอร์มนั้น
 *
 * สิ่งที่ "ไม่ได้" อยู่ในนี้โดยตั้งใจ คือการแมปฟิลด์ (toDraft/restoreDraft) และ "จังหวะ" ที่จะจับ
 * baseline ซึ่งต่างกันจริงตามแต่ละฟอร์ม (เช่นหน้าสร้างนัดหมายต้องดูดพิกัดจาก GPS auto-fill เข้า
 * baseline ด้วย ไม่งั้นเปิดหน้าเฉย ๆ แล้วกดย้อนกลับจะโดนถามว่ามีข้อมูลยังไม่บันทึก) การพยายาม
 * ยัดสองอย่างนั้นเข้ามาที่นี่จะได้ตัวกลางที่ต้องรู้เรื่องของทุกฟอร์ม ซึ่งแย่กว่าการคัดลอกเดิม
 *
 * @param initialBaseline ค่าตั้งต้นก่อนที่ฟอร์มจะโหลดเสร็จ — ต้องเป็น "ฟอร์มเปล่า" ของชนิดนั้น
 *   ไม่ใช่ null เพราะฟอร์มสร้างใหม่บางหน้าไม่เคยเรียก captureBaseline เลย ถ้าเริ่มจาก null แล้ว
 *   ให้ isDirty คืน false ตลอด การกรอกแล้วกดย้อนกลับจะไม่เตือนอะไรเลย
 */
class DraftController<T : Any>(
    private val store: DraftStore,
    private val type: Class<T>,
    initialBaseline: T,
    private val keyOf: () -> String?,
    private val currentOf: () -> T
) {
    private var baseline: T = initialBaseline

    /** ฉบับร่างที่โหลดมารอให้ผู้ใช้ตัดสินใจว่าจะกู้คืนหรือไม่ */
    var pending: T? = null
        private set

    /** เรียกหลังฟอร์มโหลดค่าตั้งต้นเสร็จแล้ว — ของที่กรอกหลังจากนี้ถึงจะนับว่าผู้ใช้แก้ */
    fun captureBaseline() {
        baseline = currentOf()
    }

    /**
     * ดูดค่าที่ระบบเติมให้เองทีหลัง (ไม่ใช่ผู้ใช้กรอก) เข้า baseline
     * ใช้กับ async prefill ที่มาถึงหลัง captureBaseline เช่น GPS auto-fill
     */
    fun absorbIntoBaseline(transform: (T) -> T) {
        baseline = transform(baseline)
    }

    fun isDirty(): Boolean = currentOf() != baseline

    /** โหลดฉบับร่างที่ค้างไว้ คืน true ถ้ามีให้กู้คืน */
    fun check(): Boolean {
        pending = keyOf()?.let { store.load(it, type) }
        return pending != null
    }

    /** ดึงฉบับร่างที่รออยู่มาใช้แล้วล้างทิ้ง — กันกู้คืนซ้ำรอบสอง */
    fun takePending(): T? = pending.also { pending = null }

    fun save() {
        keyOf()?.let { store.save(it, currentOf()) }
    }

    fun discard() {
        keyOf()?.let { store.clear(it) }
    }

    /**
     * key ของฟอร์ม ณ ขณะนั้น — ฟอร์มที่ id เปลี่ยนระหว่างเซฟ (สร้างใหม่ได้ id จริงมากลางคัน)
     * ต้องจับ key ไว้ก่อนแล้วเอามาเคลียร์ทีหลัง ไม่งั้นจะไปเคลียร์ key ของ id ใหม่ที่ไม่เคยมีฉบับร่าง
     */
    fun currentKey(): String? = keyOf()

    fun discard(key: String) {
        store.clear(key)
    }
}
