package com.veera.scammessagedetector.data.repository

import com.veera.scammessagedetector.data.local.ScanHistoryEntity
import com.veera.scammessagedetector.model.AnalysisResult
import com.veera.scammessagedetector.model.DeepAnalysisResult
import kotlinx.coroutines.flow.Flow

/**
 * Contract for all scam-detection business logic and local data access.
 *
 * Abstracting behind an interface achieves two goals:
 *  1. **Testability** — tests can inject a fake implementation without touching
 *     Room or the heuristic engine.
 *  2. **Replaceability** — swapping the heuristic engine for a server-side model
 *     only requires a new [ScamRepository] implementation and a Hilt binding change;
 *     the ViewModel is untouched.
 *
 * The concrete binding is configured in [com.veera.scammessagedetector.di.AppModule].
 */
interface ScamRepository {

    /**
     * Runs the heuristic rule engine against [text] and returns a verdict.
     *
     * This is a CPU-bound operation; callers should switch to [kotlinx.coroutines.Dispatchers.Default]
     * before invoking (the ViewModel handles this transparently).
     *
     * **Time complexity:** O(R × N) where R = number of rules and N = length of [text].
     * Each rule compiles to a finite-state automaton (Kotlin delegates to Java's
     * `java.util.regex` engine, which uses NFA-based backtracking). Worst-case
     * backtracking on adversarially crafted input is O(2^N), but the patterns here
     * use possessive quantifiers and bounded alternation to stay linear in practice.
     *
     * @param text Raw user-supplied message text. Must not be blank.
     * @return     A fully populated [AnalysisResult] with risk level, confidence,
     *             human-readable explanation, and a list of flagged tokens.
     */
    suspend fun analyze(text: String): AnalysisResult

    /**
     * Strips characters commonly used in Indirect Prompt Injection attacks before
     * passing user-controlled text to an AI model.
     *
     * Defends against:
     *  - Null bytes and non-printable ASCII control characters.
     *  - Zero-width characters (used to hide injected instructions from readers).
     *  - RTL override (used to visually disguise malicious URLs as benign ones).
     *  - Context-window padding via excessive blank lines.
     *
     * @param text Raw user input.
     * @return     Sanitized text safe for inclusion in an AI prompt.
     */
    fun sanitize(text: String): String

    /**
     * Produces a contextually appropriate [DeepAnalysisResult] by pattern-matching
     * dominant social-engineering signals in [sanitizedText].
     *
     * This is the offline/simulated counterpart to a real AI API call.
     * Replace this with a `parseAiResponse()` call once the live integration
     * socket in [ScamRepositoryImpl.performDeepScanSocket] is wired up.
     *
     * @param sanitizedText Output of [sanitize] — must not contain injection characters.
     */
    fun simulateDeepResult(sanitizedText: String): DeepAnalysisResult

    /**
     * A cold [Flow] that emits the full scan history ordered newest-first,
     * and re-emits on every database change. Backed by Room's reactive queries.
     */
    fun getHistory(): Flow<List<ScanHistoryEntity>>

    /**
     * Persists a completed analysis to the local database.
     *
     * @param inputText The original message text that was scanned.
     * @param result    The verdict produced by [analyze].
     */
    suspend fun saveToHistory(inputText: String, result: AnalysisResult)

    /**
     * Removes the history entry with the given [id]. No-op if [id] is not found.
     */
    suspend fun deleteHistoryEntry(id: Long)

    /**
     * Removes all history entries. Irreversible — use only after user confirmation.
     */
    suspend fun clearHistory()
}
