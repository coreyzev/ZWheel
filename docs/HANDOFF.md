# ZWheel — Solo Handoff Guide

Updated 2026-06-15. Supersedes the Phase 3/4 handoff (all of that is merged).

---

## 1. Where the project is right now

The app is **feature-complete for the connected-companion vision** and CI-green on
`main`:

- BLE connect / Gemini unlock / keep-alive, live dashboard (speed, battery, cell
  voltage, temps, pitch/roll/yaw), foreground ride service, ride recording to Room,
  ride history + detail with speed-colored GPS route map and full-screen map.
- Home Assistant battery push, Wear OS companion (ambient mode, auto-installs bundled
  in the phone APK).
- Networking posture is now explicit: **ADR-010 (Accepted)** — user-owned, offline-first
  companion; no OEM/vendor cloud, no firmware modification, no analytics; runtime egress
  limited to OpenStreetMap tiles + the user-configured Home Assistant URL.

What's left is the **review backlog**: issues #78–#86 from the 2026-06-15 codebase
review (`docs/reviews/2026-06-15-codebase-review.md`), plus pre-existing feature/research
issues (#74, #48, #26, #17, #14).

---

## 2. Who executes what (IMPORTANT — Codex is unavailable)

**Codex is out of credits until Thursday 2026-06-18.** Until then the normal
architect-loop (Claude specs → Codex builds) does not apply. **Claude (Sonnet) is the
implementer.** This is the documented exception to AGENTS.md's "Claude implementing
directly is near-last-resort" rule — it holds only while Codex is unavailable.

**Gemini** may be used for *trivial, mechanical, low-risk* tasks only, and only where the
issue's gate explicitly says "Gemini OK." Do not give Gemini security-sensitive code
(crypto, tokens, the BLE write allowlist), architecture decisions, or anything needing
cross-file judgment.

Each issue body (#78–#86) contains a self-contained gate spec **and** a suitability line
(Claude / Gemini-OK / not-Gemini). The matrix:

| # | What | Suitability | Depends on |
|---|------|-------------|------------|
| 78 | Rewrite network guard to enforce ADR-010 | Claude (Gradle + policy judgment) | ADR-010 ✅ done |
| 79 | Request fine-location so GPS works on API 31+ | Claude (Compose permission flow) | — |
| 80 | Encrypt HA token at rest | **Claude only** (security-sensitive) | — |
| 81 | HA push: cleartext + validation + test action | Claude (network + security) | land with/after #78 |
| 82 | Split ZWheelAppScreen.kt under 500 lines | Gemini-OK (pure extraction) | — |
| 83 | Wear round-trip + HA push tests | Claude (test design + refactor) | easier after #80/#81 |
| 84 | Version the bundled wear app with the phone | Gemini-OK | **moot if #86 removes wearApp()** |
| 85 | Service decomposition (Part B) | Claude (Part A decided — ADR-011) | ADR-011 ✅ done |
| 86 | Watch app never appears — drop embedded wearApp() delivery | Claude or Gemini-OK | — |

**Already resolved this session (no action needed):** #85 Part A (ownership model) is
decided in **ADR-011** — `ConnectionManager` is the single source of truth; only Part B
(the mechanical service split) remains. The BLE debug panel auto-show bug is **fixed on
main** (commit efae4ac — decoupled from permission state, now an explicit toggle).

---

## 2a. Priority & execution order (do them in this order)

**P0 — connected-app rails (do first; these are user-facing breakage).**
1. **#79 — GPS fine-location request.** Highest priority: GPS is *completely dark* for
   users on Android 12+ right now. Smallest blast radius, biggest user-visible win.
2. **#80 — encrypt HA token.** Security; a leaked HA long-lived token can control the
   user's whole home. Claude only.
3. **#81 — HA push robustness (cleartext + validation + test action).** Makes the HA
   feature actually work on a typical local-HTTP instance and tells the user why when it
   doesn't. Pairs naturally with #80 (same Settings/HA surface).
4. **#78 — network guard rewrite.** Lower urgency (CI hygiene, not user-facing), but it's
   cheap and the ADR is already done, so close it out in the same P0 sweep.

**P1 — hygiene & hardening (after P0, parallelizable).**
5. **#86 — drop embedded `wearApp()` delivery + fix watch install docs.** The watch app
   does not appear on Wear OS 3+ because embedded delivery is dead there; remove the
   coupling, document adb (dev) + Play (prod). **Do this before #84** — it makes #84
   moot (close #84 once `wearApp()` is gone).
6. **#82 — split ZWheelAppScreen.kt under 500 lines.** Mechanical extraction. Gemini-OK.
7. **#83 — Wear round-trip + HA push tests.** Easier once #80/#81 have settled the HA
   surface; closes the Rule 9 gap.

**P2 — architecture (decision done; safe to execute).**
8. **#85 Part B — service decomposition.** Part A is **decided (ADR-011)**:
   `ConnectionManager` is the single source of truth, `RideServiceRepository` keeps only
   service-derived state. The remaining work is the mechanical split (extract HA sync,
   location, notifications) **plus** dropping the board/connection mirror collectors and
   repointing `DashboardViewModel` at `ConnectionManager`. See ADR-011.

**P3 — pre-existing backlog (independent of the review).**
9. **#74** (notification battery bar), then **#48** (Live Updates/Now Bar).
10. **#26 / #17 / #14** research — parked, blocked on hardware capture (Corey).

One-line summary: **#79 → #80 → #81 → #78 (P0 rails), then #86 → #82/#83 in parallel
(#86 moots #84 — close it), then #85 Part B, then #74/#48.**

---

## 3. Implementing directly (while Codex is down)

Even without Codex, **write the gate spec first** — it's already embedded in each issue,
so copy it into `docs/gates/gate-<name>.md` before starting. The gate is the contract;
honor its "Allowed files" list.

```bash
cd /root/ZWheel && git fetch origin
git checkout -b fix/<issue-slug> origin/main
# ... implement exactly the gate's Allowed files ...
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin   # or :app:test
git add <files> && git commit -m "<conventional message>"   # NO Co-Authored-By
git push origin fix/<issue-slug>
gh pr create --base main --fill && gh pr merge <N> --squash --admin
gh issue close <N> --comment "Done in #<PR>."
```

Trivial doc/build one-liners may be committed straight to `main` (that's how the recent
fixes landed). Anything touching app logic goes through a PR + CI.

If /tmp fills up: `rm -rf /tmp/zwheel-gradle /tmp/gradle-home /tmp/gradle-wrapper-cache`
frees ~2.5 GB.

---

## 4. Pre-existing backlog (not from the review)

- **#74** battery progress bar in the expanded notification — small, Gemini-OK.
- **#48** surface active ride via Android Live Updates / Samsung Now Bar — medium.
- **#26 / #17 / #14** research items — **blocked on hardware capture** (Corey). Leave
  parked until a capture session.

---

## 5. Guardrails (non-negotiable — from AGENTS.md)

- **Never** add a firmware/OTA UUID. No new writable characteristics without an ADR +
  Corey sign-off. `OwUuidsTest` enforces the allowlist.
- `core/` stays Android-free (CI enforces).
- One concern per commit/PR. **No `Co-Authored-By` lines.**
- Never merge without CI green AND a diff skim.
- Networking follows **ADR-010**: no OEM/vendor cloud, no firmware modification, no
  analytics; egress limited to OSM tiles + the user HA URL. (The old "no INTERNET in
  release" guardrail is retired — do not reintroduce it.)

---

## 6. Verify / merge ritual

```bash
gh pr checks <N>
gh pr diff <N>            # skim
gh pr merge <N> --squash --admin
```

For a fresh branch off current `main`, push → PR → wait for CI → squash-merge. If `main`
moved under you: `git pull --rebase origin main` before pushing.

---

## 7. Useful paths

- Open issues with embedded gate specs: `gh issue view <78..85>`
- Review summary: `docs/reviews/2026-06-15-codebase-review.md`
- ADRs: `docs/adr/` (ADR-010 is the networking decision)
- Gate specs: `docs/gates/`
