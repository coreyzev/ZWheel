# Gate: Phase 3 — Room Schema and Ride Repositories

**Branch:** `codex/p3-room-schema`
**One concern:** Wire Room into the project: entities, DAOs, database, and repositories behind interfaces in `app/data`.

---

## Allowed files (touch ONLY these)

```
gradle/libs.versions.toml                                           ← add Room version + libs
app/build.gradle.kts                                                ← add Room deps
app/src/main/kotlin/com/zwheel/app/data/ride/RideEntities.kt        ← new: Room entities
app/src/main/kotlin/com/zwheel/app/data/ride/RideDao.kt             ← new: DAOs
app/src/main/kotlin/com/zwheel/app/data/ride/ZWheelDatabase.kt      ← new: @Database
app/src/main/kotlin/com/zwheel/app/data/ride/RideRepository.kt      ← new: repository
app/src/main/kotlin/com/zwheel/app/di/DatabaseModule.kt             ← new: Hilt @Provides
app/src/test/kotlin/com/zwheel/app/data/ride/RideDaoTest.kt         ← new: unit tests
```

---

## Dependency additions

### `gradle/libs.versions.toml`

Add under `[versions]`:
```toml
room = "2.7.1"
```

Add under `[libraries]`:
```toml
androidx-room-runtime  = { module = "androidx.room:room-runtime",  version.ref = "room" }
androidx-room-ktx      = { module = "androidx.room:room-ktx",      version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler",  version.ref = "room" }
```

### `app/build.gradle.kts`

Add to `dependencies {}`:
```kotlin
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)
```

---

## Entity spec

### `RideEntities.kt`

Two entities matching `core/model/RideSession` and `core/model/RideDataPoint`:

```kotlin
@Entity(tableName = "ride_session")
data class RideSessionEntity(
    @PrimaryKey val id: String,
    val boardId: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long?,
    val maxSpeedMetersPerSecondCorrected: Double,
    val distanceMetersCorrected: Double,
    val distanceMetersRaw: Double,
    val wattHoursUsed: Double?,
    val notes: String?,
)

@Entity(tableName = "ride_point", indices = [Index("sessionId")])
data class RideDataPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val epochMillis: Long,
    val speedMetersPerSecondCorrected: Double?,
    val speedMetersPerSecondRaw: Double?,
    val rpm: Double?,
    val batteryPercent: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val amps: Double?,
    val pitchDegrees: Double?,
    val rollDegrees: Double?,
    val controllerTempCelsius: Double?,
    val motorTempCelsius: Double?,
)
```

---

## DAO spec

### `RideDao.kt`

```kotlin
@Dao
interface RideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RideSessionEntity)

    @Update
    suspend fun updateSession(session: RideSessionEntity)

    @Query("SELECT * FROM ride_session WHERE id = :id")
    suspend fun getSession(id: String): RideSessionEntity?

    @Query("SELECT * FROM ride_session WHERE endEpochMillis IS NULL LIMIT 1")
    suspend fun getOpenSession(): RideSessionEntity?

    @Query("SELECT * FROM ride_session ORDER BY startEpochMillis DESC")
    fun getAllSessions(): Flow<List<RideSessionEntity>>

    @Insert
    suspend fun insertPoint(point: RideDataPointEntity)

    @Query("SELECT * FROM ride_point WHERE sessionId = :sessionId ORDER BY epochMillis ASC")
    fun getPointsForSession(sessionId: String): Flow<List<RideDataPointEntity>>
}
```

---

## Database

### `ZWheelDatabase.kt`

```kotlin
@Database(entities = [RideSessionEntity::class, RideDataPointEntity::class], version = 1)
abstract class ZWheelDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
}
```

---

## Repository

### `RideRepository.kt`

Thin wrapper around `RideDao`, converting between entities and `core/model` types:

```kotlin
@Singleton
class RideRepository @Inject constructor(private val dao: RideDao) {
    suspend fun startSession(session: RideSession) { dao.insertSession(session.toEntity()) }
    suspend fun updateSession(session: RideSession) { dao.updateSession(session.toEntity()) }
    suspend fun getOpenSession(): RideSession? = dao.getOpenSession()?.toModel()
    fun getAllSessions(): Flow<List<RideSession>> = dao.getAllSessions().map { list -> list.map { it.toModel() } }
    suspend fun insertPoint(point: RideDataPoint) { dao.insertPoint(point.toEntity()) }
    fun getPointsForSession(sessionId: String): Flow<List<RideDataPoint>> =
        dao.getPointsForSession(sessionId).map { list -> list.map { it.toModel() } }
}
```

Write `toEntity()` / `toModel()` extension functions as private helpers in the same file.

---

## Hilt module

### `DatabaseModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ZWheelDatabase =
        Room.databaseBuilder(context, ZWheelDatabase::class.java, "zwheel.db").build()

    @Provides
    fun provideRideDao(db: ZWheelDatabase): RideDao = db.rideDao()
}
```

---

## Tests

`RideDaoTest.kt` — use `Room.inMemoryDatabaseBuilder` (requires `androidx-test-core` and `robolectric`, which are ALREADY in the project if not then add `testImplementation(libs.robolectric)` and `testImplementation(libs.androidx.test.core)` to allowed files and add the missing libs to `libs.versions.toml`).

Tests must cover:
- Insert + retrieve a `RideSessionEntity`
- `getOpenSession()` returns null when no open sessions, returns the open one when present
- `getAllSessions()` returns sessions in descending start order

If Robolectric is not present, skip DAO tests and add a `// TODO(m3): add in-memory DAO tests once Robolectric is wired` comment to `RideDaoTest.kt`.

---

## Constraints

- `core/` is untouched — entities live in `app/data/ride/`, not `core/`.
- All Room access goes through `RideRepository`, never via DAO directly from a ViewModel or service.
- No Android imports in `core/`. The entities and DAOs are Android artifacts in `app/`.
- Zero changes to existing source files except `libs.versions.toml` and `app/build.gradle.kts`.

---

## Verification

```bash
./gradlew :app:compileDebugKotlin :app:kspDebugKotlin
```

Both must pass (KSP generates Room code). Fix any errors before reporting done.
