# Fallback Artifacts — View Transaction History

> **Purpose:** ใช้เฉพาะตอน live agent ล้มเหลว / ช้าเกินไป / context ผิด
> **Rule:** ห้ามเปิดไฟล์เหล่านี้ระหว่าง demo เว้นแต่จะ trigger fallback path

---

## ใช้เมื่อไหร่

| Trigger | Action |
|---|---|
| Agent ตอบช้า > 2 min | Open fallback ที่ตรงกับ act → Highlight 30 sec → ข้ามไป act ถัดไป |
| Agent ตอบ schema ผิด | บอก audience "เห็น schema ไม่ครบ — ขอเปิดเคสที่รันเสร็จแล้วเทียบ" |
| Context window full | Reset session + load fallback artifact ก่อน continue |
| Network drop | Tethering up + paste fallback content ลง expected path |

---

## Fallback Strategy

แต่ละ act มี **proxy artifact** จาก Money Transfer demo:

| Act | Live agent | Fallback file (Money Transfer artifact) |
|---|---|---|
| 1 | banking-ba | `docs/artifacts/S2-ba-money-transfer.md` |
| 2 | banking-sa | `docs/artifacts/S3-solution-architect-money-transfer.md` |
| 2 | banking-designer | (ใช้ wireframe ASCII ใน DEMO-SCRIPT แทน) |
| 2 | banking-qa P1 | `docs/test-plans/` (ถ้ามี) |
| 3 | banking-tech-lead | `docs/artifacts/S4-tech-lead-money-transfer.md` |
| 4 | banking-backend-dev | `docs/artifacts/S5-backend-dev-money-transfer.md` |
| 4 | banking-frontend-dev | (skim transfer-service equivalent หรือ wireframe) |
| 4 | banking-devops P1 | `docs/artifacts/S9-devops-money-transfer.md` |
| 5 | banking-reviewer-be/fe | `docs/artifacts/S6-reviewer-money-transfer.md` (มี findings ครบ) |
| 6 | banking-security | `docs/artifacts/S7-security-money-transfer.md` |

---

## ตัวอย่าง Narration เมื่อ Fallback

```
"งั้นลองดูเคสที่ผ่านมาแบบเทียบกัน — Money Transfer เคสจริง
agent เราจับ N+1 + missing Outbox event + saga compensation gap
view-history นี้ scope เล็กกว่า ก็เลยจับน้อยกว่าเป็นปกติ"
```

---

## Pre-baked Skeleton Files

ไฟล์ใน folder นี้ (เตรียมไว้ล่วงหน้าก่อน demo) :

- `S5-be-fallback.md` — pre-baked backend summary (15 file listing + key snippets)
- `S5-fe-fallback.md` — pre-baked frontend summary (10 file listing + key snippets)
- `S6-reviewer-fallback.md` — pre-baked review findings (1 blocker + 2 major + 3 minor)

> หมายเหตุ: ไฟล์เหล่านี้ตอนนี้ยังเป็น placeholder — ถ้าทีมต้องการ guarantee fallback ที่ smooth ที่สุด ให้รัน agent ครั้งเดียวก่อน demo (offline) แล้วบันทึก output มาเก็บที่นี่
