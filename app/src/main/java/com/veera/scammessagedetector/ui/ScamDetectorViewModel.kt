package com.veera.scammessagedetector.ui


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veera.scammessagedetector.model.AnalysisResult
import com.veera.scammessagedetector.model.RiskLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// UI State — sealed hierarchy
// ---------------------------------------------------------------------------

/**
 * Exhaustive model of every state the screen can be in.
 *
 * Prefer a sealed class over a single data class with nullable fields.
 * This forces the UI layer to handle every branch explicitly and eliminates
 * impossible state combinations (e.g., loading = true AND result != null).
 */
sealed class ScamDetectorUiState {

    /** Nothing has happened yet; the input field is empty or untouched. */
    data object Idle : ScamDetectorUiState()

    /** Analysis is in-flight; show a progress indicator, disable the button. */
    data object Loading : ScamDetectorUiState()

    /**
     * Analysis completed successfully.
     *
     * @param inputText  Echo back what was analyzed so the UI can display it.
     * @param result     The analysis verdict from the engine.
     */
    data class Success(
        val inputText: String,
        val result: AnalysisResult,
    ) : ScamDetectorUiState()

    /**
     * Something went wrong (network failure, API error, empty input, etc.).
     *
     * @param message  User-facing error message — never expose raw stack traces here.
     */
    data class Error(val message: String) : ScamDetectorUiState()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class ScamDetectorViewModel : ViewModel() {

    // Backing property is private — only the VM can mutate state.
    private val _uiState = MutableStateFlow<ScamDetectorUiState>(ScamDetectorUiState.Idle)

    /** Exposed as an immutable StateFlow; the Compose UI collects this. */
    val uiState: StateFlow<ScamDetectorUiState> = _uiState.asStateFlow()

    // Keep the input text in a separate StateFlow so the text field
    // can survive config changes independently of the analysis result.
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    /**
     * Called by the UI on every keystroke in the text field.
     * Also resets any prior result/error so stale output doesn't linger
     * while the user is editing a new message.
     */
    fun onInputChanged(newText: String) {
        _inputText.update { newText }
        // Clear result pane as soon as the user starts editing again.
        if (_uiState.value is ScamDetectorUiState.Success ||
            _uiState.value is ScamDetectorUiState.Error
        ) {
            _uiState.update { ScamDetectorUiState.Idle }
        }
    }

    /**
     * Entry point for the "Analyze" button.
     *
     * Current implementation: simulates a 2-second network call and returns
     * a hardcoded Suspicious result.
     *
     * Step 2 TODO: Replace the body of [performAnalysis] with a real
     * repository call (e.g., `repository.analyze(text)`) and remove the
     * hardcoded result. The state machine and error handling remain unchanged.
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

    /** Reset the screen back to its initial state. */
    fun reset() {
        _inputText.update { "" }
        _uiState.update { ScamDetectorUiState.Idle }
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Stub analysis — will be replaced with a real API/ML call in Step 2.
     *
     * Wraps the result in a try/catch so any future repository can throw and
     * the state machine will transition cleanly to [ScamDetectorUiState.Error].
     */
    private suspend fun performAnalysis(text: String) {
        try {
            // --- Simulated network latency ---
            delay(2_000L)

            // --- Hardcoded stub result ---
            val stubbedResult = AnalysisResult(
                riskLevel = RiskLevel.SUSPICIOUS,
                confidence = 0.78f,
                explanation = "This message contains several hallmarks of phishing attempts: " +
                        "an urgent call-to-action, a mismatched sender domain, and a " +
                        "shortened URL that obscures the true destination.",
                flaggedTokens = listOf("urgent", "click here", "bit.ly/3xFk91")
            )

            _uiState.update {
                ScamDetectorUiState.Success(inputText = text, result = stubbedResult)
            }
        } catch (e: Exception) {
            _uiState.update {
                ScamDetectorUiState.Error(
                    message = "Analysis failed: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }
}