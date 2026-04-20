package com.veera.scammessagedetector.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single persisted scan result.
 *
 * [flaggedTokens] is stored as a pipe-delimited string rather than a separate
 * junction table because the list is small, read-only after insert, and always
 * fetched with its parent row — a relational join would add complexity without benefit.
 *
 * @param id            Auto-generated primary key.
 * @param inputText     The raw message text that was analysed.
 * @param riskLevel     Serialised [com.veera.scammessagedetector.model.RiskLevel] name.
 * @param confidence    Engine confidence score in [0.0, 1.0].
 * @param explanation   Human-readable verdict summary.
 * @param flaggedTokens Pipe-delimited (`|`) list of matched signal tokens.
 * @param timestamp     Unix epoch milliseconds at time of scan.
 */
@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inputText: String,
    val riskLevel: String,
    val confidence: Float,
    val explanation: String,
    val flaggedTokens: String,
    val timestamp: Long = System.currentTimeMillis(),
)
