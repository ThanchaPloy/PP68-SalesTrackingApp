# PP68 Sales Tracking — สรุปสิ่งที่รันอยู่บน Server (สำหรับแจ้งทีม)

อัปเดตล่าสุด: 2026-07-28

---

## 🟢 ของเรา — ห้ามปิด/ลบ กระทบแอปโดยตรง

| อะไร | รายละเอียด |
|---|---|
| **`pp68-backend.service`** | systemd service บนเครื่อง `192.168.15.177`, พอร์ต **8080** คือ backend หลักที่แอป Android เรียกทั้งหมด (ผ่าน domain `api-ploy.cskmitl.com`) ตั้งเป็น `Restart=on-failure` และ `enabled` ไว้แล้ว (รีสตาร์ทเองถ้า crash หรือเครื่อง reboot) **ห้าม** `systemctl stop pp68-backend` หรือ `kill` process java ตรงๆ (PID เปลี่ยนทุกครั้งที่ restart ดูด้วย `systemctl status pp68-backend`) |
| **Postgres @ `192.168.15.182:5432`** | ฐานข้อมูลจริงทั้งหมด (project, customer, appointment, contact ฯลฯ) — ห้ามปิด Postgres service บนเครื่องนี้ |
| **โค้ด `/root/ploy/backend-PP68SalesTrackingApp`** (บน `192.168.15.177`) | ต้องอยู่ครบ ห้ามลบโฟลเดอร์นี้ — jar ที่รันอยู่ build มาจากที่นี่ |
| **nginx (Nginx-UI) บนเครื่อง `192.168.15.225`** | **สำคัญมาก — เจอเมื่อ 2026-07-28**: domain `api-ploy.cskmitl.com` ทั้งหมด (ทุก API รวม login/CRUD/upload) วิ่งผ่านเครื่องนี้ก่อนถึง backend จริงที่ `.177:8080` ห้ามปิด nginx บนเครื่องนี้ ห้ามลบ/แก้ config ไฟล์ `/etc/nginx/sites-available/api-ploy.cskmitl.com` โดยไม่เข้าใจผลกระทบ — ถ้า nginx ตัวนี้ล่ม แอปทั้งแอปคุยกับ server ไม่ได้เลย (ทุก request จะ timeout/connection refused ไม่ใช่แค่ error ฝั่ง backend) |

### วิธีเช็คว่า backend ยังอยู่ปกติ
```bash
curl https://api-ploy.cskmitl.com/health
# ควรได้ {"status":"ok","service":"pp68-backend"}

systemctl status pp68-backend --no-pager
```

### วิธี restart backend (ถ้าจำเป็นจริงๆ เช่น deploy โค้ดใหม่)
```bash
ssh root@192.168.15.177
cd ~/ploy/backend-PP68SalesTrackingApp
git pull origin main
./gradlew buildFatJar
systemctl restart pp68-backend
systemctl status pp68-backend --no-pager
```

**ต้องเช็คทุกครั้งหลัง deploy ที่มี migration ใหม่** — `DatabaseFactory` รันเฉพาะไฟล์ที่อยู่ในลิสต์ของมัน และหา path แบบ relative กับ working directory ของ process ถ้า `WorkingDirectory` ของ systemd ไม่ใช่ราก repo จะข้ามทุก migration แบบมี warning บรรทัดเดียว

ดู log ตอน start หา `migration ok:` ของไฟล์ที่เพิ่งเพิ่ม และต้องไม่มี `migration FAILED:` หรือ `migration SKIPPED (หาไฟล์ไม่เจอ...)`

> log ไปที่ stdout ของ service ไม่ได้เขียนไฟล์เอง เคยเสียเวลาเพราะ grep ผิดที่แล้วสรุปว่า "ไม่มี error" ทั้งที่ดูคนละที่กับที่ service เขียนจริง

ยิงเช็คจากนอกเครื่องได้อีกทาง โดยเทียบ 405 กับ 404 — 405 แปลว่ามี route แล้วแค่ผิด method ส่วน 404 แปลว่าโค้ดยังไม่ขึ้น:

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://api-ploy.cskmitl.com/account/complete-initial-setup  # 405 = Phase 4A ขึ้นแล้ว
curl -s -o /dev/null -w "%{http_code}\n" https://api-ploy.cskmitl.com/sync/v2/cursor                   # 401 = Phase 3 ขึ้นแล้ว
```

### วิธี reload nginx บน `.225` (ถ้าจำเป็นจริงๆ เช่นแก้ config)
```bash
ssh root@192.168.15.225
nginx -t              # เช็ค syntax ก่อนเสมอ ห้าม reload ถ้า test ไม่ผ่าน
nginx -s reload        # หมายเหตุ: nginx บนเครื่องนี้ไม่ได้รันผ่าน systemd (systemctl reload nginx จะบอก "not active") ต้องใช้ nginx -s reload ตรงๆ
```

**บั๊กที่เจอและแก้แล้ว (2026-07-28)**: `client_max_body_size` ไม่ได้ตั้งไว้ใน server block ของ `api-ploy.cskmitl.com` เลย ทำให้ nginx ใช้ค่า default 1MB — รูปถ่ายจากกล้องจริง (ปกติ 2-15MB) ถูกปฏิเสธด้วย `413 Request Entity Too Large` ทุกครั้งตอนบันทึกผลการขายพร้อมรูป ทำให้เซฟไม่ได้ **แก้แล้ว**: เพิ่ม `client_max_body_size 20M;` ในทั้ง 2 server block (port 80 และ 443) ของไฟล์ `/etc/nginx/sites-available/api-ploy.cskmitl.com` แล้ว reload — ทดสอบอัปโหลดไฟล์ 12MB ผ่าน production แล้วสำเร็จ

---

## 🟡 อยู่บนเครื่องเดียวกัน แต่ไม่ใช่ของเรา / ไม่ทราบเจ้าของ — อย่าเพิ่งไปยุ่ง

| พอร์ต/service | หมายเหตุ |
|---|---|
| `3000` (postgrest) | โปรเซสเก่าที่เห็นอยู่บนเครื่อง `.177` ไม่ทราบว่ายังมีใครใช้อยู่ไหม |
| `3001` (MainThread) | ตรงกับ URL เก่าที่แอปเคยใช้ (`BASE_AUTH_URL` เก่า) — **แอปตอนนี้ไม่ได้เรียกพอร์ตนี้แล้ว** ย้ายมา 8080 หมดแล้ว แต่ไม่แน่ใจว่ามีระบบอื่นยังพึ่งอยู่ไหม |
| `5173` (MainThread) | ไม่ทราบว่าคืออะไร ควรถามเจ้าของเครื่องก่อน |
| Docker: `portainer` (พอร์ต 9000/9443) | ตัวจัดการ docker เฉยๆ ไม่เกี่ยวกับแอปเรา |
| Docker containers อื่น 5 ตัว (`pp68-backend-app`, `quizzical_blackburn`, `inspiring_ganguly`, `admiring_williamson`, `dazzling_galois`) | **Exited หมดแล้ว** (ปิดอยู่ ไม่ได้รันอะไร) เป็น container เก่าจากการ deploy ครั้งก่อนที่เลิกใช้แล้ว (ตอนนี้ deploy ผ่าน systemd โดยตรง ไม่ใช้ docker) ลบทิ้งได้ถ้าอยากเคลียร์ แต่ไม่จำเป็นเร่งด่วน |
| เครื่อง `192.168.15.225` ทั้งเครื่อง — service/site อื่นๆ นอกจาก `api-ploy.cskmitl.com` | เครื่องนี้รัน Nginx-UI จัดการหลาย site (เห็น server block อื่นๆ อีกเพียบตอนเช็ค config) ไม่รู้ว่า site อื่นเป็นของใคร/ระบบอะไร แตะเฉพาะไฟล์ `sites-available/api-ploy.cskmitl.com` พอ อย่าไปยุ่ง site อื่น |

---

## 📦 Git repo status (ตรวจล่าสุด 2026-10-06)

| Repo | GitHub `origin/main` | สถานะ |
|---|---|---|
| **Backend** (`ThanchaPloy/backend-PP68SalesTrackingApp`) | `e2e9ba6` — Phase 4A + 4B + fix ERP/นัดหมาย + log flag | ยืนยันแล้วว่า Phase 4A ขึ้น production (`/account/complete-initial-setup` ตอบ 405) ส่วนตัวหลังจากนั้นไม่มี route ใหม่ ดูจาก log ตอน start แทน |
| **Android app** (`ThanchaPloy/PP68-SalesTrackingApp`) | ยังไม่ได้ merge — งานอยู่บน branch | ดูตาราง branch ด้านล่าง |

Server (`.177`) มี SSH deploy key ของตัวเองแล้ว ใช้ `git pull`/`git push` จาก repo backend ได้โดยตรงไม่ต้องผ่านเครื่องอื่น

### branch ของ Android (ตุลาคม 2026)

ยังไม่มี branch ไหน merge เข้า `main` ของฝั่งแอป แต่ละ release คือการตัด branch แล้ว build จากตรงนั้น

| Branch | เนื้อหา |
|---|---|
| `release/phase-4a` | บังคับตั้งค่าบัญชีอย่างเดียว (ตัวแรกที่แจก) |
| `release/phase-4b` | 4A + กติกานัดหมายตามเวลาเริ่มนัด + เวลาอ้างอิงจากเซิร์ฟเวอร์ |
| `release/phase-4c` | **ตัวล่าสุดที่ใช้แจก** — 4A + 4B + ฉบับร่างหลายรายการ + fix จากการทดสอบเครื่องจริง (Room 63) |
| `feature/appointment-policy` | branch พัฒนา ทุก fix ถูก cherry-pick กลับมาที่นี่เสมอ |

ฝั่ง backend `fix/*` และ `feature/appointment-policy` merge เข้า `main` หมดแล้ว

### สวิตช์สองตัวที่เปิดด้วยมือ ไม่ได้เปิดเองตอน deploy

**1. `APPOINTMENT_POLICY_V2` — กติกาแก้/ลบ/บันทึกผลนัดหมายฝั่ง server**

ใส่เป็นบรรทัดในไฟล์ `.env` ที่รากโปรเจกต์ (`loadDotenv()` ใน `Application.kt` อ่านตอน start แล้วยัดเป็น system property ก่อน config ถูก parse)

```bash
echo 'APPOINTMENT_POLICY_V2=true' >> .env     # >> คือต่อท้าย ถ้าใช้ > จะทับทั้งไฟล์
systemctl restart pp68-backend
```

ยืนยันจาก log ตอน start ต้องขึ้น `PP68 Backend started on port 8080 (appointment_policy_v2=true)` ถ้าขึ้น `false` แปลว่าไฟล์ไม่ถูกอ่าน เกือบทุกครั้งคือ `WorkingDirectory` ของ systemd ไม่ได้ชี้มาที่รากโปรเจกต์ (ปัญหาเดียวกับที่ทำให้ migration ไม่รัน)

เปิดแล้วเครื่องที่ยังใช้ APK ก่อน `release/phase-4b` จะแก้นัดหมายไม่ได้เลย เพราะรุ่นเก่าไม่ส่ง `client_modified_at` ขึ้นมา ทุกการแก้จะได้ 422 — ต้องแจก APK ให้ครบก่อนหรือพร้อมกัน ปิดกลับได้ด้วยการลบบรรทัดนั้นแล้ว restart ไม่ต้อง rollback โค้ดหรือแตะฐานข้อมูล

**2. `password_change_required` — บังคับตั้งรหัสผ่าน/เบอร์ครั้งแรก**

migration สร้างคอลัมน์ให้เป็น `FALSE` ทุกบัญชี หน้าตั้งค่าจะไม่โผล่จนกว่าจะเปิดเอง

ทดสอบเฉพาะบัญชีตัวเองก่อนได้
```sql
UPDATE employee SET password_change_required = TRUE WHERE emp_code = 'อีเมลตัวพิมพ์เล็ก';
```
เปิดทั้งบริษัทให้รัน `migrations/oneoff_enable_required_account_setup.sql`

**ห้ามเปิดก่อนแจก APK ที่มีหน้าตั้งค่า** ผู้ใช้บนแอปรุ่นเก่าจะ login ได้แต่ไม่มีหน้าให้ตั้งค่า = ใช้งานไม่ได้ทั้งหมด

---

## 🧹 ข้อมูลทดสอบ

ข้อมูลทดสอบทั้งหมด (Test Project1, นัดหมายทดสอบ, ผู้ติดต่อทดสอบ, สินค้าทดสอบ) ที่เกิดขึ้นระหว่างการแก้บั๊กถูกลบออกจาก production DB แล้ว (ลบเมื่อ 2026-07-27) ลูกค้าจริง `01226HO` (test company) ที่ใช้ทดสอบไม่ได้ถูกลบ ยังอยู่ตามปกติ
