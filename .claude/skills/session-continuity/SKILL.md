---
name: session-continuity
description: Session resume + progress tracking playbook for multi-session AI SDLC projects. Use when user types short commands like `ถึงไหนแล้ว`, `status`, `ทำต่อ`, `resume`, `บันทึก`, `save`, `จบ session`, `end`. Reads/writes `docs/PROGRESS.md` as single source of truth for cross-session state.
---

# Session Continuity — Resume Protocol

Lightweight session-state management for multi-session AI SDLC projects (banking, fintech, or any project running many agent phases over days/weeks).

This skill lets the user resume work with **single-word commands** instead of re-explaining context every session.

## When to Use

- User opens a new Claude Code session and asks "ถึงไหนแล้ว" / "status" / "resume"
- User wants to checkpoint progress before closing a session ("save" / "บันทึก")
- User wants to wrap up a session cleanly ("จบ session" / "end")
- An agent completes a major phase and should update the progress log
- Mid-session, user makes an ad-hoc decision that should survive context loss

## Single Source of Truth

**File:** `docs/PROGRESS.md` (git-tracked, human-readable Markdown)

If this file does NOT exist, the FIRST action on any resume command is to **create it** using [`references/progress-template.md`](references/progress-template.md) — populate from `git log`, `docs/{ba,sa,design,security,pm}/handoff-*.json`, and infer current phase.

---

## Command Vocabulary (Recognize Any of These)

| User intent | Thai | English | Action |
|---|---|---|---|
| **Show status, don't act** | `ถึงไหนแล้ว` `สถานะ` `อยู่ตรงไหน` `ค้างอะไร` | `status` `where am I` | Read PROGRESS.md → 5-10 line summary → **WAIT** for direction |
| **Resume execution** | `ทำต่อ` `กลับมาทำต่อ` `ลุยต่อ` | `resume` `continue` | Read PROGRESS.md → bring back context → execute next step (ask ONE clarifying question if ambiguous) |
| **Checkpoint state** | `บันทึก` `เซฟ` `เซฟไว้` | `save` `checkpoint` | Append current state + ad-hoc decisions to PROGRESS.md; do NOT git commit |
| **Wrap up session** | `จบ session` `จบเซสชั่น` `พอแค่นี้` | `end` `wrap up` | Save + show user the git commit command to run; suggest next session resume |

**Recognition rule:** Be liberal with matching. When unsure between `status` and `resume`, default to `status` (safer — non-destructive).

---

## Behavior Per Command

### 1. `status` / `ถึงไหนแล้ว` — read-only summary

```
1. Read docs/PROGRESS.md
2. Run: git log --oneline -5
3. Read latest handoff-*.json from docs/{pm,ba,sa,design,security,...}/
4. Emit summary in this exact shape:

   ✓ Done: [last 3 completed phases]
   ⏭ Next: [next_agent from latest handoff]
   ⏸ Paused: [side tracks if any]
   🚧 Blockers: [from PROGRESS.md, if any]

   Resume options:
   1. [primary track]
   2. [side track]
   3. (await user direction)

5. STOP. Do NOT launch any agent. Wait for user.
```

**Never** launch agents on `status`. This is a pure read.

### 2. `resume` / `ทำต่อ` — continue execution

```
1. Read docs/PROGRESS.md
2. Identify next_agent from latest handoff
3. Bring back any "Recent Ad-Hoc Decisions" from PROGRESS.md into working memory
4. If next step is unambiguous → execute (delegate to agent or do the work)
5. If ambiguous (e.g., main vs side track paused) → ask ONE concise question:
   "พบ 2 tracks ค้างอยู่:
    1. Main: tech-lead OpenAPI
    2. Side: FE-dev mockup
    เลือก track ไหน?"
6. On user choice → execute
```

**Anti-pattern:** Do NOT ask 5 questions. Do NOT re-summarize everything (user already said "resume"). Get on with it.

### 3. `save` / `บันทึก` — update PROGRESS.md

```
1. Read current docs/PROGRESS.md
2. Update "✅ Done" if a phase just completed
3. Update "⏭ Next" with current next_agent
4. Update "⏸ Paused" if a track was paused this session
5. APPEND to "📝 Recent Ad-Hoc Decisions" with today's date + decision
6. Update "Last updated" line at top
7. Write file
8. Confirm: "Progress saved → docs/PROGRESS.md"
9. Do NOT git commit (user runs that themselves)
```

### 4. `end` / `จบ session` — save + git hint

```
1. Run `save` flow above
2. Show user the git command:
   git add docs/PROGRESS.md docs/{any other modified files}
   git commit -m "session: <one-line summary>"
3. Suggest next session resume:
   "ครั้งหน้าเปิด session ใหม่ พิมพ์ `ถึงไหนแล้ว` แล้ว Claude จะ summary ให้"
4. STOP
```

---

## PROGRESS.md Structure (mandatory shape)

The file MUST follow this layout for the protocol to work — see [`references/progress-template.md`](references/progress-template.md) for the canonical template.

```markdown
# Progress — <project-name>

> Last updated: <YYYY-MM-DD> · Branch: <git branch>

## ⚡ Quick Resume

**Main flow:** <one-line current state>
**Side tracks:** <one-line for each paused track, or "none">

## 🎯 Active Sprint

<sprint identifier> · feature: `<feature-slug>`

## ✅ Done

- <phase-id> (<one-line summary>)
- ...

## ⏭ Next

- [ ] <next agent> → <expected deliverable>
- [ ] (parallel) <other agent>

## 🚧 Blockers / Decisions Pending

(none) OR list

## 📝 Recent Ad-Hoc Decisions

- YYYY-MM-DD: <decision>
- ...

## 🔗 Key Artifacts

- Latest handoff: <path>
- ...
```

**Critical fields for parsing:**
- `## ⚡ Quick Resume` — first thing user/Claude reads on `status`
- `## ⏭ Next` — drives `resume` command behavior
- `## 📝 Recent Ad-Hoc Decisions` — append-only log of in-session choices that aren't in formal artifacts

---

## When to Auto-Suggest `save`

Claude SHOULD proactively suggest `บันทึก / save` when:

1. **Phase complete** — agent emits handoff with `quality_gate_passed: true`
2. **Ad-hoc decision made** — user picks an option that's not in any artifact (e.g., "use Sonnet for FE", "pause this track")
3. **Long session** — every ~10 exchanges, gentle nudge
4. **User signals fatigue** — "หยุดก่อน" / "พักก่อน" / "ไว้ทำต่อ"

**Anti-pattern:** Don't nag. One reminder per session is enough.

---

## Integration with Banking Workflow

This skill is **complementary** to the agent workflow defined in `CLAUDE.md`:

- `banking-player` (orchestrator) uses this skill at session boundaries
- Each `banking-<role>` agent's handoff JSON is the formal artifact
- PROGRESS.md is the **lightweight human-readable index** over those artifacts
- Agents don't need to update PROGRESS.md themselves — orchestrator does it on `save` / `end`

---

## Bootstrap (first time `PROGRESS.md` doesn't exist)

When user types `status` and there's no PROGRESS.md:

```
1. Acknowledge: "ยังไม่มี PROGRESS.md — สร้างให้เลยไหม? (Y/n)"
2. On Y: create from references/progress-template.md
3. Populate by:
   - git log --oneline -20  → infer completed phases from commit messages
   - find docs/ -name "handoff-*.json"  → list completed agent phases
   - Latest handoff metadata.next_agent  → "⏭ Next"
4. Show resulting file to user for review
5. Suggest: git add + commit
```

---

## Quick Reference Card

See [`references/commands-cheatsheet.md`](references/commands-cheatsheet.md) for a printable 1-page summary user can pin to their workspace.

---

## Anti-Patterns

- ❌ Treating `status` as `resume` — never launch agents on read-only commands
- ❌ Re-summarizing the entire conversation on `resume` — user already has context, just continue
- ❌ Updating PROGRESS.md without user consent — `save` is explicit (or auto-suggested + confirmed)
- ❌ Git committing automatically — user always runs the commit
- ❌ Adding fields not in the template — keep PROGRESS.md parseable
- ❌ Nagging every 3 messages to save — once per session is fine
- ❌ Making the user re-explain ad-hoc decisions across sessions — that's literally what this skill prevents

---

## References

- [Progress template](references/progress-template.md) — canonical PROGRESS.md shape
- [Commands cheatsheet](references/commands-cheatsheet.md) — 1-page printable reference

## Cross-references

- Project orchestrator playbook: [`CLAUDE.md`](../../../CLAUDE.md)
- Agent registry: [`.claude/agents/`](../../agents/)
- Handoff schema (formal artifact format): [`docs/architecture/handoff-schema.md`](../../../docs/architecture/handoff-schema.md)
- Session playbook (manual sister doc): [`docs/playbook.md`](../../../docs/playbook.md)
