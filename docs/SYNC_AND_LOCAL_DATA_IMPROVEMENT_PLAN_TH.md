# แผนปรับปรุงการเปิดแอป การซิงค์ และการจัดเก็บข้อมูลในเครื่อง

สถานะเอกสาร: ระยะที่ 1 ผ่าน compile/unit tests และรอ device acceptance; ระยะที่ 2 ปิด checkpoint 2A (baseline + index migration), 2B (Paging/SQL-bounded UI), 2C (create idempotency) และ automated verification ของ 2D (durable attachment outbox) แล้ว โดย 2C/2D ยังไม่ deploy และยังต้องผ่าน staging/production acceptance ที่ระบุไว้ก่อนใช้งานจริง

## บันทึกปิดงานระยะที่ 1 — รายการ 1–4 (4 ตุลาคม 2026)

- Download Worker รวมผลของทุกส่วนจริง ไม่รายงานว่าสำเร็จเมื่อ repository คืน `Result.failure`; network/timeout/429/5xx จะ retry, 401 และ local fatal จะหยุด retry อัตโนมัติและแสดงว่าให้ตรวจสอบ
- Upload Worker นับ `attempted`, `succeeded`, `temporaryFailures`, `permanentFailures`, `rejectedPending`, `skipped` และ `stillPending` แยกกัน รวมถึงจำกัดสูงสุด 100 แถวต่อ entity ต่อรอบเพื่อไม่ให้ worker หนึ่งรอบยาวเกินไป
- ใช้ error taxonomy กลาง ได้แก่ network, timeout, rate limited, server, authentication, permanent, dependency และ local fatal พร้อมบันทึกเฉพาะ metadata ที่ผ่าน allowlist ลง diagnostics
- การส่ง relation ของโครงการ/ผู้ติดต่อและนัดหมาย/ผู้เข้าร่วมถือเป็นส่วนหนึ่งของความสำเร็จ หาก relation ล้มเหลว parent จะยังเป็น unsynced และ WorkManager จะ retry ตามประเภท error
- กฎแจ้งเตือนสิ้นวันทดสอบที่เวลา 22:00 ตาม `Asia/Bangkok`, ไม่แจ้งเมื่อไม่มีงาน และไม่แจ้งซ้ำในวันเดียวกัน; notification เปิดหน้าสถานะ sync ผ่าน navigation contract กลาง
- ผลตรวจล่าสุด: Android compile ผ่าน และ unit test suite ผ่าน 398 tests, failures 0, errors 0, skipped 4

ขอบเขต: Android `PP68SalesTrackingApp` และ backend `../backend`
จัดทำจากการตรวจโค้ดปัจจุบันและเอกสาร `PP68_SalesTracking_Technical_Thai.docx`

## 1. เป้าหมายและคำจำกัดความของความสำเร็จ

เป้าหมายปลายทางคือให้ผู้ใช้เปิดแอปและทำงานจากข้อมูลในเครื่องได้ทันที โดยไม่ต้องรอ network ขณะเดียวกันข้อมูลที่บันทึกต้องไม่หาย ไม่ถูกส่งซ้ำ และผู้ใช้ต้องตรวจสอบสถานะการส่งได้

สิ่งที่ระบบรับประกันได้:

1. การเปิดหน้าจอและการบันทึกลงเครื่องไม่รอ upload/download
2. งานที่ยังไม่ส่งสำเร็จคงอยู่ข้ามการปิดแอป การฆ่า process และการ restart เครื่อง
3. ความล้มเหลวชั่วคราวถูก retry ด้วย backoff ส่วนความผิดพลาดถาวรถูกแสดงให้ผู้ใช้แก้ไข
4. ผู้ใช้เห็นจำนวนงานค้าง สถานะกำลังซิงค์ เวลาที่ซิงค์ล่าสุด และรายการที่ต้องจัดการ
5. ข้อมูลใหม่ไม่ถูกสร้างซ้ำแม้ request สำเร็จที่ server แต่ response หายระหว่างทาง
6. ข้อมูลในมือถือมีขนาดควบคุมได้ โดยไม่ลบข้อมูลที่ยังไม่ซิงค์

สิ่งที่ระบบไม่สามารถรับประกันได้:

- ไม่สามารถรับประกันว่า upload จะเสร็จเร็วเมื่อ network หรือ server ช้า
- ไม่สามารถทำให้ข้อมูลจาก server ปรากฏทันทีในเครื่องใหม่โดยไม่ใช้เวลา download
- สิ่งที่จะรับประกันแทนคือผู้ใช้เข้าใช้งานส่วนที่อาศัย local data ได้ทันที และเห็นสถานะที่ถูกต้องระหว่างรอ

## 2. หลักความปลอดภัยที่ห้ามละเมิดทุกระยะ

1. **Local-first:** UI อ่านข้อมูลธุรกิจจาก Room ไม่อ่าน network มาแสดงโดยตรง
2. **Unsynced data is protected:** ห้าม `DELETE`, `clearAllTables` หรือ retention job แตะแถวที่ยังส่งไม่สำเร็จ
3. **Server acknowledgement:** ตั้ง `is_synced = true` ได้ต่อเมื่อ endpoint ตอบสำเร็จและ response ผ่านเงื่อนไขของ endpoint แล้วเท่านั้น
4. **No cross-user leakage:** ห้ามแสดงหรือส่งข้อมูลของผู้ใช้เดิมด้วย token ของผู้ใช้ใหม่
5. **Idempotent retry:** งานสร้างใหม่ต้อง retry ได้โดยไม่สร้างรายการซ้ำ
6. **Transactional cursor:** เปลี่ยน sync cursor ได้หลังจากบันทึกข้อมูลของ page นั้นลง Room สำเร็จทั้ง transaction แล้วเท่านั้น
7. **Backward-compatible API:** endpoint รุ่นใหม่ต้องเป็นแบบ additive และยังไม่ลบ endpoint เดิมจนกว่า Android รุ่นเก่าจะหมดอายุใช้งาน
8. **No sensitive logs:** ห้าม log token, password, payload เต็ม, รูป, พิกัดละเอียด, เบอร์โทร หรือข้อมูลส่วนบุคคลที่ไม่จำเป็น
9. **Migration-only schema changes:** การเปลี่ยน Room/PostgreSQL ต้องมี migration และ migration test ห้ามใช้ destructive fallback
10. **Small reversible commits:** แยก data model, worker, UI และ backend เป็น commit ที่ย้อนกลับได้ ไม่รวม refactor ที่ไม่เกี่ยวข้อง

## 3. ภาพรวมพฤติกรรมปัจจุบัน

### 3.1 เปิดแอปโดยยังมี session

`MainActivity.onResume()` เรียก foreground `runSyncNow()` เมื่อห่างจากรอบก่อนเกิน 60 วินาที งานรันบน `Dispatchers.IO` จึงไม่บล็อก main thread โดยตรง แต่ยังแข่งขันใช้ CPU, disk, Room connection และ network กับงานหน้าจอ

### 3.2 Login

`AuthRepository.login()` รอทั้งการส่ง outbox เดิมและ `data/repository/SyncManager.syncAll()` ก่อนคืน success ทำให้เวลาออกจากหน้า Login ขึ้นกับจำนวนข้อมูล, network, server และ timeout

### 3.3 Upload outbox

`utils/SyncManager.doSync()` อ่านทุกแถว `is_synced = false` แล้วส่งทีละรายการตาม dependency order ปัจจุบัน catch network/HTTP failure ภายใน loop เป็นส่วนใหญ่ ทำให้ `SyncWorker` อาจคืน `Result.success()` ทั้งที่ยังมีงานค้าง และ WorkManager backoff ไม่ถูกใช้จริงในกรณีทั่วไป

### 3.4 Download sync และ local storage

Room เป็นทั้ง cache และที่เก็บงานค้าง `clearAndInsert` พยายามรักษาแถว unsynced แต่ endpoint หลายตัวดึงข้อมูลก้อนใหญ่ สูงสุด 5,000 รายการ และหน้ารายการหลายจุดใช้ `Flow<List<T>>` ทั้งชุด ไม่มี Paging

### 3.5 สิ่งที่มีอยู่แล้วและต้องรักษา

- Room เป็นแหล่งข้อมูลหลักของ UI
- เขียน local ก่อนส่ง API สำหรับข้อมูลสำคัญส่วนใหญ่
- มี `is_synced`, `sync_rejection`, `pendingSummary()` และ `rejectedSummary()`
- มี WorkManager constraint `NetworkType.CONNECTED`, unique work แบบ `KEEP` และ mutex ป้องกัน sync ซ้อน
- มีการรักษา unsynced rows ระหว่าง refresh หลายตาราง
- มี banner แจ้ง download sync บางส่วนล้มเหลวในหน้า Home
- มีการ redact `Authorization` ใน debug HTTP logger

## 4. สถาปัตยกรรมเป้าหมายเมื่อครบทั้งสามระยะ

```text
ผู้ใช้เปิดแอป
    |
    +--> แสดงข้อมูลจาก Room ทันที
    |
    +--> SyncCoordinator enqueue งานเดียวใน WorkManager
             |
             +--> ระบาย durable outbox ตามลำดับ dependency
             +--> ดาวน์โหลดเฉพาะการเปลี่ยนแปลงหลัง cursor
             +--> เขียน Room เป็น transaction
             +--> อัปเดต SyncState ให้ UI

UI สังเกต Room + SyncState
    |
    +--> Idle / Pending / Running / Success / Needs attention
```

ข้อมูลบน server เป็นประวัติฉบับเต็ม ส่วนมือถือเก็บ working set ที่ต้องใช้ offline และงานที่ยังไม่ sync เท่านั้น

---

# ระยะที่ 1 — ทำให้เปิดใช้งานได้ทันทีและทำให้ retry/status เชื่อถือได้

## 5. เป้าหมายระยะที่ 1

- การเปิดแอปและการกลับจาก background ไม่ยิง sync ตรงบน lifecycle
- Login สำเร็จแล้วไม่รอ full download ก่อนเข้าหน้าหลัก
- WorkManager รู้ผลสำเร็จ/ล้มเหลวจริงและ retry ถูกประเภท
- ผู้ใช้เห็นงานค้าง สถานะกำลัง sync และรายการที่ต้องแก้
- มี error taxonomy และ structured logs ขั้นพื้นฐาน
- ยังไม่เปลี่ยน wire format ของ API และยังไม่ทำ Room schema ขนาดใหญ่โดยไม่จำเป็น

## 6. งาน Android ระยะที่ 1

### 6.1 สร้างผลลัพธ์การ sync ที่มีโครงสร้าง

เพิ่มชนิดข้อมูลที่แยกอย่างน้อย:

```text
SyncRunResult
- attempted
- succeeded
- temporaryFailures
- permanentFailures
- skipped
- stillPending
- startedAt / finishedAt
- runId
```

และจัดประเภทข้อผิดพลาด:

```text
Temporary
- no validated network
- DNS/connect/timeout
- HTTP 408, 429, 5xx

Authentication
- HTTP 401

Permanent or needs user/admin action
- HTTP 400, 403, 404, 409, 422

Local fatal
- Room write/migration/serialization failure

Cancelled
- coroutine/worker ถูกยกเลิก ไม่บันทึกเป็น error ของข้อมูล
```

ข้อควรระวัง:

- ต้อง rethrow `CancellationException` เสมอ ห้าม catch แล้วแปลงเป็น failure ปกติ
- processing failure ของหนึ่งแถวต้องไม่หยุดแถวที่เป็นอิสระ แต่ต้องถูก aggregate ในผลท้ายรอบ
- dependency failure เช่น customer TEMP ยังไม่ได้ real ID ต้องทำให้ project/appointment ลูกถูกเลื่อนไปรอบถัดไป ไม่ใช่ permanent rejection

### 6.2 แก้ `SyncWorker` ให้สะท้อนผลจริง

กฎผลลัพธ์:

- ไม่มี temporary failure และไม่มีงานที่ควร retry → `Result.success()`
- ยังมี temporary failure → `Result.retry()`
- เหลือเฉพาะ permanent rejection ที่ถูกบันทึกแล้ว → `Result.success()` เพื่อไม่ให้ยิง payload เดิมไม่รู้จบ
- local database/serialization invariant พัง → `Result.failure()` พร้อม diagnostic ที่ไม่เปิดเผยข้อมูลสำคัญ

ไม่ใช้ `runAttemptCount` เพื่อตัดงาน temporary เป็น failure ถาวรโดยอัตโนมัติ เพราะแถวใน Room ยังคงต้องถูกส่งในอนาคต ให้ WorkManager backoff และมีปุ่ม retry โดยผู้ใช้แทน

### 6.3 แยกการเปิดแอปออกจากการรัน sync โดยตรง

เปลี่ยน `MainActivity.onResume()` จาก `runSyncNow(lifecycleScope)` เป็นการเรียก `SyncScheduler.ensureScheduled(reason = APP_FOREGROUND)` ซึ่งทำเพียง enqueue unique work แบบ `KEEP`

ผลที่ต้องรักษา:

- งานเดิมที่กำลังรันหรือรอ backoff ไม่ถูกยกเลิก
- ไม่เกิด worker หลายตัวแข่งขันกัน
- การเปิดแอปไม่รอผล worker
- mutex เดิมคงไว้เป็น defense-in-depth ระหว่าง manual sync และ worker จนกว่าจะรวมทุก entry point ได้

### 6.4 แยก Login ออกจาก initial download

ลำดับใหม่:

```text
ยืนยัน username/password
→ ตรวจ local data owner ก่อนเขียน token ใหม่
→ บันทึก session อย่างปลอดภัย
→ navigate เข้าหน้าหลัก
→ enqueue InitialDownloadWorker
→ UI แสดง local cache หรือ empty state พร้อมสถานะกำลังโหลด
```

กรณีผู้ใช้เดิม:

- ใช้ cache เดิมได้ทันที
- enqueue upload ก่อน download เพื่อไม่ให้ข้อมูล server เก่ากลับมาชนงาน local

กรณีเปลี่ยนผู้ใช้:

- ถ้ามีงาน unsynced ของผู้ใช้เดิม ต้องบล็อก account switch และแสดงทางเลือก “กลับไปส่งข้อมูล”, “ลองใหม่” หรือ “ยืนยันการทิ้งข้อมูล” ห้ามล้างเงียบ ๆ
- ถ้าไม่มีงานค้าง ให้ล้างเฉพาะ cache ของผู้ใช้เดิมก่อนเปิดข้อมูลผู้ใช้ใหม่
- หน้าหลักของผู้ใช้ใหม่แสดง empty/loading state ห้ามแสดงข้อมูลของผู้ใช้เดิมแม้ชั่วขณะเดียว

ระยะที่ 3 จะเปลี่ยนเป็น storage แยกตามผู้ใช้ แต่ระยะที่ 1 ต้องป้องกันข้อมูลหายและข้อมูลข้ามบัญชีก่อน

### 6.5 เพิ่ม `SyncCoordinator` เป็นสถานะกลางของแอป

เปิด `StateFlow<SyncUiState>` ชุดเดียว:

```text
Idle(lastSuccessAt)
Queued(pendingCount)
Running(processed, total, currentCategory)
PartialFailure(pendingCount, lastErrorAt)
NeedsAttention(rejectedCount)
Offline(pendingCount)
```

แหล่งข้อมูลต้องมาจาก Room/WorkManager ไม่ผูกกับ Composable ใด Composable หนึ่ง เพื่อให้หมุนหน้าจอหรือเปลี่ยนหน้าแล้วสถานะไม่หาย

หลีกเลี่ยง progress ที่ละเอียดถึงทุก request หากทำให้ query/recomposition ถี่เกินไป อัปเดตเป็นช่วงหรือเมื่อเปลี่ยนหมวดข้อมูล

### 6.6 UI สถานะ sync

เพิ่มโดยไม่เปลี่ยน flow หลักของหน้าจอ:

1. App bar indicator ขนาดเล็ก
   - กำลังส่ง
   - รออินเทอร์เน็ต
   - ส่งครบแล้ว
   - ต้องตรวจสอบ
2. Home banner เมื่อมี pending/rejected
3. หน้า “สถานะการซิงค์” ใน Settings
   - จำนวนแยกตามลูกค้า/ผู้ติดต่อ/โครงการ/นัดหมาย/ผล/checklist/รูป
   - รายการที่ถูกปฏิเสธและข้อความที่ผู้ใช้เข้าใจได้
   - เวลาส่งสำเร็จล่าสุด
   - ปุ่ม “ลองส่งอีกครั้ง”
4. แสดงสถานะรายรายการในหน้าที่มีความเสี่ยงสูง เช่น เช็คอินและบันทึกผล ก่อนขยายไปทุก list

ห้ามใช้ animation ใหญ่หรือ dialog บังคับระหว่าง background sync เพราะจะขัดเป้าหมาย “เปิดแล้วใช้ได้ทันที”

### 6.7 แสดงงานยังไม่ sync หลังจบวัน

ข้อสรุปผลิตภัณฑ์: ใช้เขตเวลา `Asia/Bangkok`, เริ่มเตือนเวลา 22:00 และตัดวันเวลา 00:00 โดยแยกค่าเวลาไว้ให้เปลี่ยนภายหลังได้ แผนรองรับดังนี้:

- แสดง pending banner ตลอดเมื่อมีงานค้าง ไม่ต้องรอจบวัน
- หลังเวลาปิดวัน ถ้ายังมี pending ให้สร้าง local notification หนึ่งครั้งต่อ business date
- notification แสดงเพียงจำนวนและชนิดข้อมูล ไม่แสดงชื่อลูกค้า/พิกัดบน lock screen
- กด notification เปิดหน้า Sync Status
- ถ้าไม่มี pending ไม่แจ้ง
- permanent rejection ใช้ข้อความ “มีรายการต้องตรวจสอบ” แยกจาก “รออินเทอร์เน็ต”
- เก็บ `lastNotifiedBusinessDate` เพื่อไม่แจ้งซ้ำเมื่อเปิดแอปหลายครั้ง

การตรวจหลังจบวันใช้ WorkManager แบบ periodic/flex หรือประเมินอีกครั้งเมื่อ app foreground หลังเวลาที่กำหนด ไม่รับประกันเวลาระดับนาทีเพราะ Android อาจเลื่อน background work

### 6.8 Error handling และ logging ระยะที่ 1

สร้าง abstraction กลาง เช่น `AppLogger` และ `AppError` โดยยังใช้ Android Log/SLF4J เป็นปลายทางได้ ไม่ผูกกับผู้ให้บริการภายนอกในระยะนี้

ทุก sync run ต้องมี:

- `run_id` แบบ UUID
- trigger: save, app_foreground, login, manual, periodic
- user identifier แบบ hash หรือรหัสที่ผ่านนโยบายข้อมูล ห้าม email ตรง ๆ
- entity type และ ID แบบ redact/hash เมื่อ log production
- duration, attempt count, HTTP class, outcome
- pending count ก่อนและหลัง

ตัวอย่าง event:

```text
sync_run_started
sync_item_failed_temporary
sync_item_rejected
sync_run_completed
download_section_failed
room_write_failed
```

กฎ logging:

- production ไม่ log HTTP BODY
- debug BODY logger ต้องใช้เฉพาะ test data
- error body จาก server ต้อง sanitize ก่อน log
- ห้าม swallow exception แบบว่างเปล่าโดยไม่มี outcome/metric
- ข้อผิดพลาดที่ผู้ใช้แก้ไม่ได้ให้ข้อความทั่วไปและ reference ID จาก `run_id`
- log ในเครื่องที่เปิดให้ดูผ่านหน้า diagnostics ต้องจำกัดจำนวน/อายุ เช่น ring buffer ไม่ให้โตตลอดไป

### 6.9 แยก timeout ตามลักษณะงาน

- interactive save: timeout สั้นและมีเพดานเวลารวมที่ UX ยอมรับ จากนั้นเก็บเป็น pending
- background worker: timeout ยาวกว่าได้ แต่ต้อง cancellable และมี backoff
- upload รูป: timeoutแยกจาก JSON request
- ค่าจริงต้องวัดจาก production-like network ก่อนกำหนด ห้ามเลือกตัวเลขจากความรู้สึก

## 7. งาน backend ระยะที่ 1

ระยะนี้หลีกเลี่ยงการเปลี่ยน protocol หลัก แต่เพิ่มความสม่ำเสมอของ error response แบบ backward compatible:

```json
{
  "error": "VALIDATION_ERROR",
  "message": "ข้อความเดิมที่ client เก่ายังอ่านได้",
  "request_id": "...",
  "retryable": false
}
```

งานที่ต้องทำ:

- เพิ่ม request/correlation ID ใน Ktor pipeline และ response header
- ให้ `StatusPages` map exception เป็น HTTP status/error code ที่แน่นอน
- log request duration, route, status, request ID โดยไม่ log token/body/PII
- แยก 4xx ที่ไม่ควร retry ออกจาก 429/5xx ที่ retry ได้
- รักษาฟิลด์ `error` และ `message` เดิมสำหรับ Android รุ่นเก่า

## 8. การทดสอบระยะที่ 1

### Unit tests

- network exception หนึ่งแถวแล้วแถวถัดไปยังถูกส่ง แต่ผลรวมเป็น temporary failure
- HTTP 400/403/404/409/422 ถูกบันทึกใน `sync_rejection`
- HTTP 429/500/timeout ทำให้ Worker retry
- cancellation ไม่ถูกแปลงเป็น retry/failure ปกติ
- permanent rejection อย่างเดียวไม่ทำให้ worker retry ไม่รู้จบ
- pending summary นับถูกต้องทุก entity
- notification หลังจบวันแจ้งครั้งเดียวและไม่แจ้งเมื่อไม่มี pending
- account switch ถูกบล็อกเมื่อผู้ใช้เดิมมี unsynced data

### Integration/instrumented tests

- เปิดแอปตอน offline แล้วยังเห็น Room data และกดใช้งานได้
- เปิดแอประหว่างมี pending 1,000 รายการ หน้าจอแรกไม่รอ sync
- kill process ระหว่างส่ง แล้วเปิดใหม่ งานยังอยู่และส่งต่อได้
- หมุนจอ/สลับหน้า สถานะ sync ไม่หายหรือเริ่ม worker ซ้ำ
- Login ผู้ใช้เดิมเห็น cache ก่อน download เสร็จ
- Login ผู้ใช้ใหม่ไม่เห็นข้อมูลผู้ใช้เดิม

### Regression tests

- create/update customer, contact, project, appointment, checklist, check-in และ result
- TEMP ID เปลี่ยนเป็น real ID พร้อมย้าย FK ครบ
- logout protection ยังทำงาน
- notification/alarm ของนัดหมายยังอ้าง real ID หลัง sync

## 9. เกณฑ์ผ่านระยะที่ 1

- cold/warm open ไม่ await network call บนเส้นทางก่อน first usable screen
- network ใช้ไม่ได้แล้ว UI หลักยังทำงานจาก Room
- Worker คืน retry เมื่อจำลอง timeout/5xx และ success เมื่อส่งครบ
- งานค้างคงอยู่หลัง process death
- ผู้ใช้เห็น pending/running/success/needs-attention อย่างไม่กำกวม
- หลังจบวันมี pending notification ตามกฎ และไม่มี notification ซ้ำ
- ไม่มี token/PII/payload เต็มใน production logs
- unit tests และ Android compile ผ่านทั้งหมด

## 10. Rollout และ rollback ระยะที่ 1

- แยก release เป็น internal → pilot users → production
- เก็บเส้น `runSyncNow()` เดิมไว้ชั่วคราวหลัง interface แต่ปิดการเรียกจาก `onResume`; rollback ได้โดยสลับ scheduler โดยไม่เปลี่ยน schema
- อย่าลบ `is_synced` หรือ `sync_rejection`
- หาก background initial download มีปัญหา ให้ fallback เป็นปุ่ม manual refresh ไม่ย้อนกลับไปบล็อก Login ทันที

---

# ระยะที่ 2 — รองรับข้อมูลมาก ป้องกันข้อมูลซ้ำ และทำ attachment ให้ทนทาน

## บันทึกการดำเนินงาน 2A — เตรียม baseline และ migration safety (4 ตุลาคม 2026)

- ยืนยันจาก source of truth ว่า Room ก่อนเริ่มงานเป็น version 53 และมี 11 entities; แก้เอกสารประกอบที่ระบุ version 48/10 entities ซึ่งล้าสมัย
- เปิด Room schema export และเก็บ snapshot `53.json` กับ `54.json` เพื่อใช้ตรวจ migration ปัจจุบันและ migration ถัดไป
- เพิ่ม `room-testing` และ migration test `53 → 54` ซึ่งสร้างข้อมูล `is_synced = 0` ของ customer/project/activity/result ก่อน migrate จากนั้นยืนยันว่าข้อมูลค้างยังอยู่ครบและ Room schema validation ผ่าน
- เพิ่ม index เฉพาะเส้นทางที่ baseline วัดแล้ว ได้แก่ การเรียง/ค้นหาลูกค้า, งานค้าง, การเรียงโครงการ, activity ตามผู้ใช้และวัน และผลล่าสุดตามโครงการ โดยเพิ่ม Room version เป็น 54 และลงทะเบียน migration แบบไม่ล้างฐานข้อมูล
- index ของ Project กำหนดทิศทาง `startDate DESC, projectId ASC` ให้ตรงกับ stable ordering; รอบตรวจแรกพบ temporary sort ที่ตัว tie-breaker จึงแก้และรันทดสอบใหม่จนไม่มี `USE TEMP B-TREE`
- instrumented tests ผ่าน 3/3 และ JVM regression suite ผ่าน 398 tests, failures 0, errors 0, skipped 4

ผล baseline 5,000 customers + 5,000 projects บน Pixel 9 AVD, Android 17/API 37, RAM ประมาณ 4 GB:

| ตัววัด | ก่อน index (ms) | หลัง index (ms) |
|---|---:|---:|
| เขียน customer 5,000 แถว | 2,132 | 712 |
| เขียน project 5,000 แถว | 1,551 | 701 |
| อ่าน customer ทั้ง 5,000 แถว | 616 | 126 |
| อ่าน project ทั้ง 5,000 แถว | 411 | 115 |
| contains-search customer | 119 | 10 |
| contains-search project | 114 | 10 |

ข้อสรุปที่เชื่อถือได้จากรอบนี้คือ query plan เปลี่ยนจาก full scan/temporary sort ไปใช้ index ตามเงื่อนไขหลัก ส่วนตัวเลขเวลาเป็น single-run บน emulator ที่มีผลจาก warm-up/cache จึงใช้เปรียบเทียบเชิงทิศทางเท่านั้น ไม่ใช้เป็น SLA และไม่อ้างว่า index ทำให้ write เร็วขึ้น การยอมรับบนเครื่องเป้าหมาย Android 12–13/RAM ใกล้ 4 GB และ stress test 50,000 แถวยังคงต้องทำก่อนปิดระยะที่ 2

## บันทึกการดำเนินงาน 2B — Paging และ bounded queries (4 ตุลาคม 2026)

- เพิ่ม Paging 3 สำหรับหน้ารายการโครงการ ลูกค้า ผู้ติดต่อ และประวัติผลการขาย โดยโหลดครั้งแรก 40–60 แถวและจำกัดจำนวนแถวที่เก็บในหน่วยความจำของแต่ละ stream แทน `Flow<List<T>>` ทั้งตาราง
- ย้าย search/filter/tab/sort ไปทำใน Room SQL และเติม primary key เป็น tie-breaker ทุก query เพื่อให้ลำดับคงที่ระหว่าง page
- หน้า Home ไม่ใช้ Paging เพราะ UI ต้องจัดกลุ่มปฏิทินรายเดือน แต่เปลี่ยนจากโหลด activity/result ทั้งหมดเป็น query เฉพาะผู้ใช้และเดือนที่เลือก พร้อม projection ที่ join ชื่อลูกค้า/โครงการและสถานะผลลัพธ์ใน SQL
- การเรียงชื่อลูกค้า/ผู้ติดต่อยังตัดคำนำหน้าบุคคลและนิติบุคคลเหมือน UI เดิม ส่วนแถบตัวอักษรเปลี่ยนตาม decision ที่ยืนยันแล้ว: แตะ/ลากตัวอักษรเพื่อกรองจาก SQLite และ `#` เพื่อล้างตัวกรอง แทนการโหลดทั้งตารางเพื่อคำนวณตำแหน่งแล้ว scroll
- เก็บ full-list repository APIs เดิมไว้สำหรับ export, dropdown และงานที่ต้องใช้ข้อมูลครบ จึงไม่ทำให้รายงานเหลือเฉพาะ page ที่ UI โหลด
- UI แยก initial load, append load และ error/retry; หาก refresh ขณะมีข้อมูลเดิมจะไม่ล้างแถวเดิมจนหน้าจอว่าง
- เพิ่ม index ผู้ติดต่อตาม customer/sync state และผลการขายตาม `result_group_id + version + result_id`; ปรับ index นัดหมายให้ตรง query ผู้ใช้+วัน+เวลา โดยรวมไว้ใน migration 53→54 เดิมเนื่องจาก version 54 ยังไม่เคย deploy
- เพิ่ม Android query tests สำหรับ customer/contact prefix ordering, filter, fallback name, monthly activity projection, result-history ordering/query plan และ migration schema
- stress test 50,000 แถวบน Pixel 9 AVD/API 37/RAM ประมาณ 4 GB: โครงการเขียน 7,553 ms/initial page 60 แถว 106 ms, ลูกค้าเขียน 7,841 ms/page 251 ms และผู้ติดต่อเขียน 6,923 ms/page 291 ms (single run ใช้เป็นหลักฐานเชิงทิศทาง ไม่ใช่ SLA)
- ผลตรวจล่าสุด: JVM 400 tests ผ่าน, failures 0, errors 0, skipped 4; instrumented suite 12/12 ผ่าน รวม migration 53→54 และรอบ stress ลูกค้า/ผู้ติดต่อที่รันแยกอีก 1/1 ผ่าน

สิ่งที่ยังไม่ถือว่าปิดทั้งระยะที่ 2: manual acceptance บนเครื่อง Android 12–13 ที่ผู้ใช้จริงใช้, Macrobenchmark/jank/peak-memory, backend idempotency/capability rollout และ durable photo outbox งานเหล่านี้เป็น checkpoint ถัดไปและไม่รวมอยู่ใน 2B

## บันทึกการดำเนินงาน 2C — Create idempotency (4 ตุลาคม 2026)

- Android สร้าง UUID `operation_id` เพียงครั้งเดียวเมื่อเขียนแถว local ของ customer/project/appointment/result และเก็บไว้กับแถว Room เพื่อให้ retry หลัง process death/reboot ใช้ key เดิม
- เพิ่ม Room version 55 และ migration `54 → 55` แบบไม่ล้างข้อมูล โดยเพิ่ม `operation_id` ใน 4 ตารางและ backfill key ให้แถว TEMP ที่ยังไม่ sync เดิม
- เส้นบันทึกทันทีและเส้น WorkManager ใช้ request builder ชุดเดียวกันและส่ง `Idempotency-Key` เดียวกัน จึงไม่เกิด false conflict จาก payload ที่สร้างคนละแบบ; ตัดค่าที่เปลี่ยนทุก retry เช่นเวลาปัจจุบันออกจาก create-result payload
- Backend เพิ่มตาราง `api_idempotency` โดย unique ตาม `owner_id + resource + operation_id` เก็บ request hash และ response entity ID; การ claim key, สร้าง business row และบันทึก response ID อยู่ใน PostgreSQL transaction เดียวกัน
- request key/payload เดิมคืน entity เดิม, key เดิมแต่ payload เปลี่ยนคืน HTTP 409 ผ่าน error contract เดิม, header ไม่ถูกต้องคืน HTTP 400 และ client รุ่นเก่าที่ไม่ส่ง header ยังใช้ endpoint เดิมได้
- หลังได้ real ID การ insert parent ใหม่, ย้าย FK/local references และลบ TEMP parent ถูกรวมเป็น Room `@Transaction` สำหรับทั้ง 4 ประเภท เพื่อไม่ให้ process death ทิ้งข้อมูลไว้ครึ่งทาง
- immediate POST ยังไม่เปิด automatic network retry ภายในคำสั่งเดียว เพื่อไม่เสี่ยงกับ backend รุ่นเก่าระหว่าง rollout; งานจะคงอยู่ใน local outbox และ WorkManager จะ retry ด้วย key เดิมแทน
- ลำดับ rollout ที่บังคับ: deploy backend พร้อม PostgreSQL migration ก่อน ตรวจ staging ว่า replay ได้ ID เดิม แล้วจึงปล่อย Android version 55; รอบนี้ยังไม่ได้ deploy ระบบจริง
- ผลตรวจล่าสุด: Android JVM 400 tests ผ่าน (failures 0, errors 0, skipped 4), debug/test APK compile ผ่าน, instrumented suite 18/18 ผ่านบน Pixel 9 AVD/API 37 รวม migration และ TEMP→real transaction tests; backend 29/29 tests ผ่านและ compile สำเร็จ
- ข้อจำกัดของ environment รอบนี้: Docker/PostgreSQL integration environment ไม่ได้ทำงาน จึงยังไม่ได้รันกรณีตัด connection หลัง server commit กับ PostgreSQL จริง งานนี้เป็น staging acceptance ที่ต้องทำก่อน production rollout ไม่ใช่เหตุผลให้ข้ามลำดับ deploy

## บันทึกการดำเนินงาน 2D — Durable photo outbox (5 ตุลาคม 2026)

- เปลี่ยน flow รูปใหม่จาก “อ่านทั้งไฟล์เข้า memory แล้วต้อง upload ให้เสร็จก่อนบันทึกผล” เป็น copy แบบ streaming ไปยัง `filesDir/pending_attachments` ก่อน จากนั้นบันทึกผลและคิวรูปลง Room ใน transaction เดียว ผู้ใช้จึงบันทึกและใช้งานต่อได้ขณะ offline
- เพิ่ม Room version 56 และ migration `55 → 56` สำหรับ `attachment_outbox` ซึ่งเก็บ operation ID, owner, result TEMP/real ID, ลำดับรูป, local path, MIME, SHA-256, ขนาด, สถานะ, remote URL, attempts และ error แบบไม่เก็บข้อมูลรูป/URL ลง log
- เมื่อ result ได้ real ID แล้ว transaction เดิมจะ remap ทั้ง `activity_result_photo` และ `attachment_outbox` ก่อนลบ TEMP parent จึงไม่เสียรูปจาก FK cascade หาก process ตายระหว่างเปลี่ยน ID
- แยกสถานะ `pending_upload` กับ `pending_bind`: เมื่อ binary upload สำเร็จจะ persist URL ก่อนเรียกผูก metadata หากขั้น bind หรือ cover update ล้มเหลว รอบต่อไปจะใช้ URL เดิมและไม่ upload binary ซ้ำ
- Android ส่ง `Idempotency-Key` และ `X-Content-SHA256`; backend ใช้ path/ชื่อไฟล์ที่กำหนดจาก owner+operation ID และ metadata sidecar ทำให้ retry หลัง response loss คืน URL เดิม พร้อมปฏิเสธ key เดิมที่ checksum ต่างกัน
- endpoint ผูกรูปบน backend เปลี่ยนเป็น update-or-insert ตาม `(result_id, photo_order)` เพื่อให้ bind request เดิมถูกส่งซ้ำได้โดยไม่ชน primary key
- ไฟล์ local ถูกลบหลัง upload, bind และ cover metadata สำเร็จครบเท่านั้น; cleanup 7 วันลบเฉพาะไฟล์ staging ที่ไม่มีแถว outbox อ้างถึง และไม่ลบ pending attachment
- รูปที่ยังค้างแสดงไฟล์ local พร้อมป้าย “รอซิงค์”; รูปที่สำเร็จแล้วเก็บเพียง URL และเมื่อโหลดไม่ได้ขณะ offline จะแสดง placeholder แทน URL ดิบ ตาม decision ที่ยืนยันไว้
- owner ของคิวรูปถูกตรวจด้วย local data owner; รายการรูปเข้ารวมใน pending/rejected summary และไฟล์จะถูกลบก่อน clear Room เฉพาะ flow ที่ผู้ใช้ยืนยันการทิ้งข้อมูล/เปลี่ยนบัญชีแล้ว
- เพิ่ม regression guard สำหรับกรณี create result ได้ HTTP 2xx แต่ server ไม่คืน real result ID: แอปจะไม่ mark TEMP result ว่า synced, คงรูปไว้ใน outbox และจัดเป็น server failure ที่ retry ได้ เพื่อไม่ให้ attachment ค้างโดยไม่มีโอกาสถูกส่ง
- ผลตรวจล่าสุด: Android JVM 405 tests ผ่าน (failures 0, errors 0, skipped 4), debug และ androidTest APK compile ผ่าน; backend 29/29 tests ผ่านและ compile สำเร็จ
- หลัง Cold Boot Pixel 9 AVD/API 37 สามารถติดตั้ง APK ได้ตามปกติ และ instrumented suite ผ่าน 19/19 (0 skipped, 0 failed) รวม migration `55 → 56`, TEMP-result attachment remap และ regression suite เดิม ปัญหา `Broken pipe` ก่อนหน้าเป็นสถานะค้างของ package service ใน emulator ไม่ใช่ความผิดพลาดของ test
- ยังไม่ได้ deploy Android/backend; rollout ต้องลง backend ก่อน Android และต้องทำรายการ acceptance/deployment ของ 2C ที่พักไว้ก่อน production เช่นเดิม

## 11. เป้าหมายระยะที่ 2

- ลด RAM/CPU/Room scan เมื่อข้อมูลหลักพันถึงหลักหมื่น
- ลดจำนวน request และป้องกัน POST ซ้ำ
- ทำรูปที่รอ upload ให้เป็น durable outbox จริง
- เพิ่ม performance tests ก่อนเปลี่ยนวิธีดาวน์โหลดในระยะที่ 3

## 12. Paging และ query design

เปลี่ยนหน้ารายการขนาดใหญ่จาก `Flow<List<T>>` เป็น Room `PagingSource` และ Paging 3 ได้แก่:

- ลูกค้า
- ผู้ติดต่อ
- โครงการ
- นัดหมาย
- ประวัติผลการขาย

หลักการ:

- filter/search/sort ต้องทำใน SQL ไม่ดึงทั้งตารางมา filter ใน ViewModel
- ใช้ stable ordering ที่มี primary key เป็นตัวตัดสินลำดับสุดท้ายเสมอ
- UI ใช้ stable item keys
- dashboard/count ใช้ aggregate query แยก ไม่โหลดทุก row มานับ
- Export ใช้ range query/streaming แยกจาก Paging UI เพื่อให้รายงานยังครบ

## 13. Database indexes

เก็บ baseline ด้วย `EXPLAIN QUERY PLAN` และ benchmark ก่อนเพิ่ม index จากนั้นพิจารณา index ตาม query จริง เช่น:

- customer: `user_id`, ชื่อที่ค้นหา, `is_synced`
- contact: `custId`, `is_synced`
- project: `user_id/create_by + status`, `custId`, `startDate`, `is_synced`
- activity: `user_id + planned_date`, `project_id`, `cust_id`, `is_synced`
- result: `created_by + report_date`, `result_group_id + version`, `project_id + is_latest`, `is_synced`
- checklist/outbox: parent ID + sync state

ข้อควรระวัง:

- index ทำให้ read เร็วขึ้นแต่เพิ่มพื้นที่และต้นทุน write
- เพิ่มเฉพาะ index ที่ query plan ใช้จริง
- Room ต้องเพิ่ม database version แบบลำดับถัดไป พร้อม migration test จากทุก version ที่ยังรองรับ

## 14. Idempotency สำหรับ create

เพิ่ม `operation_id` UUID ตั้งแต่ตอนเขียน local row และส่งเป็น `Idempotency-Key` หรือ field ใน request

Backend ต้อง:

- มี unique constraint ต่อ caller/scope + operation ID
- บันทึก operation และผลลัพธ์ใน transaction เดียวกับการสร้าง entity
- request ซ้ำด้วย key/payload เดิมคืน entity เดิม
- key เดิมแต่ payload คนละชุดคืน conflict ที่ชัดเจน
- เก็บ mapping นานกว่าระยะเวลาที่ client อาจ retry

Android ต้อง:

- สร้าง key ครั้งเดียวและเก็บใน Room ห้ามสร้างใหม่ทุก retry
- หลังได้ real ID ให้ย้าย FK และ mark synced ใน transaction เดียว
- รองรับ backend รุ่นเก่าระหว่าง rollout โดยตรวจ capability ก่อนใช้

เริ่มจาก customer/project/appointment/result ที่ server เป็นผู้สร้าง ID แล้วขยายไป entity อื่น

## 15. Batch upload อย่างปลอดภัย

ใช้ batch เฉพาะรายการที่เป็นอิสระและ backend ทำ transaction/result รายรายการได้

- จำกัด batch size จากการวัด payload และ latency
- response ต้องคืนผลต่อ operation ID ไม่ใช่ success รวมอย่างเดียว
- ห้าม batch parent กับ child จนไม่รู้ว่ารายการใดล้มเหลว
- รักษาลำดับ customer → contact/project → appointment → checklist/result/photo
- HTTP 207 หรือ response model ราย item ต้องกำหนดชัด ไม่เดาจาก status รวม

หาก backend ยังไม่รองรับ batch ให้ใช้ bounded concurrency ขนาดเล็กแทน แต่ห้ามส่ง entity เดียวกันพร้อมกัน

## 16. Durable photo outbox

ปัจจุบันข้อมูลรูปเป็น URL ใน Room และการ upload ใช้ bytes ใน memory ส่วนไฟล์กล้องอยู่ใน `cacheDir` ซึ่งระบบอาจลบได้ จึงยังไม่ทนต่อ offline/process death อย่างแท้จริง

แผน:

- เมื่อต้องรอ upload ให้ copy รูปไป app-private persistent files ไม่ใช้ cache เป็นแหล่งเดียว
- สร้าง attachment outbox: operation ID, owner, parent temp/real ID, local path, checksum, size, MIME, state, attempts
- บีบ/ปรับขนาดแบบรักษา EXIF ที่ business rule ต้องใช้ ก่อนเข้าคิวหรือกำหนด format contract ชัดเจน
- upload สำเร็จและ metadata/result ผูกกับ URL สำเร็จแล้วจึงลบไฟล์ local
- ถ้า upload สำเร็จแต่การผูก metadata ล้มเหลว ต้อง retry ขั้นผูกโดยไม่ upload ซ้ำ
- retention cleanup ห้ามลบไฟล์ของ pending attachment
- **ข้อกำหนดที่ยืนยันแล้ว:** หลัง upload และผูก metadata สำเร็จ ให้เก็บเพียง URL ใน Room ไม่เก็บ full-resolution หรือ managed offline thumbnail เป็นข้อมูลถาวรในเครื่อง
- เมื่อ offline และไม่มีรูปอยู่ใน cache ชั่วคราว UI ต้องแสดง placeholder พร้อมข้อความว่าไม่สามารถโหลดรูปขณะ offline ห้ามแสดง URL ดิบแก่ผู้ใช้
- cache รูปของ Coil เป็นเพียง optimization และระบบล้างได้ ห้ามใช้เป็นหลักฐานว่ารูปถูกเก็บสำหรับ offline แล้ว

## 17. Performance measurement ระยะที่ 2

เพิ่ม Macrobenchmark อย่างน้อย:

- cold start และ warm start โดยมี Room 0, 5k, 50k rows
- เปิด/เลื่อน customer และ project list
- เปิด Home ขณะ Worker กำลัง sync
- search/filter
- export ช่วงข้อมูลใหญ่

วัด:

- time to initial display และ time to fully usable
- frame time/jank
- peak memory
- query duration
- database size
- sync requests, bytes, duration และ battery/network โดยประมาณ

กำหนด SLO หลังได้ baseline บนเครื่องระดับต่ำที่องค์กรใช้งานจริง ไม่ตั้งตัวเลขจาก emulator เพียงอย่างเดียว

## 18. การทดสอบระยะที่ 2

- Paging refresh/append/retry และ filter ทุกแบบ
- ไม่มี row ซ้ำหรือหายเมื่อข้อมูลเปลี่ยนระหว่างเลื่อน
- idempotency: ตัด connection หลัง server commit แล้ว retry ต้องได้ ID เดิม
- batch partial failure mark เฉพาะแถวที่สำเร็จ
- attachment รอดจาก process death/reboot และไม่ถูก cache cleanup ลบ
- migration เก็บ unsynced/temp rows และ FK ครบ
- Export ให้ผลเท่าเดิมหลังหน้ารายการเปลี่ยนเป็น Paging

## 19. Rollout และ rollback ระยะที่ 2

- backend รองรับ idempotency ก่อนปล่อย Android ที่ใช้ feature
- Android ใช้ server capability/version negotiation และ fallback request เดิม
- Paging เปลี่ยนทีละหน้าจอ เริ่มจากหน้าที่ข้อมูลมากที่สุด
- index migration แยกจาก UI refactor เพื่อวิเคราะห์ regression ได้
- attachment outbox เปิดเฉพาะรูปใหม่ รูปเก่ายังอ่าน URL เดิมได้

---

# ระยะที่ 3 — Delta Sync, การแยกผู้ใช้ และนโยบายเก็บข้อมูลระยะยาว

## 20. เป้าหมายระยะที่ 3

- เลิกดาวน์โหลดข้อมูลทั้งหมดทุก Login/refresh
- sync เฉพาะสิ่งที่เปลี่ยน พร้อมรองรับ deletion
- จำกัด working set ในมือถือโดยไม่ลบประวัติบน server
- แยกข้อมูลแต่ละผู้ใช้อย่างปลอดภัย
- มี fallback และ recovery เมื่อ cursor เสียหรือ schema เปลี่ยน

## 21. Change feed บน backend

แนะนำใช้ลำดับเพิ่มขึ้นจาก server เช่น `change_seq` มากกว่าอาศัย timestamp จากมือถือเพียงอย่างเดียว

ตาราง change log ขั้นต่ำ:

```text
seq
scope/owner/branch
entity_type
entity_id
operation: UPSERT | DELETE
server_revision
changed_at
```

เขียน change log ใน transaction เดียวกับ business row ผ่าน service/repository หรือ database trigger ที่ทดสอบได้ ห้ามตอบ client สำเร็จหาก business row commit แต่ change log ไม่เกิด

## 22. Protocol initial snapshot ที่ไม่ทำข้อมูลตกหล่น

ลำดับ bootstrap:

1. ขอ `snapshot_cursor = C` จาก server
2. ดาวน์โหลด snapshot ทีละหน้าแบบ keyset pagination และ stable order
3. เขียนแต่ละ page ลง Room transactionally
4. หลัง snapshot ครบ ขอ changes ที่ `seq > C`
5. apply changes ตามลำดับจนถึง cursor ล่าสุด
6. จึงประกาศว่า initial sync สำเร็จ

เหตุผล: การเปลี่ยนแปลงที่เกิดระหว่างดาวน์โหลด snapshot จะมี seq มากกว่า C และถูกตามเก็บภายหลัง

## 23. Protocol incremental sync

```text
GET /sync/v2/changes?after=<cursor>&limit=<pageSize>
```

กฎ client:

- apply page ตาม seq ใน Room transaction
- UPSERT ต้องไม่ทับ local pending edit โดยไม่มี conflict rule
- DELETE ใช้ tombstone และลบเฉพาะแถวที่ synced; หาก local มี pending edit ให้เข้าสถานะ conflict
- บันทึก `next_cursor` ใน transaction เดียวกับ page
- ถ้า process ตายก่อน commit ให้โหลด page เดิมซ้ำได้โดยไม่มีผลเสีย
- ถ้า cursor หมดอายุ/ไม่รู้จัก ให้ fallback เป็น bootstrap ใหม่โดยยังรักษา outbox

## 24. Conflict resolution

เพิ่ม `server_revision` หรือ ETag ต่อ entity:

- client เก็บ revision ตอนดาวน์โหลด
- update ส่ง base revision
- revision ตรง → server update และเพิ่ม revision
- revisionไม่ตรง → HTTP 409 พร้อมข้อมูลที่พอให้ client แสดงหรือ merge

ไม่ใช้ client timestamp ตัดสินเพียงอย่างเดียว เพราะเวลาของมือถือควบคุมไม่ได้

กฎ merge ต้องกำหนดราย entity:

- master/reference data: server wins
- user-owned note/check-in/result: ห้ามทับเงียบ ต้องแสดง conflict หรือสร้าง version
- activity result ที่ระบบ version อยู่แล้ว: ใช้กลไก version ฝั่ง server ต่อไป

## 25. แยกข้อมูลตามผู้ใช้

ทางเลือกที่แนะนำคือ database file ต่อ user ที่ derive ชื่อจาก hash ของ stable user ID หรือใช้ partition key ที่บังคับทุก query อย่างเคร่งครัด

เกณฑ์เลือก:

- DB ต่อ user: isolation ชัด, ล้าง/สลับง่าย แต่ migration และพื้นที่หลายบัญชีมากขึ้น
- DB เดียวมี owner column: แชร์ master data ได้ แต่เสี่ยง query ลืม filter และข้อมูลข้ามผู้ใช้

สำหรับข้อมูลลูกค้าเชิงธุรกิจและความเสี่ยงจาก token ใหม่ส่งงานคนเก่า แนะนำ DB/outbox ต่อ user ส่วน master data ที่ไม่อ่อนไหวอาจแยกเป็น shared cache

ต้องมี account cleanup UI ที่แสดงว่าบัญชีใดมี pending ก่อนลบ local profile

## 26. Retention policy และ cache eviction

Server ยังคงเป็น archive ฉบับเต็ม การ cleanup ระยะนี้เป็นการเอา cache ออกจากมือถือ ไม่ใช่ลบข้อมูลธุรกิจ

กฎบังคับ:

- ไม่ลบ pending, rejected, conflict, pinned หรือ attachment ที่ยังไม่ส่ง
- purge ได้เฉพาะแถว synced และอยู่นอก working set
- purge เป็น transaction ตาม dependency/FK
- รันหลัง sync สำเร็จและควรอยู่ใน maintenance worker
- หลังลบจำนวนมาก วัดก่อนว่าจะต้องใช้ database maintenance ใด ห้าม VACUUM บนเส้นทางเปิดแอป

นโยบายเริ่มต้นเพื่อให้ฝ่ายธุรกิจอนุมัติ:

- customer/contact ที่อยู่ใน scope ปัจจุบัน: เก็บ local ทั้งหมดหากขนาดยังอยู่ใน budget
- active projects: เก็บทั้งหมด
- closed/lost projects: เก็บย้อนหลังตามช่วงรายงาน offline เช่น 12 เดือน
- future appointments: เก็บทั้งหมด
- past appointments/results: เก็บตามช่วงรายงาน offline เช่น 6–12 เดือน
- result history เก่า: โหลดแบบ on-demand เมื่อ online
- รูปที่ upload แล้ว: เก็บ URL/thumbnail ตาม budget; full image อ่านจาก server
- report/camera cache: ลบตามอายุและขนาด แต่ไม่แตะ durable attachment outbox

ตัวเลขเดือนและ storage budget ต้องมาจาก requirement ธุรกิจและการวัดเครื่องจริงก่อน implement

## 27. Backend pagination และ query correctness

- ใช้ keyset pagination ไม่ใช้ offset สำหรับตารางใหญ่
- ทุก endpoint ที่มี limit ต้องมี deterministic order พร้อม primary key tie-breaker
- ห้ามถือว่า response ที่ชน limit คือ “ข้อมูลทั้งหมด” แล้วลบ local rows ที่ไม่อยู่ใน response
- ตรวจ PostgreSQL indexes ตาม owner/scope + cursor/order
- รูปและความสัมพันธ์โหลดตาม parent IDs ของ page ไม่โหลดประวัติทั้งหมดทุกครั้ง

## 28. Observability ระยะที่ 3

Metrics สำคัญ:

- pending count และ oldest pending age
- success/retry/rejection/conflict rate
- sync duration และ bytes แยก upload/download
- duplicate prevented by idempotency
- cursor lag และ bootstrap frequency
- database size/row count/cache eviction
- startup TTID/TTFU และ jank ขณะ sync

Alert ฝั่ง server:

- rejection/5xx เพิ่มผิดปกติ
- cursor lag สูงต่อเนื่อง
- idempotency conflict
- change-log write failure
- endpoint latency และ connection pool saturation

## 29. การทดสอบระยะที่ 3

- snapshot พร้อมกับมี create/update/delete เกิดระหว่าง download แล้วสุดท้ายข้อมูลตรง server
- process death ก่อนและหลัง cursor transaction
- tombstone ชนกับ local pending edit
- cursor expired แล้ว bootstrap ใหม่โดย outbox ไม่หาย
- client รุ่นเก่ายังใช้ endpoint เดิมได้
- retention ไม่ลบ unsynced/rejected/conflict/pinned rows
- สลับหลายบัญชีแล้วไม่มีข้อมูลข้าม user
- ข้อมูลเกิน 5,000/50,000 รายการไม่หายและลำดับคงที่

## 30. Rollout และ rollback ระยะที่ 3

- ปล่อย backend change log และ `/sync/v2` ก่อน โดยยังไม่เปิดใช้จาก Android
- ตรวจ shadow comparison ระหว่าง full response กับ snapshot+delta ใน test/pilot
- เปิดใช้ผ่าน capability flag ต่อกลุ่มผู้ใช้
- Android fallback full sync ได้เมื่อ v2 ไม่พร้อม แต่ full sync ต้องรักษา unsynced rows
- ห้ามลบ change log/endpoint เดิมจน client รุ่นเก่าหมดจากกลุ่มใช้งาน
- retention เปิดหลัง delta sync เสถียร และเริ่มแบบ dry-run รายงานว่าจะลบอะไรโดยยังไม่ลบจริง

---

# 31. ตารางตรวจเช็กลิสต์ที่ร้องขอ

| เช็กลิสต์ | ครอบคลุมหรือไม่ | ระยะ | วิธีรับมือและหลักฐานที่ต้องทดสอบ |
|---|---|---|---|
| Event เปิดแอปใหม่แล้ว upload ต้องไม่ทำให้รอนาน | ครอบคลุม | ระยะ 1 เป็นหลัก; ระยะ 2–3 ลดภาระเพิ่ม | UI อ่าน Room ทันที, `onResume` enqueue work เท่านั้น, Login ไม่รอ full download, Worker ทำงานแบบ bounded; Macrobenchmark ขณะมีคิวใหญ่ |
| แสดงข้อมูลที่ยังไม่ sync หลังจบวัน | ครอบคลุม | ระยะ 1 | Sync Status, Home banner และ local notification หลัง 22:00 ตามเวลาไทย โดยถือ 00:00 เป็นวันใหม่ |
| ทำให้ user เห็นว่ากำลังซิงค์ข้อมูล | ครอบคลุม | ระยะ 1 | สถานะกลาง + Home banner + Settings; จำนวน pending/rejected อ่านจาก Room และเวลาสำเร็จล่าสุดเก็บแบบ persistent |
| เพิ่ม log/error handling | ครอบคลุมทุกระยะ | ระยะ 1 วางมาตรฐาน, ระยะ 2–3 เพิ่มรายละเอียด | Error taxonomy, structured logs, run/request ID, sanitize PII, backend StatusPages, metrics และ diagnostics แบบจำกัดอายุ |

สรุป: เช็กลิสต์ทั้ง 4 ข้อถูกครอบคลุมทั้งหมด และ product decision เรื่องจบวัน/การแจ้งเตือนได้รับการยืนยันแล้ว

# 32. ประเด็นเพิ่มเติมที่พบและแผนเดิมต้องครอบคลุม

| ประเด็นเพิ่มเติม | ความเสี่ยงถ้าไม่แก้ | หลักการแก้ | ระยะ |
|---|---|---|---|
| Worker รายงาน success ทั้งที่แถวยังส่งไม่ผ่าน | งานไม่เข้า backoff และค้างจนมี trigger ใหม่ | aggregate outcome และคืน retry ตาม temporary failure | 1 |
| Login รอ full sync | เวลา Login ขึ้นกับ network/จำนวนข้อมูล | authenticate แล้ว navigate; initial download เป็น worker | 1 |
| เปลี่ยนผู้ใช้แล้วข้อมูล offline ของคนเดิมอาจถูกล้างหรือส่งด้วย token คนใหม่ | ข้อมูลหาย/เจ้าของผิด/ข้อมูลรั่ว | บล็อก switch เมื่อ pending; ระยะยาวแยก storage ต่อ user | 1 และ 3 |
| POST timeout แล้ว retry อาจสร้างข้อมูลซ้ำ | ลูกค้า/โครงการ/นัดหมายซ้ำ | idempotency operation ID + unique constraint | 2 |
| รูปที่ upload ไม่ผ่านยังไม่เป็น durable outbox | process death/cache cleanup ทำรูปหาย | persistent attachment outbox และแยก upload/attach step | 2 |
| รายการใหญ่โหลดทั้งหมดใน RAM | ช้าและใช้ RAM บนเครื่องสเปกต่ำ | Paging, SQL filter/sort, aggregate queries | 2 |
| query หลักขาด index ที่ตรงรูปแบบค้นหา | table scan โตตามจำนวนข้อมูล | วัด query plan แล้วเพิ่ม composite indexes | 2 |
| endpoint limit 5,000 และบาง query ไม่มี deterministic order | ข้อมูลเก่าหายจาก local cacheหรือได้ชุดไม่แน่นอน | keyset pagination, stable order, ห้าม clear จาก partial response | 3 |
| full refresh ดาวน์โหลดข้อมูล/รูปซ้ำ | เปลืองเวลา เน็ต แบต และ Room writes | snapshot + delta cursor + parent-scoped photo fetch | 3 |
| ไม่มี deletion feed | local อาจคงข้อมูลที่ server ลบแล้ว | tombstone ใน change feed | 3 |
| local DB/cache โตระยะยาว | query, RAM และพื้นที่แย่ลง | retention เฉพาะ synced working set + cache budget | 3 |
| การแก้พร้อมกันหลายเครื่อง | การแก้ล่าสุดอาจทับกันเงียบ | server revision/ETag และ conflict policy ต่อ entity | 3 |
| log ปัจจุบันกระจัดกระจายและบางจุด swallow exception | วิเคราะห์ incident ยาก | centralized error mapping, correlation ID, bounded diagnostics | 1–3 |

# 33. Decisions และข้อสรุปก่อนเริ่มแก้จริง

1. **ยืนยันแล้ว:** ตัดวันเที่ยงคืนตาม `Asia/Bangkok`; เริ่มเตือน 22:00 และออกแบบให้เปลี่ยนค่าได้ภายหลัง
2. **ยืนยันแล้ว:** ใช้ทั้ง banner ในแอปและ local notification
3. มือถือหนึ่งเครื่องอนุญาตให้หลายบัญชีสลับใช้หรือไม่
4. ถ้ามีงาน offline ของบัญชีเดิม จะอนุญาต “ทิ้งข้อมูลแล้วเปลี่ยนบัญชี” หรือบล็อกเสมอ
5. ต้องดูประวัตินัดหมาย/ผลการขายแบบ offline ย้อนหลังกี่เดือน
6. **ยืนยันแล้วสำหรับระยะ 2:** หลัง upload และผูกข้อมูลสำเร็จ เก็บเพียง URL ใน Room แล้วลบไฟล์ถาวรในเครื่อง; ผู้ใช้ไม่จำเป็นต้องดูรูปที่สำเร็จแล้วขณะ offline ให้แสดง placeholder แทน และไม่รับประกัน cache/thumbnail แบบ offline
7. **ยืนยันแล้วสำหรับระยะ 1:** ใช้ local bounded diagnostics ที่ export เป็น JSON ได้และ server logs; ยังไม่ผูกผู้ให้บริการภายนอก ไฟล์ต้องไม่รวม token/password/payload/ชื่อ/เบอร์โทร/พิกัดหรือ ID รายการจริง

ข้อสรุปเพิ่มเติมเรื่องเปลี่ยนบัญชี: แม้เกิดไม่บ่อย ต้องบล็อกเมื่อพบ pending/rejected ของบัญชีก่อนหน้า แสดงสรุปรายการ และให้ผู้ใช้ยืนยันชัดเจนก่อนลบข้อมูลที่กู้คืนไม่ได้
8. **ข้อมูลเบื้องต้น:** ผู้ใช้รวมประมาณ 50 คน ส่วนใหญ่ Android 12–13 แต่สเปกไม่แน่นอน; ใช้ Android 12/API 31, RAM 4 GB และข้อมูล 5,000 แถวเป็น acceptance baseline ชั่วคราว พร้อม 50,000 แถวเป็น stress test จนกว่าจะได้ข้อมูลเครื่องจริง

# 34. Definition of Done รวมทั้งโครงการ

ถือว่า roadmap เสร็จเมื่อ:

- ผู้ใช้เข้า first usable screen ได้โดยไม่ await network
- ทุก business write สำคัญรอดจาก offline, process death และ reboot
- retry ไม่สร้างข้อมูลซ้ำ
- ผู้ใช้ตรวจ pending/running/rejected/conflict ได้
- งานค้างหลังจบวันถูกแจ้งตามกฎที่อนุมัติ
- download ใช้ cursor/page และรองรับ deletion โดยไม่ทำ unsynced data หาย
- หน้ารายการใหญ่ใช้ Paging/SQL query และผ่าน performance budget บนเครื่องเป้าหมาย
- retention ลบเฉพาะ cache ที่ sync แล้ว และ server history ยังครบ
- ไม่มีข้อมูลข้าม user
- logs ช่วยตาม incident ด้วย run/request ID โดยไม่เปิดเผยข้อมูลสำคัญ
- Android/backend migration, unit, integration, process-death, backward-compatibility และ rollback tests ผ่าน

# 35. ลำดับลงมือเมื่อได้รับอนุมัติ

1. เก็บ baseline tests/metrics โดยยังไม่เปลี่ยน behavior
2. เพิ่ม error/result models และ tests
3. แก้ Worker outcome และ scheduler
4. เพิ่ม SyncCoordinator/UI/EOD pending notification
5. แยก Login ออกจาก initial download พร้อม account-switch guard
6. Pilot ระยะที่ 1 และเก็บผล
7. เพิ่ม index/Paging ทีละหน้า
8. เพิ่ม backend idempotency แล้วจึงเปิด Android operation ID
9. ทำ durable photo outbox และ batch/bounded concurrency
10. Pilot ระยะที่ 2
11. เพิ่ม backend change feed และ sync v2
12. shadow compare full sync กับ delta sync
13. เปิด delta sync แบบกลุ่มเล็ก พร้อม fallback
14. เปิด retention แบบ dry-run ก่อนลบจริง
15. ปิดโครงการหลัง metrics และ recovery drill ผ่าน
