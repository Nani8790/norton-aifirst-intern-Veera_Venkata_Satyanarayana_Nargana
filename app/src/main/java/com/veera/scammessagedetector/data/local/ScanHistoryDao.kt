package com.veera.scammessagedetector.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [ScanHistoryEntity].
 *
 * Room generates a concrete implementation of this interface at compile time via KSP.
 * All suspension functions run on whichever coroutine dispatcher Room's executor
 * is configured with (IO by default), so callers on the Main dispatcher are safe.
 */
@Dao
interface ScanHistoryDao {

    /**
     * Emits the full history list ordered newest-first, and re-emits whenever
     * any row in [scan_history] changes. The [Flow] never completes on its own —
     * it's cancelled when the collector's scope is cancelled.
     */
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ScanHistoryEntity>>

    /**
     * Inserts a new scan record. [OnConflictStrategy.REPLACE] handles the
     * unlikely edge-case where an auto-generated id collides.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScanHistoryEntity)

    /**
     * Removes the row identified by [id]. No-op if the id does not exist.
     */
    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Truncates the entire history table. Used by the "Clear history" action.
     */
    @Query("DELETE FROM scan_history")
    suspend fun deleteAll()
}
