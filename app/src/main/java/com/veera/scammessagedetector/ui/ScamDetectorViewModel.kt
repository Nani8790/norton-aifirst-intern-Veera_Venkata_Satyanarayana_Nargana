package com.veera.scammessagedetector.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veera.scammessagedetector.data.local.ScanHistoryEntity
import com.veera.scammessagedetector.data.repository.ScamRepository
import com.veera.scammessagedetector.model.AnalysisResult
import com.veera.scammessagedetector.model.DeepAnalysisResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ---------------------------------------------------------------------------
// UI State — sealed hierarchy
// ---------------------------------------------------------------------------

/**
 * Represents every possible state of the scam-detection UI.
 *
 * Using a sealed class (rather than multiple boolean flags) makes illegal states
 * unrepresentable — the UI can never simultaneously show a loading spinner and a
 * result card.
 */
sealed class ScamDetectorUiState {
    /** Initial state — no analysis has been requested. */
    data object Idle : ScamDetectorUiState()

    /** Heuristic engine is running; UI shows a progress indicator. */
    data object Loading : ScamDetectorUiState()

    /**
     * Heuristic scan completed successfully.
     *
     * @param inputText      The original text that was analysed.
     * @param result         Heuristic engine verdict.
     * @param isDeepScanning `true` while the Deep AI scan coroutine is in-flight.
     *                       The heuristic result card remains fully visible during this time.
     * @param deepResult     Populated once the Deep AI scan completes; `null` before that.
     */
    data class Success(
        val inputText: String,
        val result: AnalysisResult,
        val isDeepScanning: Boolean = false,
        val deepResult: DeepAnalysisResult? = null,
    ) : ScamDetectorUiState()

    /**
     * An error occurred (empty input, or unexpected exception).
     *
     * @param message Human-readable error description surfaced via Snackbar.
     */
    data class Error(val message: String) : ScamDetectorUiState()
}

// ---------------------------------------------------------------------------
// UI-only data model
// ---------------------------------------------------------------------------

/**
 * A pre-written sample message used by the "Try an example" chip strip.
 *
 * This is a UI-layer model because it has no business logic and is never
 * persisted — it exists purely to populate the input field on tap.
 *
 * @param label      Short display name shown on the chip card.
 * @param senderHint Simulated sender name shown below the label.
 * @param body       Full message body that populates the text field when selected.
 */
data class ExampleMessage(
    val label: String,
    val senderHint: String,
    val body: String,
)

// ===========================================================================
//  VIEWMODEL
// ===========================================================================

/**
 * Owns all UI state for [ScamDetectorScreen].
 *
 * All business logic and data access is delegated to [ScamRepository]. The ViewModel
 * is responsible only for:
 *  - Orchestrating coroutine lifecycles (so work survives configuration changes).
 *  - Mapping repository results to [ScamDetectorUiState] transitions.
 *  - Exposing [StateFlow]s that the Compose UI collects.
 *
 * [@HiltViewModel] enables Hilt to manage this ViewModel's lifecycle and inject
 * [ScamRepository] without requiring a custom [androidx.lifecycle.ViewModelProvider.Factory].
 */
@HiltViewModel
class ScamDetectorViewModel @Inject constructor(
    private val repository: ScamRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScamDetectorUiState>(ScamDetectorUiState.Idle)

    /** The current UI state. Collected by the root [ScamDetectorScreen] composable. */
    val uiState: StateFlow<ScamDetectorUiState> = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")

    /** Live text field contents. Two-way binding: VM owns source of truth. */
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    /**
     * Reactive history list backed by Room's [Flow]-based query.
     *
     * [SharingStarted.WhileSubscribed] with a 5-second timeout keeps the upstream
     * [Flow] alive during screen rotation (configuration change restores the collector
     * before the 5 s window expires), while cancelling it promptly when the app
     * truly leaves the foreground.
     */
    val history: StateFlow<List<ScanHistoryEntity>> = repository
        .getHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Static list of pre-written scam examples for the chip strip.
     *
     * Stored in the ViewModel (not a repository) because this data is hard-coded
     * UI copy — not business data — and does not change at runtime.
     */
    val exampleMessages: List<ExampleMessage> = listOf(
        ExampleMessage(
            label = "Package Delivery",
            senderHint = "USPS Alerts",
            body = "USPS: Your package (#92748837) is on hold due to an incomplete " +
                    "delivery address. Update your details within 24 hrs to avoid return: " +
                    "https://usps-delivery-update.com/verify?id=92748837",
        ),
        ExampleMessage(
            label = "Bank Alert",
            senderHint = "Chase Security",
            body = "URGENT: A suspicious login attempt to your Chase account was blocked " +
                    "from IP 213.45.88.12 (Romania). Verify your identity immediately or " +
                    "your account will be suspended: https://chase-secure-login.net/verify",
        ),
    )

    // -------------------------------------------------------------------------
    // Public API — called by the UI layer
    // -------------------------------------------------------------------------

    /**
     * Updates the input text and resets the result state to [ScamDetectorUiState.Idle]
     * if the user modifies the field after a completed analysis.
     *
     * @param newText Latest value from the text field.
     */
    fun onInputChanged(newText: String) {
        _inputText.update { newText }
        if (_uiState.value is ScamDetectorUiState.Success ||
            _uiState.value is ScamDetectorUiState.Error
        ) {
            _uiState.update { ScamDetectorUiState.Idle }
        }
    }

    /**
     * Fills the input field with the body of [example] and resets the result state.
     *
     * Delegates to [onInputChanged] so the reset logic is not duplicated.
     */
    fun onExampleSelected(example: ExampleMessage) {
        onInputChanged(example.body)
    }

    /**
     * Validates the input and triggers the heuristic analysis pipeline.
     *
     * Guards against blank input before transitioning to [ScamDetectorUiState.Loading],
     * preventing unnecessary coroutine launches. The actual work runs in [performAnalysis].
     */
    fun analyzeMessage() {
        val text = _inputText.value.trim()
        if (text.isBlank()) {
            _uiState.update {
                ScamDetectorUiState.Error("Please paste a message or URL to analyze.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { ScamDetectorUiState.Loading }
            performAnalysis(text)
        }
    }

    /**
     * Triggers the Deep AI scan for the currently displayed result.
     *
     * Mutates the *existing* [ScamDetectorUiState.Success] in-place by setting
     * [ScamDetectorUiState.Success.isDeepScanning] to `true`, so the heuristic
     * result card remains fully visible while the AI layer runs beneath it.
     *
     * Guards against stacked scan requests: no-ops if already scanning or if a
     * result is already available.
     */
    fun triggerDeepScan() {
        val current = _uiState.value as? ScamDetectorUiState.Success ?: return
        if (current.isDeepScanning || current.deepResult != null) return

        viewModelScope.launch {
            _uiState.update { state ->
                (state as? ScamDetectorUiState.Success)
                    ?.copy(isDeepScanning = true) ?: state
            }
            performDeepScan(current.inputText)
        }
    }

    /**
     * Clears the input field and resets the UI to [ScamDetectorUiState.Idle].
     * Does not affect persisted history.
     */
    fun reset() {
        _inputText.update { "" }
        _uiState.update { ScamDetectorUiState.Idle }
    }

    /**
     * Removes a single history entry from the database by its primary key.
     *
     * @param id Primary key of the [ScanHistoryEntity] to delete.
     */
    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch { repository.deleteHistoryEntry(id) }
    }

    /**
     * Removes all scan history from the database.
     *
     * Should be called only after presenting a confirmation dialog to the user —
     * this action is irreversible.
     */
    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    // -------------------------------------------------------------------------
    // Internal analysis pipeline
    // -------------------------------------------------------------------------

    /**
     * Runs the heuristic engine on [text] and transitions the UI to
     * [ScamDetectorUiState.Success] on completion, or [ScamDetectorUiState.Error]
     * on failure.
     *
     * The 2-second [delay] simulates real processing latency and ensures the loading
     * animation is visible — remove it once a live AI API is connected.
     *
     * CPU-bound regex matching is dispatched to [Dispatchers.Default] to keep
     * the Main thread free. The subsequent [repository.saveToHistory] call runs
     * on Room's internal IO executor via the suspend function's dispatcher.
     *
     * @param text Trimmed, non-blank user input.
     */
    private suspend fun performAnalysis(text: String) {
        try {
            delay(2_000L)
            val result = withContext(Dispatchers.Default) { repository.analyze(text) }
            _uiState.update {
                ScamDetectorUiState.Success(inputText = text, result = result)
            }
            repository.saveToHistory(text, result)
        } catch (e: Exception) {
            _uiState.update {
                ScamDetectorUiState.Error(
                    message = "Analysis failed: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    /**
     * Executes the Deep AI scan layer for [text].
     *
     * Architecture: this function is a "plug-and-play socket". The simulated block
     * is clearly separated so connecting a live AI model requires only replacing
     * the marked section (see [ScamRepository.simulateDeepResult] for the real
     * integration points).
     *
     * Threading: runs on [Dispatchers.IO] because the real implementation will
     * be a network call. [MutableStateFlow.update] is thread-safe, so the state
     * update inside the IO block is safe.
     *
     * On failure: the scanning flag is cleared but the heuristic result is preserved.
     * A production app should also emit a one-shot error event via a [kotlinx.coroutines.channels.Channel].
     *
     * @param text The original unsanitized input text from the heuristic result.
     */
    private suspend fun performDeepScan(text: String) {
        try {
            val sanitized = repository.sanitize(text)
            withContext(Dispatchers.IO) {
                // ── REAL AI INTEGRATION SOCKET ────────────────────────────────
                // Replace the simulated delay + simulateDeepResult() call below with:
                //
                // OPTION A — Google Gemini:
                //   val model = GenerativeModel("gemini-1.5-pro", BuildConfig.GEMINI_API_KEY)
                //   val response = model.generateContent(buildAiPrompt(sanitized))
                //   val deepResult = parseAiResponse(response.text ?: "")
                //
                // OPTION B — OpenAI:
                //   val response = openAiClient.chatCompletion(ChatCompletionRequest(
                //       model = ModelId("gpt-4o"),
                //       messages = listOf(systemMessage, ChatMessage(Role.User, sanitized))
                //   ))
                //   val deepResult = parseAiResponse(response.choices.first().message.content.orEmpty())
                // ─────────────────────────────────────────────────────────────

                delay(3_000L)
                val deepResult = repository.simulateDeepResult(sanitized)

                _uiState.update { current ->
                    (current as? ScamDetectorUiState.Success)?.copy(
                        isDeepScanning = false,
                        deepResult     = deepResult,
                    ) ?: current
                }
            }
        } catch (e: Exception) {
            _uiState.update { current ->
                (current as? ScamDetectorUiState.Success)
                    ?.copy(isDeepScanning = false) ?: current
            }
        }
    }
}
