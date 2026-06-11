# ZWheel — Corey's Runbook
Your exact steps, in order. Everything not listed here is the agent's job.

## 0. One-time setup (≈ 1 hour)
1. Create a fresh GitHub repo (e.g. `zwheel`). Public, GPLv3, empty.
2. Drop the five docs from this package into `/docs`, and `03_AGENTS.md` ALSO at repo
   root as `AGENTS.md` (agents look for it there).
3. Install on your Mac/PC: Android Studio (latest stable), JDK 17, Android SDK +
   platform tools. Enable Developer Options + USB debugging on the S25 Ultra. Pair the
   Watch 7 Classic for Wear debugging (Wear OS: developer options → ADB over Wi-Fi, or
   debug over the phone).
4. Pick your executor:
   - **Recommended:** Claude Code in the repo directory. First prompt:
     > Read AGENTS.md, docs/01_PROJECT_BRIEF.md, docs/02_ARCHITECTURE.md and
     > docs/04_BUILD_PLAN.md. Confirm your understanding in 5 bullets, then begin
     > Phase 0. Stop at gate M0 and give me a review checklist.
   - **If you still want Hermes:** point it at the repo with the same prompt; AGENTS.md
     is its contract. Nothing else changes. (My honest take: you don't need it —
     single-stack project, one good agent is simpler and the orchestration overhead
     buys you nothing here.)
5. Decide branch policy when the agent asks: trunk-based with direct commits is fine
   for a solo project; PRs if you want review points.

## 1. Your job at each gate (from 04_BUILD_PLAN.md)
- **M0 (no hardware):** skim `core` interfaces and `OwUuids.kt` — every UUID should
  cite where it came from. ~15 min.
- **M1 (the big one):** install the debug APK (`adb install` or Studio ▶), put your XR
  near the phone, run the debug connect screen. Success = board unlocks AND STAYS
  unlocked with live data 10+ min. Then run the agent's capture step and commit the
  fixture file it produces. If unlock fails: send the agent the full log; the answer is
  almost always in ponewheel issue #86's comment thread — tell it to re-read that.
- **M2:** ride a known route (~2 mi) with a GPS app running for ground truth. Before
  riding, set your real tire outer diameter in Settings (measure it: tire height with
  your PSI, or manufacturer spec). Compare distances; ≤ ~2% error passes.
- **M3:** the torture tests, exactly as listed in the plan. Do them with your phone in
  default Samsung battery mode AFTER completing the app's battery onboarding — that's
  the realistic condition.
- **M4:** full ride with the watch. The pass condition is yours personally: speed, top
  speed, battery, range visible the entire ride without touching the phone.
- **M5:** daily-drive a tagged build for a week, file issues, then tag v1.0.0.

## 2. Hardware truths to remember
- BLE never works in emulators. Anything BLE = your hands, physical board.
- Don't run the official FM app anymore (it's the firmware-update vector). ZWheel
  cannot update firmware by design — that's enforced by a unit test on the UUID
  allowlist.
- When you switch to a Pixel later this year: re-run the M3 torture tests once.
  Expect them to pass more easily than on Samsung.

## 3. Release (Phase 5, one-time)
1. Generate a signing keystore (Studio → Build → Generate Signed Bundle/APK). Back it
   up somewhere safe — losing it means users can't update.
2. Add the keystore secrets to GitHub Actions per the agent's instructions.
3. Tag `v1.0.0` → CI uploads the signed APK to GitHub Releases. Share the link.
4. Donate button: a Ko-fi/GitHub Sponsors link in the About screen — agent wires it,
   you create the account.

## 4. If things go sideways
- Agent looping on a bug → invoke AGENTS.md Rule 12 by name ("write your reflection,
  change approach").
- Agent claims something works on hardware → it can't know that; re-test yourself.
- Connection unstable in M1 → before blaming code: board charged, official app fully
  force-stopped (it competes for the BLE connection), phone within 2 m.
- Scope creep temptation (maps! GPX! GT support!) → it's in the backlog with a seam
  waiting. Ship v1 first.
