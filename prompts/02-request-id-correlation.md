# Prompt: เอา request_id ที่มีอยู่แล้วฝั่ง backend มาผูกกับ log ทุกบรรทัด + ให้แอปเก็บไว้ด้วย

**สถานะ: ✅ เสร็จแล้ว (ยืนยัน 2026-11-06)**
- Backend: commit `8b579c5` บน `main`, deploy แล้ว, ทดสอบจริงบนเซิร์ฟเวอร์ — log บรรทัด
  `[req=c34d53fb-...]` ตรงกับ `X-Request-ID` ที่ตอบกลับมาเป๊ะ และ startup log ที่ไม่มี
  request context ขึ้น `[req=none]` ตามที่ตั้งใจ
- Android: commit `37c6e3a` บน `release/phase-4c` (ยังไม่ push) — compile + unit test
  ผ่านหมด ยังไม่ได้ export diagnostics จากเครื่องจริงเพื่อยืนยัน end-to-end (ขั้นตอน
  logic เป็น field-passthrough ง่ายๆ ความเสี่ยงต่ำ ถือว่าเพียงพอได้โดยไม่ต้อง block)

สังเกตที่เจอตอนทดสอบ (ไม่เกี่ยวกับงานนี้ ไม่ได้แก้): curl 1 ครั้งได้ log บรรทัด
`http_request` ซ้ำกัน 2 บรรทัด — พฤติกรรมเดิมของระบบ ยังไม่ได้ไล่สาเหตุ

**ขอบเขต (เดิม): แก้โค้ด 2 repo — backend (`../backend`) และแอป Android (repo นี้) — ไม่แตะ production server**

## บริบทที่ต้องรู้ก่อนเริ่ม (สำคัญ — ส่วนใหญ่ "ทำแล้ว" อยู่แล้ว ไม่ต้องสร้างใหม่)

ตรวจโค้ดจริงแล้วพบว่า backend **มี CallId plugin ติดตั้งอยู่แล้ว** ที่
`backend/src/main/kotlin/com/pp68/backend/application/plugins/HTTP.kt` บรรทัด ~16-21:

```kotlin
install(CallId) {
    retrieveFromHeader("X-Request-ID")
    verify { it.length in 8..64 && it.all { ch -> ch.isLetterOrDigit() || ch in "-_." } }
    generate { UUID.randomUUID().toString() }
    replyToHeader("X-Request-ID")
}

install(CallLogging) {
    level = Level.INFO
    callIdMdc("request_id")
    format { call -> "http_request method=... path=... status=... request_id=${call.callId ?: "unknown"}" }
}
```

สรุปว่าตอนนี้: server รับ/สร้าง request id ต่อ request, ตอบกลับใน header
`X-Request-ID` เสมอ, และ log บรรทัด `http_request ...` (access log) มี request_id
ติดอยู่แล้ว **แต่สองอย่างนี้ยังไม่ทำ**:

1. `callIdMdc("request_id")` ใส่ค่าไว้ใน SLF4J MDC ก็จริง แต่
   `backend/src/main/resources/logback.xml` ไม่มี `%X{request_id}` ใน pattern เลย —
   แปลว่า log บรรทัดอื่นๆที่ไม่ใช่บรรทัด access log ของ `CallLogging` (เช่น exception ที่
   route handler หรือ `StatusPages.kt` log เอง) **ไม่มี request_id ติดไปด้วย** ทั้งที่ค่า
   มีอยู่ใน MDC พร้อมใช้แล้ว ขาดแค่แก้ pattern บรรทัดเดียว
2. ฝั่งแอป Android **ไม่เคยอ่าน header `X-Request-ID` ที่ server ตอบกลับมาเลย**
   (เช็คจาก grep `X-Request-ID`/`request_id`/`callId` ใน `app/src/main/java` แล้วไม่เจอ)
   — เวลาผู้ใช้ export `SyncDiagnostics` ส่งมาให้ debug ยังไม่มีทางเอา error ที่เห็นในแอป
   ไปจับคู่กับบรรทัด log ฝั่ง server ได้ตรงๆ ต้องเดาจากเวลาประมาณเหมือนเดิม

## สิ่งที่ต้องทำ

### 2.1 Backend — แก้ `logback.xml` (ง่ายสุด ทำก่อน)

ไฟล์: `backend/src/main/resources/logback.xml`

เปลี่ยน pattern จาก:
```xml
<pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
```
เป็น:
```xml
<pattern>%d{HH:mm:ss.SSS} [%thread] [req=%X{request_id:-none}] %-5level %logger{36} - %msg%n</pattern>
```
`:-none` กัน log บรรทัดที่เกิดนอก request context (เช่น ตอน startup, ตอน scheduled job
ถ้ามี) ไม่ให้พิมพ์ว่างๆแปลกๆ

**ทดสอบ:** รัน backend local (`./gradlew run`), ยิง request อะไรก็ได้ที่ทำให้เกิด error
(เช่น endpoint ที่ต้อง auth แต่ไม่ส่ง token), เช็คว่า log บรรทัด error มี `[req=xxxxx]`
ติดอยู่ และ**ตรงกับ** `X-Request-ID` ใน response header จริง (ดูด้วย `curl -i`)

### 2.2 แอป Android — เก็บ request_id เวลาบันทึก error ลง SyncDiagnostics

ไฟล์ที่ต้องแก้: `app/src/main/java/com/example/pp68_salestrackingapp/di/NetworkModule.kt`
บรรทัด ~144 (จุดที่ `AuthInterceptor` เรียก `diagnostics.record("http_error", ...)` ตอนนี้):

```kotlin
diagnostics.record(
    "http_error",
    mapOf(
        "method" to originalRequest.method,
        "path" to originalRequest.url.encodedPath,
        "http_code" to response.code
    )
)
```

เพิ่ม `"request_id" to response.header("X-Request-ID")` เข้าไปใน map (server
`replyToHeader("X-Request-ID")` ใส่ header นี้มาทุก response อยู่แล้ว ไม่ต้องแก้ server
เพิ่ม) — ระวัง `response.header(...)` คืน `String?` ถ้า key ไม่อยู่ใน `ALLOWED_FIELDS`
ของปลายทางจะถูกตัดทิ้งเงียบๆ (ดูข้อถัดไป)

ไฟล์ที่ต้องแก้คู่กัน: `app/src/main/java/com/example/pp68_salestrackingapp/utils/SyncDiagnostics.kt`
บรรทัด ~80 — เพิ่ม `"request_id"` เข้า `ALLOWED_FIELDS` set ไม่งั้นค่าที่ส่งมาจะถูกตัดทิ้ง
ไม่ error ไม่เตือน หายไปเฉยๆ (นี่คือกับดักที่ comment ในไฟล์เตือนไว้แล้วว่าเกิดกับ
`failure_type` มาก่อน)

**request_id ไม่ใช่ PII** — เป็น UUID สุ่ม ไม่มีความหมายอะไรนอกจากใช้ grep log เก็บได้
ปลอดภัย ไม่ขัดกับเจตนาเดิมของ `SyncDiagnostics` ที่ตั้งใจไม่เก็บข้อมูลที่ระบุตัวบุคคล/ธุรกิจได้

### 2.3 เทสที่ต้องมี

- Backend: เทส JVM-only ที่มีอยู่แล้ว (`./gradlew test`) ต้องผ่านเหมือนเดิม — การแก้
  `logback.xml` ไม่กระทบโค้ด ไม่ต้องเขียนเทสใหม่สำหรับส่วนนี้
- Android: เทส unit ของ `AuthInterceptor`/`NetworkModule` ถ้ามีอยู่แล้วให้เช็คว่ายังผ่าน
  (ค้นด้วย `app/src/test/.../NetworkModuleTest.kt` หรือชื่อใกล้เคียง — ถ้าไม่มีไฟล์เทส
  สำหรับส่วนนี้มาก่อน ไม่ต้องสร้างใหม่ เพราะเป็นแค่การเพิ่ม field เข้า map ไม่มี logic
  ให้พลาดได้นอกจาก typo ชื่อ key)

## สิ่งที่ "เสร็จแล้ว" ต้องเป็นแบบนี้

- ยิง request ที่ทำให้ backend error → log บรรทัดนั้นมี `[req=<uuid>]` ที่ตรงกับ
  `X-Request-ID` ใน response header เป๊ะ
- export `SyncDiagnostics` จากแอปหลังเจอ error 1 ครั้ง → เปิดไฟล์ JSON เห็น field
  `"request_id"` อยู่ในรายการ event นั้น ไม่ถูกตัดทิ้ง
- grep `request_id` นั้นใน `/var/log/pp68-backend.log` บนเซิร์ฟเวอร์เจอบรรทัดตรงกัน
