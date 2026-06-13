package com.zwheel.app.data.ride

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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
