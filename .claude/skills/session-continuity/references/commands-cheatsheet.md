# Session Continuity — Commands Cheatsheet

> Print this and pin to your workspace. The 4 commands you need to remember.

---

## 🎯 The 4 Commands

```
┌─────────────────┬──────────────────────────────┬───────────────────────┐
│  Intent         │  Thai                        │  English              │
├─────────────────┼──────────────────────────────┼───────────────────────┤
│  See status     │  ถึงไหนแล้ว                  │  status               │
│  Continue work  │  ทำต่อ                       │  resume               │
│  Save state     │  บันทึก                      │  save                 │
│  End session    │  จบ session                  │  end                  │
└─────────────────┴──────────────────────────────┴───────────────────────┘
```

---

## 🌅 Opening a New Session

```
You:    ถึงไหนแล้ว
Claude: ✓ Done: Designer-002 HI-FI (tokens + 14 components + 7 screens)
        ⏭ Next: banking-tech-lead (OpenAPI + ADRs)
        ⏸ Paused: US-MOCK-001..008 (FE-dev mockup with Sonnet)
        🚧 Blockers: none

        Resume options:
        1. Main track (tech-lead)
        2. Side track (mockup)
        3. Tell me what to do
```

→ **Read, decide, then tell Claude what to do.**

---

## 🚀 Resuming Work

```
You:    ทำต่อ
Claude: [analyzes PROGRESS.md, identifies next step]
        [launches the appropriate agent OR asks ONE clarifying question]
```

→ **Claude executes — no more re-explaining.**

---

## 💾 Mid-Session Checkpoint

```
You:    บันทึก
Claude: [updates docs/PROGRESS.md with]
        - Latest completed phases
        - Current next-agent
        - Any ad-hoc decisions made this session
        ✓ Progress saved → docs/PROGRESS.md
```

→ **Safety net — call before any risky operation.**

---

## 🌙 Ending a Session

```
You:    จบ session
Claude: [runs save flow]
        [shows git commit command]:

        git add docs/PROGRESS.md docs/<modified>
        git commit -m "session: <one-line summary>"

        Next session: พิมพ์ `ถึงไหนแล้ว` แล้วจะ summary ให้
```

→ **You run the commit. Claude doesn't auto-commit.**

---

## 🆘 Synonyms Claude Recognizes

| If you forget the exact word, these all work: |
|---|
| `status` ≈ `ถึงไหนแล้ว` ≈ `สถานะ` ≈ `อยู่ตรงไหน` ≈ `ค้างอะไร` ≈ `where am I` |
| `resume` ≈ `ทำต่อ` ≈ `กลับมาทำต่อ` ≈ `ลุยต่อ` ≈ `continue` |
| `save` ≈ `บันทึก` ≈ `เซฟ` ≈ `เซฟไว้` ≈ `checkpoint` |
| `end` ≈ `จบ session` ≈ `จบเซสชั่น` ≈ `พอแค่นี้` ≈ `wrap up` |

---

## ⚠️ Important Rules

1. **`status` is read-only** — Claude won't launch any agent until you say so.
2. **`save` doesn't git commit** — you run the commit (Claude shows you the command).
3. **No `PROGRESS.md` yet?** — Type `ถึงไหนแล้ว` and Claude offers to create one.
4. **Auto-suggest** — Claude will suggest `บันทึก` after big phases complete. Don't ignore it.

---

## 🗂 Where Things Live

```
docs/PROGRESS.md                        ← single source of truth for session state
.claude/skills/session-continuity/      ← this skill (the playbook)
docs/{pm,ba,sa,design,security,...}/    ← formal handoff artifacts
CLAUDE.md                               ← project instructions (always auto-loaded)
```

---

## 💡 Pro Tips

- **Long task?** Type `บันทึก` every ~30 minutes — context-loss safety net.
- **Ad-hoc decision (e.g., "use Sonnet")?** Tell Claude → Claude appends to PROGRESS.md automatically when you `save`.
- **Switching machines / Claude editions?** `จบ session` first → commit → resume anywhere with `ถึงไหนแล้ว`.
- **Lost?** Just type `ถึงไหนแล้ว`. Worst case Claude reads PROGRESS.md and tells you exactly where you are.

---

## 🔗 More Info

- Full skill playbook: [`../SKILL.md`](../SKILL.md)
- Progress file template: [`./progress-template.md`](./progress-template.md)
- Project workflow: `CLAUDE.md` in project root
