# Gate: Home Assistant push — cleartext, validation, and test action

Base: main  (land AFTER or WITH the ADR-010 networking issue)

## Allowed files (touch ONLY these)
app/src/main/AndroidManifest.xml
app/src/main/res/xml/network_security_config.xml          ← NEW
app/src/main/kotlin/com/zwheel/app/service/HomeAssistantPusher.kt
app/src/main/kotlin/com/zwheel/app/ui/settings/SettingsScreen.kt
app/src/main/kotlin/com/zwheel/app/ui/settings/SettingsViewModel.kt

## Spec
1. HomeAssistantPusher.push(): return a sealed result (Success / AuthFailed /
   Unreachable / BadUrl) instead of Unit-with-swallow. Validate the URL has a
   scheme; treat responseCode !in 200..299 as failure (401 -> AuthFailed). Keep the
   IO dispatcher + timeouts.
2. Cleartext: add a network_security_config permitting cleartext (scoped to private
   address ranges if feasible, else global with a comment referencing ADR-010) and
   reference it from <application android:networkSecurityConfig=...>. This must NOT
   break the release build's permission posture agreed in ADR-010.
3. SettingsViewModel: add testHaConnection() that calls push() with the current
   battery (or a probe value) and exposes a one-shot result state.
4. SettingsScreen: add a "Test connection" button under the HA fields that shows the
   result (success / auth failed / unreachable / bad URL).

## Verify
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
Commit: feat(ha): cleartext support, response validation, and Settings test action
