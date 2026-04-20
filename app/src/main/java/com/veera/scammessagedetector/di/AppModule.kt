package com.veera.scammessagedetector.di

import android.content.Context
import androidx.room.Room
import com.veera.scammessagedetector.data.local.ScamDatabase
import com.veera.scammessagedetector.data.local.ScanHistoryDao
import com.veera.scammessagedetector.data.repository.ScamRepository
import com.veera.scammessagedetector.data.repository.ScamRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt dependency injection module for application-scoped bindings.
 *
 * Installed in [SingletonComponent] so that [ScamDatabase], [ScanHistoryDao],
 * and [ScamRepository] are created once per process and shared across all
 * injection sites (ViewModel, tests, etc.).
 *
 * The abstract class + companion object pattern is required by Hilt because
 * `@Binds` must be on an abstract function (it generates no code, just wires
 * an existing bound type), while `@Provides` must be on a concrete function
 * that actually constructs the object.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /**
     * Binds [ScamRepositoryImpl] as the concrete implementation of [ScamRepository].
     *
     * Hilt generates an efficient delegation — no reflection, no runtime overhead.
     * Swapping implementations for testing only requires replacing this binding.
     */
    @Binds
    @Singleton
    abstract fun bindScamRepository(impl: ScamRepositoryImpl): ScamRepository

    companion object {

        /**
         * Provides the Room database singleton.
         *
         * [fallbackToDestructiveMigration] is intentionally omitted here; add
         * explicit [androidx.room.migration.Migration] objects before shipping
         * a production update that changes the schema.
         *
         * @param context Application context supplied by Hilt's qualifier.
         */
        @Provides
        @Singleton
        fun provideScamDatabase(@ApplicationContext context: Context): ScamDatabase =
            Room.databaseBuilder(
                context,
                ScamDatabase::class.java,
                "scam_detector.db",
            ).build()

        /**
         * Provides the [ScanHistoryDao] from the database singleton.
         *
         * Room generates the DAO implementation; this function simply exposes it
         * to the Hilt graph so [ScamRepositoryImpl] can declare it as an
         * `@Inject constructor` parameter.
         */
        @Provides
        @Singleton
        fun provideScanHistoryDao(database: ScamDatabase): ScanHistoryDao =
            database.scanHistoryDao()
    }
}
