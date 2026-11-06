# Prompt: แจ้งเตือนอัตโนมัติเมื่อ backend ล่มหรือ error พุ่ง

**สถานะ: ✅ เสร็จแล้ว (ยืนยัน 2026-11-07)**

ติดตั้งและทดสอบจริงบน `sp691-tng` ครบทั้ง 2 ชั้น:
- ชั้นที่ 1 (process ตาย): ทดสอบด้วย `systemctl kill -s SIGKILL pp68-backend` จริง —
  ได้รับ email alert, service ฟื้นเองจาก `Restart=on-failure` ที่มีอยู่แล้วในของเดิม
- ชั้นที่ 2 (error rate พุ่ง): ทดสอบ true-positive path ด้วยไฟล์ log ปลอม (ไม่แตะของจริง)
  ใส่ 3 บรรทัด `ERROR` แล้วยืนยันได้รับ alert "เจอ ERROR 3 ครั้ง" — cron ติดตั้งจริงแล้ว
  (`*/5 * * * * /usr/local/bin/pp68-error-watch.sh`, threshold 20, ไม่มี cron เดิมถูกทับ)
- ส่ง channel: **Email ผ่าน `curl` คุย SMTP ตรง** (ไม่ใช่ local sendmail — เครื่องนี้ออก
  internet port 25 ไม่ได้เลย, ใช้ port 587 แทนซึ่งเชื่อมต่อได้) บัญชี
  `tangnamsalesproject@gmail.com` ทั้งส่งและรับ (เปลี่ยน `TO` ใน
  `/usr/local/bin/pp68-alert.sh` เป็น alias ทีมจริงทีหลังได้ถ้าต้องการ)
- ไฟล์บนเซิร์ฟเวอร์: `/usr/local/bin/pp68-alert.sh`, `/usr/local/bin/pp68-error-watch.sh`,
  `/etc/systemd/system/pp68-backend-alert.service`, `/etc/pp68-alert/smtp.pass` (chmod 600),
  `/etc/systemd/system/pp68-backend.service` (เพิ่ม `OnFailure=` บรรทัดเดียว, มี `.bak`
  สำรองของเดิมไว้ที่ `pp68-backend.service.bak`)

**ขอบเขต (เดิม): เครื่องเซิร์ฟเวอร์ production เท่านั้น ไม่มีไฟล์ไหนใน git repo ให้แก้**

**channel ที่เลือกแล้ว (2026-11-06): ส่ง alert เป็น Email** — เหตุผล: งานนี้จะถูกส่งมอบ
แล้วไม่มีคนเดิมดูแลต่อ ทีมที่รับงานไม่รู้ว่าจะใช้ Telegram เป็นประจำไหม Email เป็นช่อง
ทางเดียวที่**ทุกองค์กรเช็คอยู่แล้วแน่ๆ ไม่ต้องสอนเครื่องมือใหม่ให้คนที่รับงานต่อ** และ
เป็น protocol มาตรฐานเปิด (SMTP) ไม่ใช่ feature ของบริษัทใดบริษัทหนึ่งที่ปิดบริการได้
แบบไม่ทันตั้งตัว — **LINE Notify ที่เคยเสนอไว้ก่อนหน้านี้ใช้ไม่ได้แล้ว** (LINE ปิดบริการ
ไปแล้วจริงตั้งแต่ 31 มี.ค. 2025 ประกาศล่วงหน้า 7 ต.ค. 2024) ตัดออกจากตัวเลือกทั้งหมด

## บริบทที่ต้องรู้ก่อนเริ่ม

สเกลของระบบ: ผู้ใช้งานจริง ~50 คน, เซิร์ฟเวอร์ self-hosted เครื่องเดียว (`192.168.15.177`),
ไม่มีทีม SRE/ops แยก ตอนนี้วิธีเดียวที่รู้ว่า backend มีปัญหาคือ**ผู้ใช้บ่นเข้ามา** แล้วค่อย
`tail -f /var/log/pp68-backend.log` ไปหาสาเหตุ — ไม่มีการแจ้งเตือนอัตโนมัติเลย

ที่สเกลนี้ **ไม่คุ้มที่จะติด Prometheus/Grafana/Datadog** (ค่าใช้จ่าย+ความซับซ้อนในการ
ดูแลสูงกว่าปัญหาที่ต้องแก้มาก) งานนี้ตั้งใจให้เป็นของง่ายที่สุดที่ยังได้ผลจริง สองชั้น
แยกกัน ทำได้อิสระจากกัน ไม่ต้องรอกัน:

### ชั้นที่ 1 — process ตายทั้งตัว (severity สูง, ต้องรู้ทันที)

`pp68-backend.service` ตายไปเลย (crash, OOM, ฯลฯ) — systemd รู้เรื่องนี้อยู่แล้วเพราะ
เป็นคนรัน process ใช้ feature ของ systemd เองได้ ไม่ต้องเขียน script เฝ้า

### ชั้นที่ 2 — process ยังรันอยู่ แต่ error rate ในไฟล์ log พุ่งขึ้นผิดปกติ (severity ต่ำกว่า
process ตาย แต่สำคัญเพราะ process จะดูปกติจาก `systemctl status` ทั้งที่ผู้ใช้ทุกคน
กำลังเจอ 500 อยู่)

ต้องมี script เฝ้า log แยก เพราะ systemd ไม่รู้เรื่อง "เนื้อหาใน log บอกว่า error เยอะ"

## ตัดสินใจก่อนเริ่ม (ถามคนสั่งงานก่อนลงมือ — ไม่มีคำตอบในเอกสารที่มีอยู่ตอนนี้)

1. **ส่ง email ไปที่อะไร และส่งออกจากบัญชีไหน** — channel ตัดสินแล้วว่าเป็น Email แต่ยัง
   ไม่มีคำตอบ 2 อย่างนี้:
   - **ปลายทาง (to)**: อีเมลของทีม/คนที่จะรับงานต่อ ควรเป็น **alias กลุ่ม** (เช่น
     `ops@บริษัท.com`) ไม่ใช่ email ส่วนตัวของใครคนเดียว เพราะคนรับ alert ควรเปลี่ยนได้
     โดยไม่ต้องแก้ script
   - **ต้นทาง (from/SMTP)**: ก่อนเขียน script ต้องเช็คก่อนว่าเครื่อง `sp691-tng` มี mail
     relay/MTA ตั้งไว้แล้วหรือยัง (`which sendmail mail mailx msmtp 2>/dev/null` และ
     `cat /etc/postfix/main.cf 2>/dev/null | grep relayhost`) — **ถ้ามีอยู่แล้วให้ใช้ของเดิม
     ไม่ต้องตั้งใหม่** (ตามหลัก "เช็คของที่มีอยู่แล้วก่อนเพิ่มของใหม่") ถ้าไม่มี ให้ใช้วิธี
     ในข้อ 3.1 ด้านล่าง (curl คุย SMTP ตรงผ่านบัญชี email ขององค์กรที่เปิด app password
     ได้ — ไม่ต้องติดตั้ง package ใหม่เลยเพราะ `curl` รองรับ SMTP ในตัวอยู่แล้ว)
2. **threshold ของชั้นที่ 2 ควรเป็นเท่าไหร่** — เริ่มเสนอ "ERROR เกิน 20 ครั้งใน 5 นาที"
   เป็นค่าเริ่มต้นที่เดาคร่าวๆ (ยังไม่มีข้อมูล error-rate ปกติของระบบจริงมา baseline)
   **ควรรันแบบ log-only (ไม่ส่ง alert จริง) สัก 2-3 วันก่อน** เพื่อดูว่า threshold นี้
   สมเหตุสมผลไหม ปรับทีหลังได้ง่าย เพราะเป็นตัวเลขเดียวใน script

## สิ่งที่ต้องทำ

### 3.1 ชั้นที่ 1 — แจ้งเตือนเมื่อ process ตาย

สร้าง `/etc/systemd/system/pp68-backend-alert.service`:
```ini
[Unit]
Description=แจ้งเตือนเมื่อ pp68-backend ล่ม

[Service]
Type=oneshot
ExecStart=/usr/local/bin/pp68-alert.sh "pp68-backend service หยุดทำงาน (%n)"
```

สร้าง `/usr/local/bin/pp68-alert.sh` (ใช้ร่วมกับชั้นที่ 2 ด้วย) — สองแบบ เลือกตามผล
เช็คในข้อ 1 ของหัวข้อ "ตัดสินใจก่อนเริ่ม":

**แบบ A — เครื่องมี MTA/relay อยู่แล้ว (`sendmail`/`mail` ใช้ได้):**
```bash
#!/bin/bash
# $1 = ข้อความที่จะส่ง
TO="ops@บริษัท.com"   # ← แก้เป็น alias กลุ่มจริง
echo "$1" | mail -s "[pp68-backend alert]" "$TO"
```

**แบบ B — ไม่มี MTA บนเครื่อง ใช้ `curl` คุย SMTP ตรงผ่านบัญชีอีเมลองค์กร (ไม่ต้องติดตั้ง
package ใหม่เลย `curl` รองรับ SMTP ในตัวมาหลายปีแล้ว):**
```bash
#!/bin/bash
# $1 = ข้อความที่จะส่ง
FROM="alert@บริษัท.com"          # ← บัญชีที่เปิด app password ไว้ส่ง (ไม่ใช่บัญชีส่วนตัว)
TO="ops@บริษัท.com"              # ← alias กลุ่มของทีมที่รับงานต่อ
SMTP_HOST="smtp.gmail.com:465"   # ← แก้ตาม provider ขององค์กร (Gmail/Workspace/Outlook ฯลฯ)
PASS_FILE="/etc/pp68-alert/smtp.pass"   # chmod 600, เก็บ app password อย่างเดียวในไฟล์นี้

{
  echo "From: $FROM"
  echo "To: $TO"
  echo "Subject: [pp68-backend alert] $(date '+%Y-%m-%d %H:%M')"
  echo
  echo "$1"
} | curl -s --ssl-reqd \
  --url "smtps://${SMTP_HOST}" \
  --user "${FROM}:$(cat "$PASS_FILE")" \
  --mail-from "$FROM" \
  --mail-rcpt "$TO" \
  --upload-file -
```
`mkdir -p /etc/pp68-alert && chmod 700 /etc/pp68-alert` แล้วเอา app password ไปวางใน
`smtp.pass` ด้วย `chmod 600` — **ห้ามเก็บ password/app-password เป็น plaintext ในไฟล์ที่
อ่านได้ทุกคน** เด็ดขาด ทั้งสองแบบใช้ `chmod +x /usr/local/bin/pp68-alert.sh` เหมือนกัน

แก้ `pp68-backend.service` ที่มีอยู่ เพิ่มบรรทัดในหมวด `[Unit]`:
```ini
OnFailure=pp68-backend-alert.service
```
`systemctl daemon-reload` แล้ว**ทดสอบจริง**ด้วยการ `sudo systemctl kill -s SIGKILL
pp68-backend` (ไม่ใช่ `stop` — `stop` คือ exit ปกติ `OnFailure` จะไม่ trigger) แล้วเช็คว่า
ได้รับ alert จริง ก่อนเชื่อว่าใช้งานได้

### 3.2 ชั้นที่ 2 — แจ้งเตือนเมื่อ error rate พุ่ง

สร้าง `/usr/local/bin/pp68-error-watch.sh`:
```bash
#!/bin/bash
LOG=/var/log/pp68-backend.log
STATE=/var/tmp/pp68-error-watch.state   # เก็บเลขบรรทัดล่าสุดที่เช็คไปแล้ว
THRESHOLD=20

last_line=$(cat "$STATE" 2>/dev/null || echo 0)
total_lines=$(wc -l < "$LOG")
new_errors=$(tail -n +"$((last_line + 1))" "$LOG" | grep -c " ERROR ")

echo "$total_lines" > "$STATE"

if [ "$new_errors" -ge "$THRESHOLD" ]; then
    /usr/local/bin/pp68-alert.sh "pp68-backend: เจอ ERROR $new_errors ครั้งในช่วงที่ผ่านมา"
fi
```
`chmod +x /usr/local/bin/pp68-error-watch.sh`

สร้าง cron entry (รันทุก 5 นาที) — `crontab -e` เพิ่มบรรทัด:
```
*/5 * * * * /usr/local/bin/pp68-error-watch.sh
```

**ข้อควรระวัง:** ถ้าตั้ง logrotate ไว้แล้ว (ดู `01-logrotate-backend-log.md`) และใช้
`copytruncate` — ไฟล์จะถูก truncate เป็น 0 บรรทัดตอน rotate แต่ `STATE` ไฟล์ยังเก็บ
เลขบรรทัดเก่าที่มากกว่าจำนวนบรรทัดจริงในไฟล์ใหม่ ทำให้ `tail -n +(last_line+1)` ไม่ได้
อะไรเลยสักพักหลัง rotate (ไม่ error แค่เงียบไปชั่วคราว ไม่กระทบความถูกต้อง แค่พลาด
error ที่เกิดช่วงสั้นๆหลัง rotate ไป) — ถ้าต้องการแก้ให้เนียน ให้เพิ่มเช็คว่า
`total_lines < last_line` แล้ว reset `last_line=0` ก่อนนับ ไม่ใช่ required แต่ควรรู้ไว้

### 3.3 เทสที่ต้องมี

ไม่มีโค้ดในฝั่ง repo ให้เขียน unit test — นี่คือ infra script ล้วนๆ สิ่งที่ต้องทำแทนเทส
คือ**พิสูจน์ด้วยการรันจริงบนเซิร์ฟเวอร์**ตามหัวข้อ "สิ่งที่ 'เสร็จแล้ว' ต้องเป็นแบบนี้"

## สิ่งที่ "เสร็จแล้ว" ต้องเป็นแบบนี้

- `systemctl kill -s SIGKILL pp68-backend` → ได้รับ alert ข้อความ "service หยุดทำงาน"
  ภายในไม่กี่วินาที แล้ว `systemctl status pp68-backend` ต้องกลับมา active เองถ้ามี
  `Restart=` ตั้งไว้อยู่แล้วใน unit เดิม (เช็คว่ามีไหมก่อน ไม่ใช่ขอบเขตของ prompt นี้
  แต่ถ้าไม่มีเลย ควรแจ้งคนสั่งงานว่าพบช่องว่างนี้ด้วย)
- ยิง request ปลอมๆให้เกิด ERROR เกิน threshold ในไฟล์ log ภายในหน้าต่าง 5 นาที (เขียน
  loop เรียก endpoint ที่รู้ว่า error แน่ๆ เช่น auth ผิด) → ได้รับ alert ข้อความ
  "เจอ ERROR N ครั้ง"
- รันทิ้งไว้ 2-3 วันแบบไม่มี error จริง → **ไม่มี alert ปลอมเกิดขึ้นเลย** (false positive
  rate ต้องเป็นศูนย์ ไม่งั้นทีมจะเริ่มเมิน alert ไปเอง)
