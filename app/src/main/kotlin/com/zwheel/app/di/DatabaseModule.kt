package com.zwheel.app.di

import android.content.Context
import androidx.room.Room
import com.zwheel.app.data.ride.RideDao
import com.zwheel.app.data.ride.ZWheelDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
