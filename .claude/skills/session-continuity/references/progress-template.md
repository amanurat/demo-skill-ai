# PROGRESS.md — Canonical Template

> This is the **template** for `docs/PROGRESS.md`. Copy this structure when bootstrapping for the first time.

The protocol in [`../SKILL.md`](../SKILL.md) parses these sections in this exact order. Do not rename headings; do not add new top-level sections without updating the SKILL.

---

## Template (copy below this line to `docs/PROGRESS.md`)

```markdown
# Progress — <project-name>

> Last updated: <YYYY-MM-DD HH:mm> · Branch: <git branch> · Session: <number-or-name>

## ⚡ Quick Resume

**Main flow:** <one-line current state — what phase, what next agent>
**Side tracks:** <one-line for each paused track, or "none">

---

## 🎯 Active Sprint

`<sprint-id>` · feature: `<feature-slug>` · dates: `<start>` → `<end>`

---

## ✅ Done

- <PHASE-ID> — <one-line summary> (commit: `<hash>`)
- <PHASE-ID> — <one-line summary> (commit: `<hash>`)
- ...

---

## ⏭ Next

- [ ] <next-agent> → <expected deliverable>
- [ ] (parallel) <other-agent> → <deliverable>

---

## ⏸ Paused / Side Tracks

| Track ID | What | Why paused | How to resume |
|---|---|---|---|
| <ID> | <description> | <reason> | <action to resume> |

(or `(none)` if no paused tracks)

---

## 🚧 Blockers / Decisions Pending

- <BLOCKER-ID>: <description> — owner: <who> — needs: <what>

(or `(none)`)

---

## 📝 Recent Ad-Hoc Decisions

> Append-only log of in-session choices that aren't in formal artifacts. Add at the TOP (newest first).

- **YYYY-MM-DD HH:mm** — <decision> — context: <why>
- **YYYY-MM-DD HH:mm** — <decision> — context: <why>
- ...

---

## 🔗 Key Artifacts

| Phase | Artifact | Path |
|---|---|---|
| PM | handoff-pm-001.json | `docs/pm/<feature>/handoff-pm-001.json` |
| BA | handoff-ba-001.json | `docs/ba/<feature>/handoff-ba-001.json` |
| SA | handoff-sa-001.json | `docs/sa/<feature>/handoff-sa-001.json` |
| Designer P1 | handoff-designer-001.json | `docs/design/<feature>/handoff-designer-001.json` |
| Designer P2 | handoff-designer-002.json | `docs/design/<feature>/hifi/handoff-designer-002.json` |
| Security | early-review-*.md | `docs/security/<feature>/...` |
| Tech Lead | handoff-tl-*.json | `docs/tl/<feature>/...` |
| ... | ... | ... |

---

## 📚 Session History (optional — keep last 5)

- **Session N (YYYY-MM-DD):** <one-line summary> — commits: `<range>`
- **Session N-1 (YYYY-MM-DD):** <one-line summary> — commits: `<range>`
- ...
```

---

## Bootstrap Heuristics

When creating PROGRESS.md for the first time, populate sections by inferring from project state:

### ✅ Done section

```bash
# Infer completed phases from handoff files
find docs -name "handoff-*.json" -type f | sort

# Map each handoff to a "Done" entry:
# docs/pm/<feature>/handoff-pm-001.json → "PM-001 — backlog + risk register"
# docs/ba/<feature>/handoff-ba-001.json → "BA-001 — user stories + AC + NFR"
# docs/sa/<feature>/handoff-sa-001.json → "SA-001 — architecture + ADRs"
# ...
```

### ⏭ Next section

Read the LATEST handoff (sort by mtime), extract `metadata.next_agent` field, list as next step.

### 🎯 Active Sprint

Look for `sprint-goal.md` or `backlog.md` under `docs/pm/` — extract sprint identifier + feature slug.

### Last updated line

Use current date + `git rev-parse --abbrev-ref HEAD` for branch.

---

## Maintenance Rules

1. **Append, don't rewrite** — Ad-Hoc Decisions section grows over time; keep history
2. **Keep "✅ Done" newest-last** — chronological order, oldest first
3. **Trim "Session History" at 5 entries** — older sessions live in `git log`, not here
4. **One PROGRESS.md per project root** — not per feature; multi-feature projects use sections per feature
5. **Update on every `save` / `end` command** — see SKILL.md §"Behavior Per Command"

---

## What NOT to Put Here

- ❌ Long form text — keep entries one-line; deep content goes in handoff JSON or feature docs
- ❌ Code snippets — link to source files instead
- ❌ Stakeholder names / PII — keep this file safe to share
- ❌ Credentials / secrets — never
- ❌ Volatile state (running pod IDs, k8s namespaces) — those belong in observability dashboards

---

## Example (filled, real)

See `docs/PROGRESS.md` in the actual project for a worked example with real banking sprint state.
