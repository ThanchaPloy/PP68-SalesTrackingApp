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

## 📦 Git repo status (ตรวจล่าสุด 2026-07-27)

| Repo | Local/Server commit | GitHub `origin/main` | สถานะ |
|---|---|---|---|
| **Backend** (`ThanchaPloy/backend-PP68SalesTrackingApp`) | `0b0d911` (บน `192.168.15.177`) | `0b0d911` | ✅ ตรงกัน |
| **Android app** (`ThanchaPloy/PP68-SalesTrackingApp`) | `6b27dea` | `6b27dea` | ✅ ตรงกัน |

Server (`.177`) มี SSH deploy key ของตัวเองแล้ว ใช้ `git pull`/`git push` จาก repo backend ได้โดยตรงไม่ต้องผ่านเครื่องอื่น

---

## 🧹 ข้อมูลทดสอบ

ข้อมูลทดสอบทั้งหมด (Test Project1, นัดหมายทดสอบ, ผู้ติดต่อทดสอบ, สินค้าทดสอบ) ที่เกิดขึ้นระหว่างการแก้บั๊กถูกลบออกจาก production DB แล้ว (ลบเมื่อ 2026-07-27) ลูกค้าจริง `01226HO` (test company) ที่ใช้ทดสอบไม่ได้ถูกลบ ยังอยู่ตามปกติ
