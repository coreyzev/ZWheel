package com.zwheel.app.di

import com.zwheel.app.service.RideServiceController
import com.zwheel.app.service.RideServiceControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {
    @Binds
    @Singleton
    abstract fun bindRideServiceController(impl: RideServiceControllerImpl): RideServiceController
}
