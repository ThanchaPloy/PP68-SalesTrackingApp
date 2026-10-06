# แผนปรับปรุงการบังคับตั้งค่าบัญชี กฎนัดหมาย และฉบับร่างหลายรายการ

เอกสารนี้ต่อยอดจาก Phase 3B แต่แยกงานออกมาเพื่อให้ deploy/rollback ได้อิสระ และไม่ทำให้ระบบ snapshot, delta sync, conflict และ TEMP ID mapping ที่ผ่าน regression แล้วเปลี่ยนพร้อมกันทั้งหมด

## สถานะการทำจริง (ปรับล่าสุด 6 ตุลาคม 2026)

**เขียนโค้ดครบทั้ง 4A, 4B, 4C แล้ว ยังไม่ผ่านการทดสอบบนเครื่องจริงและยังไม่เปิดใช้งาน**

| เฟส | ขั้น | สถานะ | ที่อยู่ |
|---|---|---|---|
| 4A | 1-4 นโยบาย, migration, API, setup gate, tests | เสร็จ | backend `main` (`40f0fa4`), Android `release/phase-4a` |
| | 5-6 staging, เปิด flag ให้บัญชี active | **ยังไม่ทำ** | รัน `oneoff_enable_required_account_setup.sql` ด้วยมือหลังแจก APK |
| 4B | 1-4 policy สองฝั่ง, guards, time anchor | เสร็จ | backend `feature/appointment-policy`, Android `feature/appointment-policy` |
| | 5 เปิด `APPOINTMENT_POLICY_V2` | **ยังไม่ทำ** | ต้องรอ APK ที่มี 4B ออกก่อน |
| 4C | 1-4 Room, repository, UI, account isolation | เสร็จ | Android `feature/appointment-policy` |

### สิ่งที่ทำต่างจากแผน และเหตุผล

- **Room migration เป็น 62→63 ไม่ใช่ 61→62** ตามที่หัวข้อ C.1 เขียนไว้ เพราะ 62 ถูกใช้ไปกับงาน B.4 (เก็บเวลาที่กดแก้แผนลงแถว `activity_table`) ก่อน
- **B.4 ข้อ 1 ไม่ได้เพิ่มฟิลด์ `server_time` ในทุก response** แต่อ่าน header `Date` ที่ทุก HTTP response มีอยู่แล้ว ได้ผลเท่ากันโดยไม่ต้องแก้ contract ของทุก endpoint
- **เพิ่มรหัสเหตุผล `APPOINTMENT_STATUS_LOCKED`** นอกเหนือจาก 5 ตัวที่หัวข้อ B.3 แนะนำ เพราะ "นัดถูกยกเลิก/บันทึกผลแล้ว/ขาดนัด" คนละเรื่องกับ "หมดเวลาแก้" และผู้ใช้ต้องได้เหตุผลที่ตรง
- **ฝั่ง server แยก "การแก้แผน" ออกจาก PATCH อื่นด้วยรายชื่อฟิลด์** ไม่ใช่ flag จาก client เพราะ check-in, การจบนัด และการผูกโครงการตอนบันทึกผล ใช้ `PATCH /appointment` เส้นเดียวกันและเกิดหลังเวลาเริ่มนัดเสมอ
- **เพิ่มงานนอกแผน**: periodic sync รอบ 6 ชั่วโมงเป็นตาข่ายรองรับ outbox (เดิมพึ่ง one-time work ที่ผูกกับการบันทึกอย่างเดียว)

### ที่เหลือของหัวข้อ 8 (test plan)

- ยังไม่ได้รัน instrumented suite เลย รวมถึง migration test ของ 62 และ 63 ที่เขียนไว้แล้ว
- 8.2 ยังขาดเคส deep link เปิดฟอร์มก่อน cutoff แล้วกด save หลัง cutoff และเคส rejected sync ไม่วน retry
- 8.3 ยังขาดเคสที่ต้องใช้ Room จริง: สร้างครบ 20 ร่างแล้วไม่เขียนทับกัน, boundary 29/30 วัน, process death แล้วร่างยังอยู่
- 8.4 regression gate ยังไม่ได้ทำทั้งหัวข้อ

## 1. ข้อกำหนดที่ยืนยันแล้ว

### 1.1 การตั้งค่าบัญชีครั้งถัดไป

- ทุกบัญชีต้องเปลี่ยนรหัสผ่านในการเข้าแอปครั้งถัดไป เพราะปัจจุบันใช้รหัสเริ่มต้นเดียวกัน
- บัญชีที่ไม่มีเบอร์มือถือเท่านั้นที่ต้องกรอกเบอร์มือถือ
- หน้าบังคับต้องแสดงข้อความตามที่ยืนยันแล้วสองบรรทัด โดยไม่เติมข้อความอื่น:
  - `กรุณากรอกเบอร์มือถือสำหรับใช้ระบบนี้`
  - `บริษัทจะเก็บและใช้เบอร์มือถือของท่านเพื่อการดำเนินงานภายใน Sales Tracking App`
- อุปกรณ์ที่ใช้งานเป็นโทรศัพท์ของบริษัทซึ่งให้พนักงานยืมใช้
- ผู้ใช้ต้องไม่สามารถปิด dialog, กดย้อนกลับ หรือเข้าเมนูธุรกิจเพื่อข้ามขั้นตอนนี้ได้

### 1.2 กฎนัดหมาย

- ก่อนถึงวันนัด: แก้ไขและลบแผนได้ เช่น นัดพรุ่งนี้ คืนนี้ยังแก้ได้
- ในวันนัดตามเขตเวลา `Asia/Bangkok`: แก้ไขได้เฉพาะก่อนถึงเวลาเริ่มนัด
- ตั้งแต่ `00:00` ของวันนัด: ห้ามลบ แม้ยังไม่ถึงเวลาเริ่มนัด
- ตั้งแต่เวลาเริ่มนัดเป็นต้นไป: ห้ามแก้ไข
- หลังวันนัด: ห้ามแก้ไขและห้ามลบ
- On-site ที่พ้นวันนัดและไม่มี Check-in ถือว่า `ขาดนัด` และห้ามบันทึกผล
- Online/Call ไม่ต้อง Check-in และยังบันทึกผลย้อนหลังได้
- การแก้ไขที่เกิดขึ้นตอน offline ก่อนปิดช่วงแก้ไขต้องได้รับการยอมรับ แม้จะ sync หลังปิดช่วงแล้ว

### 1.3 ฉบับร่างนัดหมาย

- สร้างและเก็บ Draft ของนัดหมายใหม่ได้หลายรายการ
- Draft หมดอายุหลัง 30 วัน
- เก็บได้สูงสุด 20 รายการต่อบัญชี
- Draft เป็นข้อมูล local-only ไม่ส่งขึ้น backend
- ลบ Draft ของบัญชีนั้นเมื่อผู้ใช้ยืนยัน logout หรือเปลี่ยนบัญชี

## 2. จุดที่ต้องยืนยันก่อนเริ่มเขียนโค้ด

Decision ที่ยืนยันแล้ว:

1. ในวันนัดก่อนถึงเวลาเริ่มนัดแก้ไขได้อย่างเดียว แต่ห้ามลบตั้งแต่ `00:00`
2. รหัสผ่านใหม่ขั้นต่ำ 8 ตัวอักษร รองรับ passphrase/Unicode/ช่องว่าง ไม่บังคับผสมตัวพิมพ์ใหญ่ ตัวเลข และอักขระพิเศษ แต่ต้องบล็อกรหัสที่พบบ่อย รหัสเริ่มต้นเดิม รหัสพนักงาน และรหัสที่รั่วไหล พร้อม rate limiting ฝั่ง backend

3. ข้อความแจ้งการเก็บและใช้เบอร์มือถือยืนยันแล้วเป็น `กรุณากรอกเบอร์มือถือสำหรับใช้ระบบนี้` และ `บริษัทจะเก็บและใช้เบอร์มือถือของท่านเพื่อการดำเนินงานภายใน Sales Tracking App` โดยอุปกรณ์เป็นโทรศัพท์ของบริษัทที่ให้พนักงานยืมใช้

**Decision gates ทั้ง 3 ข้อได้รับการยืนยันครบแล้ว**

หมายเหตุด้านความเสี่ยง: ขั้นต่ำ 8 ตัวเป็น requirement ที่ยืนยันสำหรับระบบนี้ แต่ต่ำกว่าคำแนะนำ NIST ปัจจุบันสำหรับ password ที่เป็น single factor ซึ่งแนะนำขั้นต่ำ 15 ตัว จึงต้องชดเชยด้วย blocklist, login throttling, bcrypt และการยกเลิก shared default password

## 3. Baseline ที่ตรวจพบในระบบปัจจุบัน

### Android

- `HomeViewModel` ตรวจ `phone_number` จาก `/user` และ `HomeScreen` แสดงเพียงการ์ดเตือนที่กดไปหน้าแก้ไขโปรไฟล์ได้ ผู้ใช้ยังทำงานอื่นต่อได้ จึงยังไม่ใช่ forced dialog
- มีหน้า `ChangePasswordScreen` และ endpoint เปลี่ยนรหัสอยู่แล้ว แต่ไม่ได้มีสถานะจาก server ว่าบัญชีใดต้องเปลี่ยน และกฎขั้นต่ำอยู่ใน Android เพียง 6 ตัวอักษร
- `AppointmentStatus.isEditLocked()` ล็อกนัดสถานะ planned ตั้งแต่เหลือ 0–7 วัน ซึ่งไม่ตรง requirement ใหม่
- สถานะ `missing` ปัจจุบันยังเปิดปุ่มบันทึกผล และข้อความบนหน้ารายละเอียดระบุว่าบันทึกย้อนหลังได้ ซึ่งไม่ตรงกฎ On-site ใหม่
- `DraftStore` ใช้ SharedPreferences และ key ของนัดหมายใหม่ เช่น `create_appointment:new:none` จึงมี Draft ได้หนึ่งรายการต่อบริบทและสามารถเขียนทับกันได้
- Draft เดิมยังไม่ได้แยกเจ้าของด้วย account key จึงต้องแก้ก่อนรองรับหลายบัญชีอย่างปลอดภัย

### Backend

- `/login-api` ยังไม่คืน `password_change_required`, `phone_required` หรือสถานะ setup
- `/change-password-api` ตรวจ current password และใช้ bcrypt แล้ว แต่ backend ยังไม่ตรวจความยาว/รหัสพบบ่อย และยังไม่มี transaction ที่อัปเดตรหัสผ่าน เบอร์มือถือ และการรับทราบ notice พร้อมกัน
- `/user` PATCH แก้เบอร์ได้ แต่เป็น endpoint แยก จึงเกิด partial success ได้ เช่น เปลี่ยนรหัสสำเร็จแต่บันทึกเบอร์ล้มเหลว
- PATCH/DELETE `/appointment` และ POST `/activity_result` ตรวจสิทธิ์เจ้าของแล้ว แต่ยังไม่ตรวจช่วงเวลาที่แก้ไข/ลบหรือกฎขาดนัด

## 4. หลักการออกแบบร่วม

1. **Backend เป็นผู้ตัดสินสุดท้าย** — Android ซ่อน/disable ปุ่มเพื่อ UX แต่ห้ามพึ่ง UI เป็น security boundary
2. **กฎเดียว มี test matrix เดียว** — สร้าง `AppointmentPolicy` ทั้ง Android และ backend ด้วยตารางกรณีทดสอบชุดเดียว ลด logic drift
3. **เวลาไทยอย่างชัดเจน** — ใช้ `ZoneId.of("Asia/Bangkok")` และ inject `Clock`; ห้ามกระจาย `LocalDate.now()`/`LocalDateTime.now()` โดยไม่มี zone ในหลายไฟล์
4. **Atomic account setup** — รหัสผ่าน เบอร์ที่จำเป็น และ notice acknowledgement ต้องสำเร็จหรือ rollback พร้อมกัน
5. **Fail closed เฉพาะเรื่องสิทธิ์/บัญชี** — หาก server ตอบสถานะ setup ไม่ได้ ห้ามตีความว่า setup สำเร็จ แต่ต้องแสดง retry/logout ไม่ค้างเป็นหน้าขาว
6. **Draft ไม่ใช่ outbox** — Draft ยังไม่ใช่ข้อมูลธุรกิจที่ผู้ใช้ยืนยันบันทึก จึงไม่เข้า sync pending, conflict หรือ notification หลัง 22:00
7. **ไม่ log PII/secret** — ห้าม log password, token, phone, privacy payload หรือเนื้อหา Draft; diagnostics เก็บเพียง error code/request ID

## 5. งาน A — Forced initial account setup

### A.0 ปิดความเสี่ยงจากรหัสเริ่มต้นร่วมกันก่อน

Forced change หลัง login อย่างเดียวไม่ป้องกันการยึดบัญชี: ผู้ที่รู้รหัสเริ่มต้นร่วมและเดารหัสพนักงานได้อาจ login แล้วเปลี่ยนรหัสก่อนเจ้าของจริง

ก่อน production ต้องเลือกอย่างน้อยหนึ่งวิธี:

- แนะนำ: สร้างรหัสเริ่มต้น/activation code แบบสุ่มไม่ซ้ำรายบัญชีและส่งให้เจ้าของผ่านช่องทางภายในที่ยืนยันตัวตนแล้ว
- หรือให้ผู้ดูแลยืนยันตัวตนและเปิดสิทธิ์ reset เป็นรายบัญชีในช่วง rollout
- จำกัด rate ของ login และบันทึก audit ของ login/setup failure โดยไม่เก็บ password

ห้ามถือว่าการใส่ forced dialog เพียงอย่างเดียวแก้ความเสี่ยงของ shared password แล้ว

### A.1 PostgreSQL migration

เพิ่ม migration แบบ additive เช่น `add_required_account_setup.sql`:

- `password_change_required BOOLEAN NOT NULL DEFAULT FALSE`
- `password_changed_at TIMESTAMPTZ NULL`
- `phone_notice_version VARCHAR(...) NULL`
- `phone_notice_acknowledged_at TIMESTAMPTZ NULL`
- `credential_version INTEGER NOT NULL DEFAULT 1` สำหรับยกเลิก token เก่าหลังเปลี่ยนรหัส

ลำดับ migration ที่ปลอดภัย:

1. เพิ่ม column ด้วย default ที่ยังไม่บังคับ เพื่อให้ backend/Android รุ่นปัจจุบันทำงานต่อได้
2. deploy backend ที่อ่าน/เขียน field ใหม่และรองรับ restricted setup token
3. deploy Android รุ่นที่มี setup gate
4. ตรวจ telemetry/login ของรุ่นใหม่
5. จึงรันคำสั่งแยกเพื่อ set `password_change_required = TRUE` ให้บัญชี active ทั้งหมด

อย่าผูกการ set ทุกบัญชีเป็น TRUE ไว้ใน startup migration เดียวกับการเพิ่ม column เพราะ rollback Android จะทำให้ผู้ใช้ทุกคนติดหน้าใช้งานไม่ได้ทันที

### A.2 API contract

ปรับ `POST /login-api` ให้คืนข้อมูลเพิ่มโดยยัง backward-compatible:

- `setup_required`
- `password_change_required`
- `phone_required`
- `phone_notice_version`
- `server_time`
- token แบบ `setup_only` อายุสั้นเมื่อยัง setup ไม่ครบ

เพิ่ม `POST /account/complete-initial-setup` ภายใต้ JWT:

- รับ current password, new password, confirm/phone เฉพาะกรณีจำเป็น และ notice version
- ยืนยัน current password ซ้ำก่อนเปลี่ยน เพราะเป็น sensitive action
- ตรวจ password policy และ phone normalization ฝั่ง backend เสมอ
- อัปเดต password hash, phone, acknowledgement, flag และ credential version ใน PostgreSQL transaction เดียว
- คืน full JWT ใหม่หลัง commit สำเร็จ
- token `setup_only` เรียกได้เฉพาะ endpoint setup, status และ logout; business endpoint อื่นตอบ error code ที่แน่นอน

ตัวอย่าง error code ที่ Android ต้องรองรับ:

- `ACCOUNT_SETUP_REQUIRED`
- `CURRENT_PASSWORD_INVALID`
- `PASSWORD_TOO_WEAK`
- `PASSWORD_COMPROMISED`
- `PHONE_REQUIRED`
- `PHONE_INVALID`
- `PHONE_NOTICE_VERSION_MISMATCH`

### A.3 การจัดการ session

- Backend auth middleware ต้องตรวจ `credential_version` และ setup flag เพื่อกัน token เก่าหรือ Android รุ่นเก่าข้ามขั้นตอน
- หลังเปลี่ยนรหัสให้ revoke/ทำให้ token ก่อนหน้าใช้ไม่ได้ และออก token ใหม่
- Android ห้ามเก็บ current/new password ใน SharedPreferences, Room, SavedStateHandle, logs หรือ diagnostics
- เมื่อ process ตายกลาง setup ให้กลับไป login ใหม่ ไม่กู้ password จาก disk

### A.4 Android UX

เพิ่ม account setup gate ระหว่าง Login success กับ main NavGraph ไม่วาง logic ไว้เฉพาะ Home:

- แสดง full-screen/non-dismissible dialog หรือ dedicated setup screen
- ปิด back, outside tap และ navigation ไปหน้าอื่น
- ฟอร์มรหัสเดิม รหัสใหม่ ยืนยันรหัสใหม่ และเบอร์มือถือเฉพาะบัญชีที่ไม่มีเบอร์
- แสดงข้อความที่ยืนยันแล้วสองบรรทัดตามข้อ 1.1 โดยไม่เพิ่มข้อความอื่น
- ปุ่มบันทึก disabled ระหว่าง request ป้องกัน double submit
- network ล้มเหลวให้ retry ได้โดยข้อมูล password อยู่เฉพาะ memory ของหน้าปัจจุบัน และมีปุ่ม logout
- สำเร็จแล้วรับ token ใหม่ ล้าง password fields แล้วจึงเปิด main navigation

การ์ดเตือนเบอร์เดิมบน Home ควรคงเป็น fallback ชั่วคราวหนึ่ง release แล้วค่อยลบเมื่อยืนยันว่า account setup gate ทำงานครบ

### A.5 Phone normalization

- UI รับรูปแบบ `0XXXXXXXXX` และ `+66XXXXXXXXX`
- Backend normalize เป็นรูปแบบ canonical เดียวก่อนบันทึก และส่งค่าที่ mask แล้วใน log/response ที่ไม่จำเป็น
- ห้ามใช้เบอร์เป็น username, authorization decision หรือ account owner key
- unique constraint ของเบอร์ไม่ควรเพิ่มโดยอัตโนมัติ เพราะพนักงานอาจใช้เบอร์บริษัท/เบอร์ร่วมกัน ต้องมี requirement แยกก่อน

## 6. งาน B — Appointment policy ใหม่

### B.1 ตารางกฎที่ทั้งสองฝั่งต้องใช้ตรงกัน

| สถานการณ์ | แก้ไข | ลบ | บันทึกผล On-site | บันทึกผล Online/Call |
|---|---:|---:|---:|---:|
| ก่อนวันนัด | ได้ | ได้ | ใช้ flow ปัจจุบัน | ใช้ flow ปัจจุบัน |
| วันนัด ก่อนเวลาเริ่ม | ได้ | ไม่ได้ | ใช้ flow/check-in ปัจจุบัน | ได้ตาม flow ปัจจุบัน |
| วันนัด ตั้งแต่เวลาเริ่ม | ไม่ได้ | ไม่ได้ | ต้องผ่าน Check-in ตาม flow ปัจจุบัน | ได้ตาม flow ปัจจุบัน |
| หลังวันนัดและเคย Check-in | ไม่ได้ | ไม่ได้ | ได้ | ได้ |
| หลังวันนัด ไม่มี Check-in | ไม่ได้ | ไม่ได้ | ไม่ได้: ขาดนัด | ได้ |
| completed | ไม่ได้ | ไม่ได้ | ไม่สร้างผลซ้ำ; ใช้ version/edit-result flow เดิม | เช่นเดียวกัน |

นิยาม boundary:

- ใช้เวลาไทยจาก `planned_date + planned_time`
- `now < appointmentStart` แก้ได้
- `now >= appointmentStart` แก้ไม่ได้ ดังนั้นเวลาเท่ากับเวลานัดพอดีถือว่าปิดช่วงแก้ไขแล้ว
- แถวเก่าที่ไม่มี `planned_time` ให้ lock ตั้งแต่ `00:00` และรายงาน data-quality warning แทนการเดาเวลา
- `missing` เป็น effective status จาก On-site + พ้นวันนัด + ไม่มี check-in ไม่จำเป็นต้องเขียนทับ `plan_status` ทันที

### B.2 Android policy

แทนกฎ 0–7 วันใน `AppointmentStatus` ด้วย pure policy ที่รับ:

- activity type/status
- planned date/time
- check-in state
- `Clock` และ `ZoneId`

ใช้ policy เดียวกันที่:

- Home card/menu
- Activity detail ปุ่มแก้ไข/ลบ/check-in/result
- CreateAppointmentViewModel ตอนกดบันทึกการแก้ไข
- ActivityRepository ก่อนเขียน local update/delete
- SalesResultViewModel ก่อนสร้างผลจาก appointment

ต้องตรวจซ้ำที่ repository ไม่ใช่ซ่อนปุ่มอย่างเดียว เพื่อกัน deep link, stale screen และ race ข้ามเวลานัดขณะเปิดฟอร์มค้างอยู่

ข้อความ UX ต้องบอกเหตุผลตรงกัน เช่น:

- `เลยเวลาเริ่มนัดแล้ว จึงแก้ไขแผนไม่ได้`
- `นัด On-site นี้ขาดนัดและไม่มี Check-in จึงบันทึกผลย้อนหลังไม่ได้`

### B.3 Backend policy

เพิ่ม domain service `AppointmentPolicy` ที่ inject `Clock` และใช้ `Asia/Bangkok` แล้วเรียกจาก:

- PATCH `/appointment`
- DELETE `/appointment`
- POST/UPSERT `/activity_result`

ห้ามตรวจเฉพาะ route; use case ต้องตรวจ current appointment จากฐานข้อมูลภายใน transaction ก่อน mutation เพื่อกัน race ระหว่างอ่านกับเขียน

error code ที่แนะนำ:

- `APPOINTMENT_EDIT_WINDOW_CLOSED`
- `APPOINTMENT_DELETE_WINDOW_CLOSED`
- `ONSITE_CHECKIN_REQUIRED`
- `MISSED_ONSITE_RESULT_NOT_ALLOWED`
- `APPOINTMENT_TIME_MISSING`

Android sync classifier ต้องจัด business rejection เหล่านี้เป็น permanent rejection ที่ผู้ใช้เห็น ไม่ retry ไม่จบ และไม่เปลี่ยน local row เป็น synced

### B.4 Offline edit ที่ sync หลัง deadline

ข้อจำกัดพื้นฐาน: server ไม่สามารถพิสูจน์เวลาเกิดเหตุการณ์บนมือถือที่ offline ได้สมบูรณ์ เพราะผู้ใช้เปลี่ยนนาฬิกาเครื่องได้

แนวทางที่สมดุลกับ requirement:

1. ทุก authenticated response สำคัญคืน `server_time`; Android เก็บ server-time anchor และ offset โดยไม่ใช้เวลามือถือดิบเพียงอย่างเดียว
2. เมื่อแก้ appointment local ให้บันทึก `client_modified_at`, `server_time_anchor_at_edit` และ operation ID ใน outbox/row
3. Backend ยอมรับ delayed mutation เมื่อเวลาที่คำนวณจาก anchor อยู่ก่อน appointment start และ metadata ไม่ย้อนเวลา/ไม่อยู่อนาคตผิดปกติ
4. เก็บ audit เฉพาะ ID, actor, policy result, client/server timestamps และ request ID; ไม่เก็บ payload เต็ม
5. หากไม่มี time anchor ที่เชื่อถือได้ ให้ Android เตือนว่าการแก้ใกล้เวลานัดต้องเชื่อมต่อก่อน แทนการบอกว่าสำเร็จแน่นอน

ต้องมี test กรณีแก้ offline ก่อนเวลา 1 นาที แล้ว sync หลังเวลา รวมถึง clock rollback, timezone เครื่องไม่ใช่ไทย, reboot และ retry operation เดิม

## 7. งาน C — Draft นัดหมายหลายรายการ

### C.1 เปลี่ยน storage เป็น Room

เพิ่ม Room migration ถัดจาก schema ปัจจุบัน 61 เช่น `61 -> 62` และ export `62.json`

ตาราง `appointment_draft` ที่แนะนำ:

- `draft_id TEXT PRIMARY KEY` — UUID สร้างครั้งเดียว
- `owner_key TEXT NOT NULL` — SHA-256 account key แบบเดียวกับ sync state
- `schema_version INTEGER NOT NULL`
- `title TEXT`
- `planned_date TEXT NULL`
- `planned_time TEXT NULL`
- `project_id TEXT NULL`, `project_name_snapshot TEXT NULL`
- `customer_id TEXT NULL`, `customer_name_snapshot TEXT NULL`
- `payload_json TEXT NOT NULL`
- `created_at TEXT NOT NULL`
- `updated_at TEXT NOT NULL`
- `expires_at TEXT NOT NULL`

index:

- `(owner_key, updated_at DESC)` สำหรับหน้ารายการ
- `(owner_key, expires_at)` สำหรับ cleanup

Draft ไม่เข้า delta snapshot/change feed และไม่มี `is_synced`

### C.2 Repository และข้อจำกัด 20 รายการ

เพิ่ม `AppointmentDraftRepository` พร้อม transaction:

- `createDraft`, `updateDraft`, `getDraft`, `observeDrafts`, `deleteDraft`
- `deleteExpired(now)`
- `deleteForOwner(ownerKey)`
- save draft เดิมต้อง update `draft_id` เดิม ไม่สร้างสำเนาทุกครั้ง
- ถ้าจะสร้างรายการที่ 21 ให้แสดงหน้ารายการและขอให้ผู้ใช้ลบก่อน ห้ามลบ Draft ที่เก่าที่สุดเงียบ ๆ
- รายการที่หมดอายุ 30 วันลบได้ระหว่างเปิดหน้า Draft/maintenance หลัง sync โดยไม่สร้าง background worker แยก

### C.3 UI/navigation

- เพิ่ม route `appointment_drafts`
- เพิ่ม route สร้างนัดหมายที่รับ `draftId?`
- หน้า Draft list เรียงแก้ล่าสุดก่อน แสดงหัวข้อ บริษัท/โครงการ วันนัด และ `แก้ล่าสุด`
- มีคำสั่ง `ทำต่อ`, `ลบ`, และ `สร้างนัดหมายใหม่`
- กด Save Draft บนฟอร์มได้โดยไม่ต้องออกจากหน้า
- กดย้อนกลับยังคง dialog `บันทึก Draft / ทิ้ง / กลับไปแก้`
- เมื่อบันทึก appointment จริงสำเร็จ ให้ลบเฉพาะ Draft ปัจจุบัน
- Draft อื่นต้องไม่ถูกลบหรือเขียนทับ

### C.4 Account isolation และ legacy draft

- query ทุกตัวต้องรับ `owner_key`; ห้ามมี DAO ที่คืน Draft ทุกบัญชีให้ UI
- logout/เปลี่ยนบัญชีต้องลบ Draft ใน transaction เดียวกับการล้าง local data หลังผู้ใช้ยืนยัน
- Draft ไม่ถือเป็น unsynced business data จึงไม่บล็อก logout แต่ต้องแจ้งใน dialog ว่า Draft กี่รายการจะถูกลบ
- migrate Draft แบบ SharedPreferences เดิมได้เฉพาะเมื่อ `localDataOwner` ตรงกับบัญชีปัจจุบัน; หากพิสูจน์เจ้าของไม่ได้ให้ลบทิ้งและห้ามนำไปแสดงกับบัญชีใหม่
- เมื่อ restore แล้ว TEMP project/contact ถูก remap ให้ resolve ผ่าน `LocalIdMappingDao`; หาก parent ถูกลบให้แสดงชื่อ snapshot และบังคับเลือกใหม่ก่อนบันทึกจริง

## 8. Test plan

### 8.1 Account setup

- migration เพิ่ม field โดยไม่เปลี่ยน password hash/phone เดิม
- active accounts ถูกเปิด flag หลัง Android พร้อมเท่านั้น
- บัญชีมีเบอร์: บังคับเฉพาะรหัสผ่าน
- บัญชีไม่มีเบอร์: บังคับทั้งรหัสผ่านและเบอร์
- back/outside tap/process death ไม่สามารถข้าม gate
- password ผิด, password อ่อน, phone ผิด, notice version เก่า และ network timeout
- transaction rollback: phone update ล้มแล้ว password/flag ต้องไม่เปลี่ยนครึ่งเดียว
- token เก่าและ setup-only token เรียก business API ไม่ได้
- setup สำเร็จแล้ว login ใหม่ไม่แสดง gate ซ้ำ
- diagnostics/log ไม่มี password, phone และ token

### 8.2 Appointment policy

ใช้ parameterized tests ทั้ง Android/backend ที่เวลาไทย:

- ก่อนวันนัด 1 นาที/1 วัน
- วันนัดก่อนเวลา 1 วินาที, เท่ากับเวลา, หลังเวลา 1 วินาที
- ก่อน/หลังเที่ยงคืน
- เครื่องตั้ง timezone อื่นแต่ผลยังยึด Bangkok
- On-site planned/checked_in/completed/missing
- Online/Call หลังวันนัด
- planned time ว่าง/ผิดรูปแบบ
- deep link เปิด edit ก่อน cutoff แต่กด save หลัง cutoff
- offline edit ก่อน cutoff แล้ว sync หลัง cutoff
- API PATCH/DELETE/result โดยตรงต้องได้ผลเหมือน Android
- rejected sync ไม่วน retry และไม่ทำข้อมูล local หาย

### 8.3 Draft

- สร้าง 20 Draft แล้วแต่ละรายการไม่เขียนทับกัน
- รายการที่ 21 ถูกบล็อกพร้อมทางไปลบ ไม่ auto-delete
- update Draft เดิมไม่เพิ่ม count
- save appointment ลบเฉพาะ Draft ต้นทาง
- 29 วันยังอยู่, 30 วันตาม boundary หมดอายุ
- logout/switch ลบเฉพาะ owner ที่ยืนยันและไม่แสดงข้ามบัญชี
- process death/reboot แล้ว Draft ยังอยู่
- Room migration `61 -> 62` และ full migration chain ผ่าน
- restore Draft ที่อ้าง TEMP/remapped/deleted parent

### 8.4 Regression gate ก่อน deploy

- Android JVM suite
- Android instrumented suite บน API 31/33 และ emulator ปัจจุบัน
- Room migration tests + exported schema
- backend `clean build` และ full tests
- staging กับ PostgreSQL จริง
- เครื่องจริง: login/setup, offline edit, delayed sync, 20 Draft, logout/switch

## 9. ลำดับพัฒนาเพื่อลดผลกระทบ

### Phase 4A — Account security ก่อน

1. ใช้ password policy และข้อความแจ้งที่ยืนยันแล้ว พร้อมกำหนดวิธีแจก activation credential
2. เพิ่ม backend migration และ API แบบยังไม่เปิด flag
3. เพิ่ม backend tests และ deploy แบบ backward-compatible
4. เพิ่ม Android setup gate และ tests
5. staging end-to-end
6. ปล่อย Android แล้วค่อยเปิด flag ทุก active account

### Phase 4B — Appointment policy

1. เขียน shared test matrix ก่อน
2. ทำ backend policy หลัง feature flag ที่ปิดไว้
3. ทำ Android policy/repository guards และ offline metadata
4. ทดสอบ delayed sync/clock edge cases
5. เปิด backend flag ใน staging แล้ว production หลัง Android พร้อม

### Phase 4C — Multi-draft

1. Room schema/DAO/migration tests
2. repository และ account isolation tests
3. list/navigation/form integration
4. legacy draft handling และ logout warning
5. targeted tests แล้วจึง full regression รอบปิด Phase 4

ไม่ควรรวมทั้ง 4A, 4B และ 4C เป็น commit/deploy เดียว เพราะ rollback ยากและแยกสาเหตุไม่ได้

## 10. Rollback

- Account setup: ปิด enforcement flag ได้โดยไม่ลบ column/acknowledgement; ห้าม rollback password hash
- Appointment: backend feature flag ปิด policy v2 ได้ชั่วคราวและยังเก็บ audit; Android รุ่นเดิมยังอ่านข้อมูลได้เพราะ schema/API additive
- Draft: ปิดเมนู Draft หลายรายการได้โดยไม่ลบ table; ห้าม destructive migration/fallback
- ทุก migration ต้องเป็น forward-fix; ห้าม drop column/table ใน release เดียวกับ rollout

## 11. ไฟล์ที่คาดว่าจะกระทบ

### Android

- `data/model/LoginParams.kt`, `UserDto.kt`, model account setup ใหม่
- `data/remote/AuthService.kt`, `ApiService.kt`
- `data/repository/AuthRepository.kt`, `ActivityRepository.kt`
- `di/TokenManager.kt`, `NetworkModule.kt`
- `ui/viewmodels/auth/LoginViewModel.kt`
- account setup screen/viewmodel และ `ui/navigation/NavGraph.kt`
- `utils/AppointmentStatus.kt` หรือแทนด้วย `AppointmentPolicy.kt`
- `CreateAppointmentViewModel.kt`, `ActivityDetailScreen.kt`, `SalesResultViewModel.kt`, Home menus
- `AppDatabase.kt`, `DatabaseModule.kt`, `AppointmentDraftDao/Entity/Repository`
- `CreateAppointmentScreen.kt`, Home/Draft list navigation
- unit/instrumented/migration tests และ Room schema 62

### Backend

- migration account setup
- `AuthDto.kt`, `AuthRoutes.kt`, `AuthUseCase.kt`
- employee table/entity/repository
- JWT/auth middleware และ token generation
- `AppointmentUseCase.kt`, `AppointmentRoutes.kt`
- appointment/result repositories เฉพาะส่วน transaction enforcement
- route/use-case/migration/policy tests

## 12. Definition of Done

- ไม่มีบัญชี active ใดใช้ full business API ได้ขณะ `password_change_required = true`
- ผู้ใช้ที่มีเบอร์แล้วไม่ถูกขอเบอร์ซ้ำ และผู้ใช้ไม่มีเบอร์ข้ามไม่ได้
- password/phone/notice setup atomic และ token เก่าถูกยกเลิก
- Android และ backend ตัดสิน appointment boundary ตรงกันทุก test case
- On-site ที่ขาดนัดและไม่มี Check-in สร้างผลไม่ได้ทั้ง online, offline queue และ direct API
- offline edit ที่เกิดก่อน cutoff ไม่หายและ sync ได้ตาม policy metadata ที่กำหนด
- มี Draft ได้ 20 รายการโดยไม่เขียนทับกัน ไม่ข้ามบัญชี และหมดอายุ 30 วัน
- full regression/staging/device acceptance ผ่านก่อน deploy

## 13. แหล่งอ้างอิงเชิงหลักการ

- [NIST SP 800-63B](https://pages.nist.gov/800-63-4/sp800-63b.html) — password blocklist, authenticated protected channel และข้อกำหนด password ปัจจุบัน
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html) — ตรวจ current password, re-authentication และ rotate/invalidate session หลังเหตุการณ์สำคัญ
- [Android Developers: Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions) — export schema, versioned migration และ migration tests โดยไม่ใช้ destructive fallback
