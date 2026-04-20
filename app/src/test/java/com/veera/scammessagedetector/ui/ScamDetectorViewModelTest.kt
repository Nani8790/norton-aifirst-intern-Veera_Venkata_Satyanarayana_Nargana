@file:OptIn(ExperimentalCoroutinesApi::class)

package com.veera.scammessagedetector.ui

import com.veera.scammessagedetector.data.local.ScanHistoryDao
import com.veera.scammessagedetector.data.local.ScanHistoryEntity
import com.veera.scammessagedetector.data.repository.ScamRepositoryImpl
import com.veera.scammessagedetector.model.RiskLevel
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

// =============================================================================
//  MAIN DISPATCHER RULE
//
//  Replaces Dispatchers.Main with a test-controlled dispatcher for the
//  lifetime of each test. This is necessary because viewModelScope internally
//  uses Dispatchers.Main, which does not exist on the JVM unit-test runtime.
//
//  Why UnconfinedTestDispatcher?
//   • Coroutines launched on it start executing immediately (eagerly) on the
//     calling thread — state transitions are visible synchronously in tests.
//   • delay() is governed by a virtual clock inside runTest, so the 2-second
//     delay in performAnalysis() completes instantly without real wall time.
// =============================================================================

class MainDispatcherRule : TestWatcher() {

    val testDispatcher = UnconfinedTestDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

// =============================================================================
//  FAKE DAO — in-memory Room replacement for unit tests
//
//  Implements [ScanHistoryDao] using a [MutableStateFlow]-backed list.
//  No database, no disk I/O, no Android dependencies — runs on plain JVM.
//  Allows the tests to use the real [ScamRepositoryImpl] (and therefore the
//  real heuristic engine) without requiring Hilt or Room instrumentation.
// =============================================================================

private class FakeScanHistoryDao : ScanHistoryDao {

    private val _store = MutableStateFlow<List<ScanHistoryEntity>>(emptyList())

    override fun observeAll(): Flow<List<ScanHistoryEntity>> = _store.asStateFlow()

    override suspend fun insert(entity: ScanHistoryEntity) {
        _store.update { it + entity.copy(id = (it.size + 1).toLong()) }
    }

    override suspend fun deleteById(id: Long) {
        _store.update { list -> list.filter { it.id != id } }
    }

    override suspend fun deleteAll() {
        _store.update { emptyList() }
    }
}

// =============================================================================
//  SCAM DETECTOR VIEWMODEL — UNIT TEST SUITE
//
//  Strategy: inject a real [ScamRepositoryImpl] backed by [FakeScanHistoryDao].
//  This exercises the full analysis pipeline (regex rules, scoring, explanation)
//  without Hilt, Room, or Android instrumentation.
//
//  Covers:
//   1.  Initial state contract
//   2.  Empty-input error guard                              [AI-GENERATED]
//   3.  Loading state emitted before Success
//   4.  Clean everyday message → SAFE                       [AI-GENERATED]
//   5.  Bank alert example → DANGEROUS                      [AI-GENERATED]
//   6.  Single shortened-URL → SUSPICIOUS (threshold guard)
//   7.  onInputChanged after Success → resets to Idle
//   8.  reset() clears input and state
//   9.  onExampleSelected populates inputText
//  10.  DANGEROUS explanation contains actionable safety advice
// =============================================================================

@RunWith(JUnit4::class)
class ScamDetectorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: ScamDetectorViewModel

    @Before
    fun setUp() {
        viewModel = ScamDetectorViewModel(
            repository = ScamRepositoryImpl(FakeScanHistoryDao())
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1 — Baseline contract
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A freshly created ViewModel must start in Idle with an empty input.
     * Any other starting state would mean the ViewModel is pre-loading data
     * that could cause visible flicker on first launch.
     */
    @Test
    fun `initial state is Idle with empty input`() {
        assertEquals(ScamDetectorUiState.Idle, viewModel.uiState.value)
        assertEquals("", viewModel.inputText.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2 — Empty-input guard            [AI-GENERATED]
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * [AI-GENERATED] — Generated by Claude (Anthropic), reviewed by developer.
     *
     * Calling analyzeMessage() with a blank input must immediately emit an
     * Error state without ever transitioning through Loading.
     *
     * The guard fires synchronously inside analyzeMessage() before the
     * coroutine is launched, so no runTest or virtual-time advancement is needed.
     */
    @Test
    fun `analyzeMessage with blank input emits Error without reaching Loading`() {
        viewModel.analyzeMessage()

        val state = viewModel.uiState.value

        assertTrue(
            "Expected Error for blank input, got $state",
            state is ScamDetectorUiState.Error,
        )
        assertTrue(
            "Error message should instruct the user to provide input",
            (state as ScamDetectorUiState.Error).message
                .contains("paste a message", ignoreCase = true),
        )
        assertFalse(
            "Loading must not be emitted for blank input",
            state is ScamDetectorUiState.Loading,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3 — Loading state is visible during analysis
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * With UnconfinedTestDispatcher the launched coroutine runs eagerly up to
     * its first suspension point (delay). At that moment state must be Loading.
     * advanceUntilIdle() drains the virtual clock and completes the analysis.
     */
    @Test
    fun `analyzeMessage transitions through Loading before emitting Success`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.onInputChanged("Hello, how are you today?")

            viewModel.analyzeMessage()
            assertEquals(
                "State must be Loading while the virtual delay is pending",
                ScamDetectorUiState.Loading,
                viewModel.uiState.value,
            )

            advanceUntilIdle()

            assertFalse(
                "State must not still be Loading after analysis completes",
                viewModel.uiState.value is ScamDetectorUiState.Loading,
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 4 — Clean message → SAFE         [AI-GENERATED]
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * [AI-GENERATED] — Generated by Claude (Anthropic), reviewed by developer.
     *
     * A completely normal sentence must trigger no scam rules and be classified
     * as SAFE. The input deliberately avoids every keyword and URL pattern in
     * the rule catalogue, so the engine can only add BASE_SCORE (0.05), which
     * is well below the 0.25 SUSPICIOUS threshold.
     */
    @Test
    fun `clean everyday message is classified as SAFE with low confidence`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val cleanMessage =
                "Hey! Are we still on for lunch at the new Italian place on Friday? " +
                        "I heard the pasta is really good. Let me know what time works for you."

            viewModel.onInputChanged(cleanMessage)
            viewModel.analyzeMessage()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue("Expected Success for clean message, got $state", state is ScamDetectorUiState.Success)

            val result = (state as ScamDetectorUiState.Success).result

            assertEquals("Clean message must be classified as SAFE", RiskLevel.SAFE, result.riskLevel)
            assertTrue(
                "Confidence must be below the SUSPICIOUS threshold (0.25), was ${result.confidence}",
                result.confidence < 0.25f,
            )
            assertTrue("No tokens should be flagged in a clean message", result.flaggedTokens.isEmpty())
            assertTrue(
                "Explanation must state that no indicators were detected",
                result.explanation.contains("no scam indicators", ignoreCase = true),
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 5 — Bank alert → DANGEROUS       [AI-GENERATED]
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * [AI-GENERATED] — Generated by Claude (Anthropic), reviewed by developer.
     *
     * Scoring trace (each category fires once due to deduplication):
     *   BASE_SCORE                    = 0.05
     *   HIGH  — Suspicious Domain     = 0.40
     *   MEDIUM — Urgency Language     = 0.25
     *   MEDIUM — Account Threat       = 0.25
     *   MEDIUM — Credential Phishing  = 0.25
     *   ─────────────────────────────────────
     *   Raw total = 1.20 → coerced to 1.0 → DANGEROUS (≥ 0.60)
     */
    @Test
    fun `bank alert example is classified as DANGEROUS with high confidence`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val bankAlertBody =
                "URGENT: A suspicious login attempt to your Chase account was blocked " +
                        "from IP 213.45.88.12 (Romania). Verify your identity immediately or " +
                        "your account will be suspended: https://chase-secure-login.net/verify"

            viewModel.onInputChanged(bankAlertBody)
            viewModel.analyzeMessage()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue("Expected Success for bank alert, got $state", state is ScamDetectorUiState.Success)

            val result = (state as ScamDetectorUiState.Success).result

            assertEquals("Bank alert must be DANGEROUS", RiskLevel.DANGEROUS, result.riskLevel)
            assertTrue(
                "Confidence must be ≥ 0.60, was ${result.confidence}",
                result.confidence >= 0.60f,
            )
            assertTrue("flaggedTokens must be non-empty", result.flaggedTokens.isNotEmpty())
            assertTrue(
                "Explanation must reference the suspicious domain signal",
                result.explanation.contains("suspicious domain", ignoreCase = true),
            )
            assertTrue(
                "Explanation must advise not to click links",
                result.explanation.contains("do not click", ignoreCase = true),
            )
            assertEquals(
                "Success.inputText must echo the analysed text exactly",
                bankAlertBody,
                (state as ScamDetectorUiState.Success).inputText,
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 6 — Shortened URL alone → SUSPICIOUS (threshold boundary)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * One HIGH signal (shortened URL, +0.40) + BASE (0.05) = 0.45.
     * Sits above SUSPICIOUS (0.25) but below DANGEROUS (0.60).
     * Guards the threshold: a single HIGH signal must not escalate to DANGEROUS.
     */
    @Test
    fun `message with only a shortened URL is classified as SUSPICIOUS`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.onInputChanged("Please review this document: https://bit.ly/3xFk91")
            viewModel.analyzeMessage()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is ScamDetectorUiState.Success)

            val result = (state as ScamDetectorUiState.Success).result
            assertEquals(
                "A single shortened URL must produce SUSPICIOUS, not DANGEROUS",
                RiskLevel.SUSPICIOUS,
                result.riskLevel,
            )
            assertTrue(
                "A flagged token must reference the shortened-URL service",
                result.flaggedTokens.any { it.contains("bit.ly", ignoreCase = true) },
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 7 — Editing input after a result resets to Idle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Once a result is displayed, any keystroke must clear the result pane
     * back to Idle so stale verdicts never linger alongside new input.
     */
    @Test
    fun `onInputChanged after Success resets state to Idle`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.onInputChanged("Hello there!")
            viewModel.analyzeMessage()
            advanceUntilIdle()
            assertTrue(
                "Precondition: must be in Success before editing",
                viewModel.uiState.value is ScamDetectorUiState.Success,
            )

            viewModel.onInputChanged("Hello there! — edited")

            assertEquals(
                "Editing after a result must reset state to Idle",
                ScamDetectorUiState.Idle,
                viewModel.uiState.value,
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 8 — reset() clears input and returns to Idle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The toolbar reset action must return the screen to its exact initial
     * state regardless of what was previously analysed.
     */
    @Test
    fun `reset after Success clears inputText and returns to Idle`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.onInputChanged("Claim your free prize now!")
            viewModel.analyzeMessage()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is ScamDetectorUiState.Success)

            viewModel.reset()

            assertEquals("reset() must return state to Idle", ScamDetectorUiState.Idle, viewModel.uiState.value)
            assertEquals("reset() must clear inputText", "", viewModel.inputText.value)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 9 — onExampleSelected populates inputText
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tapping an example card must populate inputText with the example's body.
     * This is the contract the "✓ Loaded" chip affordance in the UI relies on.
     */
    @Test
    fun `onExampleSelected populates inputText with the example body`() {
        val example = viewModel.exampleMessages.first()
        viewModel.onExampleSelected(example)
        assertEquals(
            "inputText must equal the tapped example's body",
            example.body,
            viewModel.inputText.value,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 10 — DANGEROUS explanation contains actionable safety advice
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The explanation for a DANGEROUS verdict must include concrete next steps,
     * not just a risk badge. Guards the UX requirement that users always
     * receive actionable guidance.
     */
    @Test
    fun `DANGEROUS result explanation contains actionable safety advice`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.onInputChanged(
                "URGENT: Your account has been suspended. Verify your credentials now at " +
                        "https://bank-secure-login.xyz/verify or your account will be permanently closed."
            )
            viewModel.analyzeMessage()
            advanceUntilIdle()

            val result = (viewModel.uiState.value as ScamDetectorUiState.Success).result
            assertEquals(RiskLevel.DANGEROUS, result.riskLevel)
            assertTrue(
                "DANGEROUS explanation must advise not to click links",
                result.explanation.contains("do not click", ignoreCase = true),
            )
            assertTrue(
                "DANGEROUS explanation must reference official channels",
                result.explanation.contains("official", ignoreCase = true),
            )
        }
}
