package com.squeeze.app.di

import android.content.Context
import com.squeeze.app.data.crypto.DatabaseKeyManager
import com.squeeze.app.data.db.MeasurementDao
import com.squeeze.app.data.db.MesocycleDao
import com.squeeze.app.data.db.ProfileDao
import com.squeeze.app.data.db.SqueezeDatabase
import com.squeeze.app.data.db.SqueezeDatabaseFactory
import com.squeeze.app.data.db.WorkoutDao
import com.squeeze.core.program.ProgramGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    // DatabaseKeyManager, Entitlements, BillingManager, AdGate and BodyCompositionRepository
    // all carry @Inject constructors, so Dagger builds them itself. Adding @Provides methods
    // for them as well would bind each type twice and fail the build.

    @Provides
    @Singleton
    fun provideDatabase(context: Context, keyManager: DatabaseKeyManager): SqueezeDatabase =
        SqueezeDatabaseFactory.create(context, keyManager)

    @Provides fun provideMeasurementDao(db: SqueezeDatabase): MeasurementDao = db.measurementDao()
    @Provides fun provideWorkoutDao(db: SqueezeDatabase): WorkoutDao = db.workoutDao()
    @Provides fun provideMesocycleDao(db: SqueezeDatabase): MesocycleDao = db.mesocycleDao()
    @Provides fun provideProfileDao(db: SqueezeDatabase): ProfileDao = db.profileDao()

    @Provides
    @Singleton
    fun provideProgramGenerator(): ProgramGenerator = ProgramGenerator()

    /**
     * Application-scoped coroutine scope for work that must outlive any screen, such as
     * reconciling billing state after a purchase completes while the user is elsewhere.
     */
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
