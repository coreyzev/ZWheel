# Gate: Replace verifyNetworkPermissionScoping with an ADR-010 policy guard

Base: main

## Allowed files (touch ONLY these)
app/build.gradle.kts

## Context
ADR-010 (Accepted) permits INTERNET, with egress limited by policy to OpenStreetMap
tiles + the user-configured Home Assistant URL. The HA host is runtime user input and
is never hardcoded. The old guard checked a non-existent release overlay manifest and
is vacuous.

## Spec
Rewrite the verifyNetworkPermissionScoping task (keep it wired into tasks.check) so it
enforces ADR-010 instead:
1. Scan tracked app + core + wear .kt source for hardcoded http(s):// URLs. Fail the
   build if any hardcoded host is found that is NOT an OpenStreetMap/OSMDroid tile host.
2. Fail the build if any analytics/crash/ads dependency appears in app/build.gradle.kts.
3. Rename/redescribe the task to reflect it now enforces ADR-010 network policy.
4. Remove the dead release-overlay manifest check.

## Verify
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:check
Commit: build: enforce ADR-010 network policy instead of vacuous release-manifest check
