package com.veera.scammessagedetector.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veera.scammessagedetector.model.AnalysisResult
import com.veera.scammessagedetector.model.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// UI State — sealed hierarchy
// ---------------------------------------------------------------------------

sealed class ScamDetectorUiState {
    data object Idle : ScamDetectorUiState()
    data object Loading : ScamDetectorUiState()

    /**
     * @param inputText      The original text that was analysed.
     * @param result         Heuristic engine verdict (always present after scan).
     * @param isDeepScanning True while the Deep AI scan coroutine is in-flight.
     *                       The heuristic result remains fully visible during this time.
     * @param deepResult     Populated once the Deep AI scan completes; null before that.
     */
    data class Success(
        val inputText: String,
        val result: AnalysisResult,
        val isDeepScanning: Boolean = false,
        val deepResult: DeepAnalysisResult? = null,
    ) : ScamDetectorUiState()

    data class Error(val message: String) : ScamDetectorUiState()
}

// ---------------------------------------------------------------------------
// Deep analysis result model
// ---------------------------------------------------------------------------

/**
 * The output of the Deep AI scan layer.
 *
 * @param tactic        Primary social-engineering tactic identified
 *                      (e.g., "Urgency & Fear Induction").
 * @param riskReasoning Full paragraph explaining the AI's reasoning.
 * @param isSimulated   True in demo mode; false when a live AI model is connected.
 *                      Surface this to the user as an "AI Verified" or "Simulated" badge.
 */
data class DeepAnalysisResult(
    val tactic: String,
    val riskReasoning: String,
    val isSimulated: Boolean,
)

// ---------------------------------------------------------------------------
// Example messages
// ---------------------------------------------------------------------------

data class ExampleMessage(
    val label: String,
    val senderHint: String,
    val body: String,
)

// ===========================================================================
//  LOCAL HEURISTIC ANALYSIS ENGINE
// ===========================================================================

private enum class Severity(val weight: Float) {
    HIGH(0.40f),
    MEDIUM(0.25f),
    LOW(0.10f),
}

private data class Rule(
    val category: String,
    val severity: Severity,
    val pattern: Regex,
    val tokenize: (MatchResult) -> String = { it.value.lowercase().trim() },
)

private data class MatchedIndicator(
    val displayToken: String,
    val category: String,
    val severity: Severity,
)

private const val BASE_SCORE           = 0.05f
private const val THRESHOLD_SUSPICIOUS = 0.25f
private const val THRESHOLD_DANGEROUS  = 0.60f

private val RULES: List<Rule> = listOf(

    // ── HIGH ─────────────────────────────────────────────────────────────────

    Rule(
        category = "Shortened URL",
        severity = Severity.HIGH,
        pattern = Regex(
            pattern = """https?://(bit\.ly|tinyurl\.com|t\.co|goo\.gl|ow\.ly|is\.gd""" +
                    """|buff\.ly|shorturl\.at|cutt\.ly|rb\.gy|tiny\.cc|lnkd\.in""" +
                    """|dlvr\.it|su\.pr|tr\.im|v\.gd|b\.link|short\.io)/\S*""",
            option = RegexOption.IGNORE_CASE,
        ),
        tokenize = { match ->
            val host = match.value.substringAfter("://").substringBefore("/")
            "Shortened URL ($host)"
        },
    ),

    Rule(
        category = "Raw IP Address in URL",
        severity = Severity.HIGH,
        pattern = Regex(
            pattern = """https?://(\d{1,3}\.){3}\d{1,3}(/\S*)?""",
            option = RegexOption.IGNORE_CASE,
        ),
        tokenize = { match ->
            val ip = match.value.substringAfter("://").substringBefore("/")
            "IP-based URL ($ip)"
        },
    ),

    Rule(
        category = "Suspicious Domain",
        severity = Severity.HIGH,
        pattern = Regex(
            pattern = """https?://[^\s/]*[-_](secure|verify|login|update|alert|account""" +
                    """|banking|support|confirm|validation|access|signin|recover)[^\s/]*\.[a-z]{2,6}(/\S*)?""",
            option = RegexOption.IGNORE_CASE,
        ),
        tokenize = { match ->
            val domain = match.value.substringAfter("://").substringBefore("/")
            "Suspicious domain ($domain)"
        },
    ),

    // ── MEDIUM ───────────────────────────────────────────────────────────────

    Rule(
        category = "Urgency Language",
        severity = Severity.MEDIUM,
        pattern = Regex(
            pattern = """\b(urgent|immediately|act now|right away|right now|asap""" +
                    """|within \d+ (hr|hour|min|minute)s?|expires? (soon|today|in \d+)""" +
                    """|limited time|final (warning|notice)|last chance|deadline (today|tonight))\b""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ),
    ),

    Rule(
        category = "Account Threat",
        severity = Severity.MEDIUM,
        pattern = Regex(
            pattern = """\b(suspend(ed|ing|ion)?|block(ed|ing)?|restrict(ed|ing|ion)?""" +
                    """|terminat(ed|ing|ion)?|disabl(ed|ing)?|clos(ed|ing)?""" +
                    """|lock(ed|ing|out)?|unauthori[sz]ed (access|activity|login))\b""",
            option = RegexOption.IGNORE_CASE,
        ),
    ),

    Rule(
        category = "Credential Phishing",
        severity = Severity.MEDIUM,
        pattern = Regex(
            pattern = """\b(password|credentials?""" +
                    """|verify (your )?(identity|account|details?|information)""" +
                    """|confirm (your )?(identity|account|details?|information)""" +
                    """|validate (your )?(account|identity|details?)""" +
                    """|provide (your )?(details?|information|credentials?|bank|card))\b""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ),
    ),

    Rule(
        category = "Suspicious TLD",
        severity = Severity.MEDIUM,
        pattern = Regex(
            pattern = """https?://[^\s]+\.(xyz|top|click|loan|pw|cc|tk|ml|ga|cf|gq""" +
                    """|work|online|site|website|tech|club|info|biz|live|shop|stream)(/\S*)?""",
            option = RegexOption.IGNORE_CASE,
        ),
        tokenize = { match ->
            val tld = match.value.substringBeforeLast("/").substringAfterLast(".").take(8)
            "Suspicious TLD (.$tld)"
        },
    ),

    // ── LOW ──────────────────────────────────────────────────────────────────

    Rule(
        category = "Phishing Call-to-Action",
        severity = Severity.LOW,
        pattern = Regex(
            pattern = """\b(click here|click (the |this |on the |on this )?link""" +
                    """|tap here|tap (the |this )?link|follow (this |the )?link""" +
                    """|open (this |the )?link|download now|install now|get it now)\b""",
            option = RegexOption.IGNORE_CASE,
        ),
    ),

    Rule(
        category = "Financial Bait",
        severity = Severity.LOW,
        pattern = Regex(
            pattern = """\b(you('ve| have) won|congratulations you|prize|lottery""" +
                    """|reward|claim (your|the)|free gift|cash prize""" +
                    """|transfer funds?|wire transfer|money transfer|inheritance)\b""",
            option = RegexOption.IGNORE_CASE,
        ),
    ),

    Rule(
        category = "Impersonation Language",
        severity = Severity.LOW,
        pattern = Regex(
            pattern = """\b(dear (customer|user|account.?holder|member|client|valued)""" +
                    """|official (notice|alert|message|communication)""" +
                    """|security (team|alert|department|notification)""" +
                    """|customer (care|support|service)( (team|department))?""" +
                    """|helpdesk|help desk)\b""",
            option = RegexOption.IGNORE_CASE,
        ),
    ),
)

// ===========================================================================
//  VIEWMODEL
// ===========================================================================

class ScamDetectorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ScamDetectorUiState>(ScamDetectorUiState.Idle)
    val uiState: StateFlow<ScamDetectorUiState> = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

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
    // Public API — called by the UI
    // -------------------------------------------------------------------------

    fun onInputChanged(newText: String) {
        _inputText.update { newText }
        if (_uiState.value is ScamDetectorUiState.Success ||
            _uiState.value is ScamDetectorUiState.Error
        ) {
            _uiState.update { ScamDetectorUiState.Idle }
        }
    }

    fun onExampleSelected(example: ExampleMessage) {
        onInputChanged(example.body)
    }

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
     * Called by the 'Deep AI Scan' button in the UI. This function mutates the
     * *existing* Success state in-place by setting [ScamDetectorUiState.Success.isDeepScanning]
     * to true, so the heuristic result card stays fully visible while the AI
     * layer runs beneath it. The top-level sealed state never changes — we stay
     * in Success throughout.
     */
    fun triggerDeepScan() {
        val current = _uiState.value as? ScamDetectorUiState.Success ?: return

        // Guard: don't stack scan requests.
        if (current.isDeepScanning || current.deepResult != null) return

        viewModelScope.launch {
            _uiState.update { state ->
                (state as? ScamDetectorUiState.Success)
                    ?.copy(isDeepScanning = true) ?: state
            }
            performDeepScan(current.inputText)
        }
    }

    fun reset() {
        _inputText.update { "" }
        _uiState.update { ScamDetectorUiState.Idle }
    }

    // -------------------------------------------------------------------------
    // Heuristic analysis pipeline
    // -------------------------------------------------------------------------

    private suspend fun performAnalysis(text: String) {
        try {
            delay(2_000L)
            // CPU-bound regex work runs on the Default dispatcher, keeping Main free.
            val result = withContext(Dispatchers.Default) { analyzeText(text) }
            _uiState.update {
                ScamDetectorUiState.Success(inputText = text, result = result)
            }
        } catch (e: Exception) {
            _uiState.update {
                ScamDetectorUiState.Error(
                    message = "Analysis failed: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    private fun analyzeText(text: String): AnalysisResult {
        val allHits: List<MatchedIndicator> = RULES.flatMap { rule ->
            rule.pattern.findAll(text).map { match ->
                MatchedIndicator(
                    displayToken = rule.tokenize(match),
                    category     = rule.category,
                    severity     = rule.severity,
                )
            }
        }
        val uniqueHits   = allHits.distinctBy { it.displayToken.lowercase() }
        val scoringHits  = allHits.distinctBy { it.category }
        val rawScore     = BASE_SCORE + scoringHits.sumOf { it.severity.weight.toDouble() }.toFloat()
        val confidence   = rawScore.coerceIn(0f, 1f)
        val riskLevel    = when {
            confidence >= THRESHOLD_DANGEROUS  -> RiskLevel.DANGEROUS
            confidence >= THRESHOLD_SUSPICIOUS -> RiskLevel.SUSPICIOUS
            else                               -> RiskLevel.SAFE
        }
        return AnalysisResult(
            riskLevel     = riskLevel,
            confidence    = confidence,
            explanation   = buildExplanation(riskLevel, scoringHits),
            flaggedTokens = uniqueHits.map { it.displayToken },
        )
    }

    private fun buildExplanation(riskLevel: RiskLevel, scoringHits: List<MatchedIndicator>): String {
        if (scoringHits.isEmpty()) {
            return "No scam indicators were detected. The message contains no urgency " +
                    "language, account threats, suspicious links, or credential requests. " +
                    "Always stay cautious with unsolicited messages."
        }
        val highHits   = scoringHits.filter { it.severity == Severity.HIGH }
        val mediumHits = scoringHits.filter { it.severity == Severity.MEDIUM }
        val lowHits    = scoringHits.filter { it.severity == Severity.LOW }
        return buildString {
            when (riskLevel) {
                RiskLevel.DANGEROUS  -> append("This message exhibits multiple strong indicators of a phishing or scam attack. ")
                RiskLevel.SUSPICIOUS -> append("This message contains patterns commonly associated with scam attempts. ")
                RiskLevel.SAFE       -> append("This message has minor signals worth noting, though none are conclusive on their own. ")
            }
            if (highHits.isNotEmpty()) {
                val plural = if (highHits.size > 1) "s" else ""
                append("Critical signal$plural detected: ${highHits.joinToString(" and ") { it.category.lowercase() }}. ")
            }
            if (mediumHits.isNotEmpty()) {
                val plural = if (mediumHits.size > 1) "s" else ""
                append("Supporting indicator$plural: ${mediumHits.joinToString(", ") { it.category.lowercase() }}. ")
            }
            if (lowHits.isNotEmpty()) {
                val plural = if (lowHits.size > 1) "s" else ""
                append("Weaker signal$plural also present: ${lowHits.joinToString(", ") { it.category.lowercase() }}. ")
            }
            val total = scoringHits.size
            append("$total unique indicator${if (total > 1) "s were" else " was"} matched. ")
            when (riskLevel) {
                RiskLevel.DANGEROUS  -> append("Do not click any links or provide personal information. If this appears to be from a known organisation, contact them directly via their official website or phone number.")
                RiskLevel.SUSPICIOUS -> append("Treat this message with caution. Verify the sender through an official channel before clicking any links or sharing personal details.")
                RiskLevel.SAFE       -> append("Even messages with few signals can be scams if they feel unexpected or request sensitive information.")
            }
        }
    }

    // =========================================================================
    //  DEEP AI SCAN — HYBRID ANALYSIS LAYER
    // =========================================================================

    /**
     * Strips characters frequently used in Indirect Prompt Injection attacks
     * before passing user-controlled text to an AI model.
     *
     * Defends against:
     *  • Null bytes and non-printable control characters
     *  • Zero-width characters (used to hide injected instructions)
     *  • RTL override (used to visually disguise URLs)
     *  • Context-window stuffing via excessive blank lines
     */
    private fun sanitizeInput(text: String): String = text
        // Remove null bytes and non-printable ASCII controls (keep \t \n \r)
        .replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"), "")
        // Remove zero-width space, non-joiner, joiner, and BOM — prime injection vectors
        .replace(Regex("[\u200B-\u200F\u2028\u2029\uFEFF]"), "")
        // Remove RTL override character used to disguise malicious URLs
        .replace("\u202E", "")
        // Collapse 3+ consecutive blank lines → 2 (prevents context-window padding)
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /**
     * Deep AI scan pipeline.
     *
     * Architecture: this function is designed as a "plug-and-play socket".
     * The simulated block is clearly separated from the real integration point
     * so connecting a live model requires only replacing the marked section.
     *
     * Threading: the entire function runs on [Dispatchers.IO] because a real
     * AI SDK call is a network operation. The state update is posted back
     * to the StateFlow from whatever thread calls [update] — that is safe
     * because [MutableStateFlow.update] is thread-safe.
     */
    private suspend fun performDeepScan(text: String) {
        try {
            val sanitizedText = sanitizeInput(text)

            withContext(Dispatchers.IO) {

                // ─────────────────────────────────────────────────────────────
                // REAL AI INTEGRATION SOCKET
                //
                // OPTION A — Google Gemini (google-ai-generativelanguage SDK):
                //
                //   val generativeModel = GenerativeModel(
                //       modelName = "gemini-1.5-pro",
                //       apiKey    = BuildConfig.GEMINI_API_KEY,
                //   )
                //   val prompt   = buildAiPrompt(sanitizedText)
                //   val response = generativeModel.generateContent(prompt)
                //   val deepResult = parseAiResponse(response.text ?: "")
                //
                // OPTION B — OpenAI (openai-kotlin SDK):
                //
                //   val response = openAiClient.chatCompletion(
                //       request = ChatCompletionRequest(
                //           model    = ModelId("gpt-4o"),
                //           messages = listOf(
                //               ChatMessage(role = Role.System, content = SYSTEM_PROMPT),
                //               ChatMessage(role = Role.User,   content = sanitizedText),
                //           ),
                //       )
                //   )
                //   val deepResult = parseAiResponse(
                //       response.choices.first().message.content.orEmpty()
                //   )
                //
                // TODO: Uncomment one of the blocks above, delete the simulated
                //       delay + buildSimulatedDeepResult call below, and implement
                //       parseAiResponse() to deserialise the model's JSON output
                //       into a DeepAnalysisResult.
                // ─────────────────────────────────────────────────────────────

                // ── SIMULATED RESPONSE (remove when real AI is connected) ────
                delay(3_000L) // mirrors real LLM cold-start latency
                val deepResult = buildSimulatedDeepResult(sanitizedText)
                // ── END SIMULATED BLOCK ──────────────────────────────────────

                // Post result back — update() is thread-safe on MutableStateFlow
                _uiState.update { current ->
                    (current as? ScamDetectorUiState.Success)?.copy(
                        isDeepScanning = false,
                        deepResult     = deepResult,
                    ) ?: current
                }
            }
        } catch (e: Exception) {
            // On failure: clear the scanning flag but keep the heuristic result.
            // A real app should also emit a one-shot error event (SharedFlow/Channel).
            _uiState.update { current ->
                (current as? ScamDetectorUiState.Success)
                    ?.copy(isDeepScanning = false) ?: current
            }
        }
    }

    /**
     * Produces a contextually appropriate simulated [DeepAnalysisResult] by
     * inspecting the sanitized text for dominant social-engineering signals.
     *
     * Replace this function with a real AI response parser once the integration
     * socket above is wired up.
     */
    private fun buildSimulatedDeepResult(text: String): DeepAnalysisResult {
        val lower = text.lowercase()
        return when {
            lower.containsAny("urgent", "immediately", "suspended", "locked") ->
                DeepAnalysisResult(
                    tactic = "Urgency & Fear Induction",
                    riskReasoning = "The message employs high-pressure social engineering by " +
                            "creating an artificial time constraint and threatening adverse " +
                            "consequences (account suspension or loss of access). This tactic " +
                            "bypasses the target's rational decision-making by triggering a stress " +
                            "response, pushing them to act impulsively before verifying the sender's " +
                            "legitimacy. It is one of the most reliable vectors in credential-phishing " +
                            "campaigns targeting financial accounts.",
                    isSimulated = true,
                )
            lower.containsAny("verify", "confirm", "validate", "identity", "credentials") ->
                DeepAnalysisResult(
                    tactic = "Authority Impersonation & Credential Harvesting",
                    riskReasoning = "The message impersonates a trusted institution and requests " +
                            "identity verification through an attacker-controlled channel. The phrasing " +
                            "deliberately mirrors legitimate security communication to establish false " +
                            "authority — a hallmark of spear-phishing campaigns. The goal is to route " +
                            "the target to a cloned login page where credentials are silently exfiltrated.",
                    isSimulated = true,
                )
            lower.containsAny("won", "prize", "lottery", "reward", "claim", "inheritance") ->
                DeepAnalysisResult(
                    tactic = "Advance-Fee & Reward Fraud",
                    riskReasoning = "The message baits the target with a fabricated financial reward " +
                            "contingent on taking an action. This is consistent with advance-fee fraud " +
                            "(419/Nigerian prince scam) or lottery-fraud templates. The attacker's " +
                            "objective is to extract personal information or a small upfront 'processing " +
                            "fee' in exchange for a promised but entirely fictitious reward.",
                    isSimulated = true,
                )
            lower.containsAny("package", "delivery", "shipment", "parcel", "usps", "fedex", "dhl") ->
                DeepAnalysisResult(
                    tactic = "Delivery Impersonation Phishing",
                    riskReasoning = "This message mimics a legitimate parcel carrier notification to " +
                            "exploit the high baseline expectation of online deliveries. The false " +
                            "'delivery failed' scenario creates urgency and plausibility in equal measure, " +
                            "directing the target to a phishing page engineered to capture address details, " +
                            "payment card information, or account credentials under the guise of resolving " +
                            "a non-existent shipping issue.",
                    isSimulated = true,
                )
            else ->
                DeepAnalysisResult(
                    tactic = "Multi-Vector Social Engineering",
                    riskReasoning = "The message combines several social-engineering vectors — false " +
                            "authority, urgency signals, and a deceptive call-to-action — without relying " +
                            "heavily on any single one. This breadth suggests a targeted, iteratively " +
                            "tested phishing template rather than opportunistic spam. The attacker's " +
                            "primary objective appears to be credential theft, with malware delivery as " +
                            "a secondary vector via the linked destination.",
                    isSimulated = true,
                )
        }
    }

    // Convenience extension to keep the `when` branches above readable.
    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it, ignoreCase = true) }
}