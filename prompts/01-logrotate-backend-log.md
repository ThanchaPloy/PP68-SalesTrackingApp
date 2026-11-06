# Prompt: ติดตั้ง logrotate ให้ pp68-backend.log

**สถานะ: ✅ เสร็จแล้ว (ยืนยัน 2026-11-06) — ไม่ต้องทำซ้ำ**

ตรวจบนเซิร์ฟเวอร์จริงพบว่า `/etc/logrotate.d/pp68-backend` มีอยู่แล้วและทำงานจริงมา
ตั้งแต่ ~2026-09-23 (`weekly`, `rotate 8`, `compress`, `copytruncate` — มี `copytruncate`
ซึ่งเป็นจุดสำคัญที่สุดอยู่แล้ว) มีไฟล์ `.1.gz` ถึง `.6.gz` ยืนยันว่า `logrotate.timer`
รันจริงทุกสัปดาห์ ไม่ใช่แค่ config ค้างเฉยๆ เอกสารข้างล่างนี้เก็บไว้เป็นประวัติ/อ้างอิง
เผื่อต้องตั้งเครื่องใหม่หรือ config หายไปในอนาคต

---

**ขอบเขต (เดิม): เครื่องเซิร์ฟเวอร์ production เท่านั้น ไม่มีไฟล์ไหนใน git repo ให้แก้**

## บริบทที่ต้องรู้ก่อนเริ่ม

`pp68-backend.service` (systemd unit บน `192.168.15.177`) ตั้ง
`StandardOutput=append:/var/log/pp68-backend.log` และ `StandardError` แบบเดียวกัน — log
ของแอปทั้งหมดไปที่ไฟล์นี้ ไม่ไป journald (`journalctl -u pp68-backend` เห็นแค่บรรทัด
start/stop ของ systemd เอง ไม่เห็น error ของแอปเลย)

ไฟล์นี้**ไม่มี logrotate ยืนยันว่าติดตั้งแล้ว** ณ ตอนเขียน prompt นี้ (2026-11-06)
เคยมีคนเสนอ `copytruncate` ไว้ก่อนหน้านี้แต่ไม่ได้ยืนยันว่าทำจริง ความเสี่ยง: ไฟล์โต
ไม่จำกัดจนดิสก์เต็ม ทำให้ **backend process ล้มทั้งตัว** (เขียนไฟล์ไม่ได้/disk full) ไม่ใช่
แค่ log หาย — นี่คือเหตุผลที่งานนี้ priority สูงกว่าอีกสองข้อ

## สิ่งที่ต้องทำ

1. SSH เข้า `192.168.15.177` (หรือเครื่องที่รัน `pp68-backend.service` จริง — ยืนยัน IP
   กับคนที่ดูแล infra ก่อน ถ้าเปลี่ยนไปแล้วตั้งแต่ข้อมูลนี้ถูกบันทึก)
2. เช็คก่อนว่ามี logrotate ติดตั้งอยู่แล้วหรือยัง:
   ```bash
   which logrotate || sudo apt install -y logrotate   # หรือ yum/dnf ตาม distro
   ls /etc/logrotate.d/ | grep -i pp68                 # เช็คว่ามี config เก่าค้างอยู่ไหม
   ```
3. สร้าง (หรือแก้ถ้ามีแล้ว) `/etc/logrotate.d/pp68-backend`:
   ```
   /var/log/pp68-backend.log {
       daily
       rotate 14
       compress
       delaycompress
       missingok
       notifempty
       copytruncate
   }
   ```
   **ห้ามตัด `copytruncate` ออก** — service เปิดไฟล์แบบ append ค้าง fd ไว้ตลอดเวลาที่รัน
   ถ้าใช้ logrotate แบบ rename+signal ปกติ (ไม่มี `copytruncate` และไม่มี
   `postrotate` สั่ง restart/reopen fd) log ใหม่หลัง rotate ครั้งแรกจะเขียนไปที่ไฟล์ที่
   ถูก rename ไปแล้ว ไม่มีใครเห็น error อีกเลย เงียบสนิท ไม่มี error message บอกเลยว่าเกิด
   อะไรขึ้น — เป็นจุดพลาดที่พบบ่อยที่สุดของการตั้ง logrotate กับ service ที่รันต่อเนื่อง
4. ทดสอบ config โดยไม่ต้องรอถึงเวลาจริง:
   ```bash
   sudo logrotate -d /etc/logrotate.d/pp68-backend   # dry-run ดู debug output
   sudo logrotate -f /etc/logrotate.d/pp68-backend   # force รันจริงครั้งเดียวทันที
   ```
5. ยืนยันผลลัพธ์:
   - มีไฟล์ `/var/log/pp68-backend.log.1` (หรือ `.gz` ถ้า rotate ไปแล้วรอบที่สอง) เกิดขึ้น
   - `/var/log/pp68-backend.log` (ไฟล์หลัก) ยังมีขนาด/inode เดิม (ไม่ได้ rename) และ service
     ยัง**เขียน log ใหม่เข้าไฟล์นี้ต่อได้จริง** — ทดสอบด้วยการยิง request อะไรสักอย่างไป
     backend แล้วเช็คว่า `tail -f /var/log/pp68-backend.log` เห็นบรรทัดใหม่ขึ้นจริง
   - `sudo systemctl status pp68-backend` ยัง active ปกติ ไม่ได้ crash ตอน rotate

## Rollback ถ้ามีปัญหา

ลบ `/etc/logrotate.d/pp68-backend` ทิ้ง ไม่มีผลกระทบย้อนหลัง (ไฟล์ log เก่าที่ rotate
ไปแล้วไม่ถูกลบคืน แต่ก็ไม่ใช่ปัญหา)

## สิ่งที่ "เสร็จแล้ว" ต้องเป็นแบบนี้

- `logrotate -d` รันแล้วไม่มี error/warning เกี่ยวกับไฟล์นี้
- รอข้ามคืนหนึ่งรอบ (cron ของ logrotate ปกติรันทุกวันตอนเช้า ผ่าน `/etc/cron.daily/logrotate`
  หรือ systemd timer `logrotate.timer` ขึ้นกับ distro) แล้วเช็คว่ามีไฟล์ `.1.gz` ใหม่เกิดขึ้น
  และ service ยังทำงานปกติไม่มีใครบ่นเรื่อง log ไม่ขึ้น
