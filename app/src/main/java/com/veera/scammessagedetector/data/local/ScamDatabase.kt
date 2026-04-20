package com.veera.scammessagedetector.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database singleton for the Scam Message Detector.
 *
 * Declared abstract — Room's KSP processor generates the concrete implementation
 * (ScamDatabase_Impl) at compile time. The singleton lifecycle is managed by Hilt
 * via [com.veera.scammessagedetector.di.AppModule.provideScamDatabase].
 *
 * Schema versioning:
 *  - v1: Initial schema with [ScanHistoryEntity].
 *  - [exportSchema] is `false` for this demo; set to `true` and commit the schema
 *    JSON to source control for production apps that ship database migrations.
 */
@Database(
    entities = [ScanHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ScamDatabase : RoomDatabase() {

    /** Returns the DAO for scan history CRUD operations. */
    abstract fun scanHistoryDao(): ScanHistoryDao
}
