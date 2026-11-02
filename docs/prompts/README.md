# ชุด Prompt สำหรับสร้าง PP68 Sales Tracking App ขึ้นใหม่จากศูนย์

สถานะเอกสาร: เริ่มจัดทำวันที่ 7 ตุลาคม 2026 โดย reverse engineer จาก source code จริงของ Android และ backend

## จุดประสงค์

โฟลเดอร์นี้ไม่ใช่เอกสารอธิบายระบบแบบย่อ แต่เป็นชุดคำสั่งภาษาไทยสำหรับมอบให้ AI coding agent หรือทีมพัฒนา เพื่อค่อย ๆ สร้างระบบที่มีพฤติกรรม สถาปัตยกรรม ข้อจำกัดด้านข้อมูล และมาตรฐานการทดสอบใกล้เคียงระบบปัจจุบัน

ชุด Prompt ถูกแบ่งเป็นงานย่อยเพื่อลดความเสี่ยงจากการสั่งสร้างทั้งระบบในครั้งเดียว แต่ละหมวดต้องผ่าน compile/test/acceptance gate ก่อนเริ่มหมวดถัดไป

## วิธีใช้งาน

1. ให้ AI อ่าน `00_MASTER_RULES_AND_CONTEXT_TH.md` ทั้งไฟล์ก่อนทุกหมวด
2. ส่ง Prompt ของหมวดที่ต้องการ โดยให้ AI ตรวจ repository และรายงาน baseline ก่อนแก้ไข
3. ห้ามข้ามคำถามที่ถูกระบุเป็น decision gate
4. หลังจบหมวด ให้เก็บผล compile, test, migration และ acceptance scenario เป็นหลักฐาน
5. ถ้าสร้างระบบใหม่จริง ให้เริ่มจากหมวด 01 แล้วทำตามลำดับ ห้ามเริ่มจากระบบ sync ขั้นสูงก่อนมี CRUD และ local database ที่ถูกต้อง

## ลำดับ Prompt ทั้งโครงการ

| ลำดับ | ไฟล์ | ขอบเขต | สถานะ |
|---|---|---|---|
| 00 | `00_MASTER_RULES_AND_CONTEXT_TH.md` | กติกากลาง สถาปัตยกรรม เป้าหมาย และข้อห้าม | จัดทำแล้ว |
| 01 | `01_FOUNDATION_ANDROID_BACKEND_TH.md` | ตั้งต้น Android, Ktor, PostgreSQL, DI, network, Room และ project skeleton | จัดทำแล้ว |
| 02 | `02_AUTH_SESSION_AND_ACCOUNT_SECURITY_TH.md` | Login, JWT, session, account switch, forced password/phone setup | จัดทำแล้ว |
| 03 | `03_CUSTOMER_AND_CONTACT_TH.md` | Lead customer, ERP customer lookup, contact visibility และ offline cache | จัดทำแล้ว |
| 04 | `04_PROJECT_PIPELINE_AND_FACTORS_TH.md` | โครงการ สถานะ pipeline ปัจจัย คะแนนโอกาส และ stage history | จัดทำแล้ว |
| 05 | `05_APPOINTMENT_CHECKIN_AND_REMINDERS_TH.md` | นัดหมาย กฎเวลาไทย check-in GPS alarm และ proximity | จัดทำแล้ว |
| 06 | `06_SALES_RESULT_PHOTO_AND_VERSIONING_TH.md` | บันทึกผล เวอร์ชัน รูป EXIF และ opportunity propagation | จัดทำแล้ว |
| 07 | `07_OFFLINE_FIRST_AND_SYNC_PHASE_1_TH.md` | outbox, WorkManager, retry, UI status, EOD notification และ diagnostics | จัดทำแล้ว |
| 08 | `08_STORAGE_MEDIA_AND_PERFORMANCE_PHASE_2_TH.md` | idempotency, durable attachment, Paging, index และ resource budget | จัดทำแล้ว |
| 09 | `09_DELTA_SYNC_CONFLICT_TEMP_ID_PHASE_3_TH.md` | snapshot/delta, cursor, tombstone, conflict และ TEMP ID graph | จัดทำแล้ว |
| 10 | `10_APPOINTMENT_POLICY_AND_MULTI_DRAFT_PHASE_4_TH.md` | policy ใหม่, offline edit proof และ Draft หลายรายการ | จัดทำแล้ว |
| 11 | `11_DASHBOARD_EXPORT_SETTINGS_AND_DIAGNOSTICS_TH.md` | dashboard รายงาน Excel/PDF ตั้งค่า และ export log JSON | จัดทำแล้ว |
| 12 | `12_TEST_DEPLOY_ROLLOUT_AND_OPERATIONS_TH.md` | test matrix, migration, staging, release, systemd, rollback และ monitoring | จัดทำแล้ว |
| 13 | `13_MASTER_REBUILD_PROMPT_TH.md` | Prompt รวมสำหรับสร้างระบบครบทั้งโครงการ โดยอ้างอิงหมวด 00–12 | จัดทำแล้ว |
| 14 | `14_MOBILE_PHASE2_AND_PROJECT_CODE_ROLE_PREFIX_TH.md` | Mobile phase 2 (6 รายการ) + รหัสโครงการตามบทบาท — reverse engineer จากงานที่ทำเสร็จแล้ว ไม่ใช่ Prompt ก่อนเริ่มงานแบบหมวดอื่น | จัดทำแล้ว |

## หลักการจัดลำดับ

- หมวด 01–06 สร้างความสามารถทางธุรกิจพื้นฐาน
- หมวด 07 แก้ความถูกต้องและประสบการณ์ Offline-first
- หมวด 08 ลดข้อมูลซ้ำ การใช้ RAM พื้นที่ และความเสี่ยงของรูปภาพ
- หมวด 09 ทำให้ข้อมูลจำนวนมากซิงค์แบบ incremental พร้อมรับการลบและ conflict
- หมวด 10 เพิ่มกฎธุรกิจล่าสุดและ Draft หลายรายการ
- หมวด 11–12 ปิดส่วนรายงาน การสนับสนุนผู้ใช้ และการนำขึ้นใช้งานจริง
- หมวด 13 จะเขียนหลังตรวจความสอดคล้องของ Prompt ย่อยทั้งหมดแล้วเท่านั้น

## ขอบเขตความถูกต้อง

แหล่งความจริงเรียงตามลำดับ:

1. source code และ migration ที่ถูกใช้งานจริง
2. automated tests และ exported Room schema
3. configuration และ wiring จริง เช่น `Routing.kt`, `AppModule.kt`, `DatabaseModule.kt`
4. เอกสารแผนล่าสุด
5. เอกสารบริบทหรือเอกสารเทคนิคเก่า

หากข้อมูลขัดกัน ให้บันทึกความขัดแย้งและยึด source/runtime wiring ไม่คัดลอกข้อมูลเก่าลง Prompt โดยไม่ตรวจสอบ

## สิ่งที่ Prompt ชุดนี้ไม่ทำ

- ไม่ฝัง password, token, signing password, `.env` หรือข้อมูลลูกค้าจริง
- ไม่ถือว่า compile ผ่านเท่ากับพร้อม production
- ไม่สั่งล้างฐานข้อมูลหรือใช้ destructive migration เพื่อให้ build ผ่าน
- ไม่สร้าง endpoint หรือ field จากการคาดเดาเมื่อ contract ยังไม่ชัด
- ไม่รับรองว่าจะกู้ prompt ดั้งเดิมแบบคำต่อคำ แต่สร้างข้อกำหนดที่สามารถอธิบายระบบปัจจุบันและใช้สร้างซ้ำได้
