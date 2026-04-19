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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// UI State — sealed hierarchy
// ---------------------------------------------------------------------------

sealed class ScamDetectorUiState {
    data object Idle : ScamDetectorUiState()
    data object Loading : ScamDetectorUiState()
    data class Success(
        val inputText: String,
        val result: AnalysisResult,
    ) : ScamDetectorUiState()
    data class Error(val message: String) : ScamDetectorUiState()
}

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
//
//  Design principles:
//   1. Rules are data, not code. Each Rule is a plain object with a Regex
//      and metadata. Adding a new signal = adding one item to RULES.
//   2. Scoring is category-deduped: a message with 10 synonyms for "URGENT"
//      scores the same as one — each *category* contributes once.
//   3. Display tokens are instance-deduped: the chip list never repeats
//      the same string, even if the Regex matched at multiple positions.
//   4. Confidence is bounded [0, 1] via coerceIn, with a small BASE_SCORE
//      so a clean message displays as ~5% instead of 0%.
// ===========================================================================

// ---------------------------------------------------------------------------
// Severity tier
// ---------------------------------------------------------------------------

/**
 * Calibrated so representative inputs produce sensible verdicts:
 *
 *   1 HIGH alone            → ~0.45  → SUSPICIOUS
 *   2 HIGH                  → ~0.85  → DANGEROUS
 *   1 HIGH + 1 MEDIUM       → ~0.70  → DANGEROUS
 *   1 MEDIUM alone          → ~0.30  → SUSPICIOUS
 *   2 MEDIUM                → ~0.55  → SUSPICIOUS
 *   3 MEDIUM                → ~0.80  → DANGEROUS
 *   1–2 LOW                 → ~0.15–0.25 → SAFE
 *   3 LOW                   → ~0.35  → SUSPICIOUS
 */
private enum class Severity(val weight: Float) {
    HIGH(0.40f),    // near-certain scam signal on its own
    MEDIUM(0.25f),  // strong contextual signal; meaningful in combination
    LOW(0.10f),     // weak signal; only significant when clustered
}

// ---------------------------------------------------------------------------
// Rule definition
// ---------------------------------------------------------------------------

/**
 * A single detection rule.
 *
 * @param category  Human-readable group name used to deduplicate scoring
 *                  (only 1 hit per category toward the confidence score)
 *                  and to build the explanation string.
 * @param severity  Determines the weight added when this category fires.
 * @param pattern   Compiled Regex matched against the full input text.
 * @param tokenize  Converts a MatchResult to the string shown in the UI chip.
 *                  Default: matched substring, lowercased and trimmed.
 */
private data class Rule(
    val category: String,
    val severity: Severity,
    val pattern: Regex,
    val tokenize: (MatchResult) -> String = { it.value.lowercase().trim() },
)

// ---------------------------------------------------------------------------
// Internal match record
// ---------------------------------------------------------------------------

private data class MatchedIndicator(
    val displayToken: String,
    val category: String,
    val severity: Severity,
)

// ---------------------------------------------------------------------------
// Engine constants
// ---------------------------------------------------------------------------

private const val BASE_SCORE           = 0.05f
private const val THRESHOLD_SUSPICIOUS = 0.25f
private const val THRESHOLD_DANGEROUS  = 0.60f

// ---------------------------------------------------------------------------
// Rule catalogue
// ---------------------------------------------------------------------------

/**
 * Complete catalogue of detection rules, ordered HIGH → MEDIUM → LOW.
 * Within each tier, more-specific rules come first so that distinctBy on
 * displayToken keeps the most informative label when patterns overlap.
 */
private val RULES: List<Rule> = listOf(

    // ── HIGH — near-certain scam signals ────────────────────────────────────

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

    // ── MEDIUM — strong contextual signals ──────────────────────────────────

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

    // ── LOW — weak signals, significant only when clustered ─────────────────

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

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

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

    fun reset() {
        _inputText.update { "" }
        _uiState.update { ScamDetectorUiState.Idle }
    }

    // -------------------------------------------------------------------------
    // Analysis pipeline
    // -------------------------------------------------------------------------

    /**
     * Wraps [analyzeText] with the simulated network delay and state transitions.
     *
     * The 2-second delay mirrors real API latency so the Loading state is
     * always exercised during manual testing. Remove it when connecting a
     * real backend.
     */
    private suspend fun performAnalysis(text: String) {
        try {
            delay(2_000L)
            val result = withContext(Dispatchers.Default) {
                analyzeText(text)
            }
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

    /**
     * Core heuristic engine — a pure function with no side-effects.
     * Can be extracted and unit-tested independently of the ViewModel.
     *
     * Pipeline:
     *  1. Run every Rule against the text to collect all MatchedIndicators.
     *  2. Deduplicate display tokens  → [uniqueHits]   (for chip list).
     *  3. Deduplicate by category     → [scoringHits]  (for confidence score).
     *  4. Sum weighted scores → clamp to [0, 1] → [confidence].
     *  5. Map confidence to RiskLevel via fixed thresholds.
     *  6. Build a dynamic explanation from the categorised hits.
     */
    private fun analyzeText(text: String): AnalysisResult {

        // 1. Collect every match across all rules.
        val allHits: List<MatchedIndicator> = RULES.flatMap { rule ->
            rule.pattern.findAll(text).map { match ->
                MatchedIndicator(
                    displayToken = rule.tokenize(match),
                    category     = rule.category,
                    severity     = rule.severity,
                )
            }
        }

        // 2. Deduplicate tokens for the chip list (case-insensitive).
        val uniqueHits: List<MatchedIndicator> =
            allHits.distinctBy { it.displayToken.lowercase() }

        // 3. Deduplicate by category for scoring.
        //    One noisy Regex can't inflate the score by matching the same
        //    category 50 times in a single message.
        val scoringHits: List<MatchedIndicator> =
            allHits.distinctBy { it.category }

        // 4. Confidence score.
        val rawScore = BASE_SCORE +
                scoringHits.sumOf { it.severity.weight.toDouble() }.toFloat()
        val confidence = rawScore.coerceIn(0f, 1f)

        // 5. Risk level thresholds.
        val riskLevel = when {
            confidence >= THRESHOLD_DANGEROUS  -> RiskLevel.DANGEROUS
            confidence >= THRESHOLD_SUSPICIOUS -> RiskLevel.SUSPICIOUS
            else                               -> RiskLevel.SAFE
        }

        // 6. Assemble result.
        val flaggedTokens = uniqueHits.map { it.displayToken }
        val explanation   = buildExplanation(riskLevel, scoringHits)

        return AnalysisResult(
            riskLevel     = riskLevel,
            confidence    = confidence,
            explanation   = explanation,
            flaggedTokens = flaggedTokens,
        )
    }

    /**
     * Builds a human-readable explanation from the scored hits.
     *
     * Structure:
     *   • Opening verdict sentence (varies by risk level).
     *   • HIGH findings — listed individually; each is a critical signal.
     *   • MEDIUM findings — grouped as "supporting indicators".
     *   • LOW findings — grouped as "minor signals".
     *   • Total indicator count.
     *   • Closing advice appropriate to the risk level.
     */
    private fun buildExplanation(
        riskLevel: RiskLevel,
        scoringHits: List<MatchedIndicator>,
    ): String {

        if (scoringHits.isEmpty()) {
            return "No scam indicators were detected. The message contains no " +
                    "urgency language, account threats, suspicious links, or " +
                    "credential requests. Always stay cautious with unsolicited messages."
        }

        val highHits   = scoringHits.filter { it.severity == Severity.HIGH }
        val mediumHits = scoringHits.filter { it.severity == Severity.MEDIUM }
        val lowHits    = scoringHits.filter { it.severity == Severity.LOW }

        return buildString {

            // Opening verdict
            when (riskLevel) {
                RiskLevel.DANGEROUS ->
                    append("This message exhibits multiple strong indicators of a phishing or scam attack. ")
                RiskLevel.SUSPICIOUS ->
                    append("This message contains patterns commonly associated with scam attempts. ")
                RiskLevel.SAFE ->
                    append("This message has minor signals worth noting, though none are conclusive on their own. ")
            }

            // HIGH findings — named individually because each is critical alone
            if (highHits.isNotEmpty()) {
                val plural = if (highHits.size > 1) "s" else ""
                val labels = highHits.joinToString(" and ") { it.category.lowercase() }
                append("Critical signal$plural detected: $labels. ")
            }

            // MEDIUM findings
            if (mediumHits.isNotEmpty()) {
                val plural = if (mediumHits.size > 1) "s" else ""
                val labels = mediumHits.joinToString(", ") { it.category.lowercase() }
                append("Supporting indicator$plural: $labels. ")
            }

            // LOW findings
            if (lowHits.isNotEmpty()) {
                val plural = if (lowHits.size > 1) "s" else ""
                val labels = lowHits.joinToString(", ") { it.category.lowercase() }
                append("Weaker signal$plural also present: $labels. ")
            }

            // Indicator count summary
            val total = scoringHits.size
            val verb  = if (total > 1) "were" else "was"
            append("$total unique indicator${if (total > 1) "s" else ""} $verb matched. ")

            // Closing advice
            when (riskLevel) {
                RiskLevel.DANGEROUS ->
                    append(
                        "Do not click any links or provide personal information. " +
                                "If this appears to be from a known organisation, contact them " +
                                "directly via their official website or phone number."
                    )
                RiskLevel.SUSPICIOUS ->
                    append(
                        "Treat this message with caution. Verify the sender " +
                                "through an official channel before clicking any links " +
                                "or sharing personal details."
                    )
                RiskLevel.SAFE ->
                    append(
                        "Even messages with few signals can be scams if they feel " +
                                "unexpected or request sensitive information."
                    )
            }
        }
    }
}