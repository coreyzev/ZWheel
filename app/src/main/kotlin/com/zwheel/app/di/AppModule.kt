package com.zwheel.app.di

import com.zwheel.app.SystemClock
import com.zwheel.app.ble.KableBleTransport
import com.zwheel.core.calc.DefaultRangeEstimator
import com.zwheel.core.calc.RangeEstimator
import com.zwheel.core.ports.BleTransport
import com.zwheel.core.ports.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideKableBleTransport(): KableBleTransport = KableBleTransport()

    @Provides
    @Singleton
    fun provideBleTransport(transport: KableBleTransport): BleTransport = transport

    @Provides
    @Singleton
    fun provideClock(clock: SystemClock): Clock = clock

    @Provides
    @Singleton
    fun provideRangeEstimator(): RangeEstimator = DefaultRangeEstimator
}
