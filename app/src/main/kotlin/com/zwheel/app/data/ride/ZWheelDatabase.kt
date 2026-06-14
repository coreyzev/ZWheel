package com.zwheel.app.data.ride

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ride_point ADD COLUMN altitude REAL")
    }
}

@Database(
    entities = [RideSessionEntity::class, RideDataPointEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ZWheelDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
}
