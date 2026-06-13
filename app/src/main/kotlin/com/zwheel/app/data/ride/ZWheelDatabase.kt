package com.zwheel.app.data.ride

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RideSessionEntity::class, RideDataPointEntity::class], version = 1, exportSchema = false)
abstract class ZWheelDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
}
