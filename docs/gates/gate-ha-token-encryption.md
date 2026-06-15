# Gate: Encrypt Home Assistant token at rest

Base: main

## Allowed files (touch ONLY these)
gradle/libs.versions.toml
app/build.gradle.kts
app/src/main/kotlin/com/zwheel/app/data/settings/SettingsRepository.kt
app/src/main/kotlin/com/zwheel/app/di/SettingsModule.kt   ← also needed to wire Context
app/src/main/AndroidManifest.xml

## Spec
1. Add androidx.security:security-crypto (stable 1.0.0) to the version catalog + app deps.
2. In SettingsRepository, store/read HA_TOKEN via an EncryptedSharedPreferences
   instance backed by a MasterKey (AES256_GCM). Keep the public suspend setHaToken()
   / Flow getter signatures identical; only the storage backing changes. The HA_URL
   stays in DataStore (not secret).
   - setHaToken: write to EncryptedSharedPreferences, then clear the plaintext HA_TOKEN
     from DataStore (lazy migration path for existing installs).
   - preferences Flow: read token from EncryptedSharedPreferences; fall back to DataStore
     legacy key if encrypted store is empty (covers upgrades).
3. android:allowBackup="false" is already set — verify only.
4. SettingsModule: pass @ApplicationContext context to SettingsRepository constructor.

## Verify
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew --no-daemon :app:compileDebugKotlin
Commit: fix(security): encrypt Home Assistant token at rest
