package com.veera.scammessagedetector.data.repository

import com.veera.scammessagedetector.data.local.ScanHistoryDao
import com.veera.scammessagedetector.data.local.ScanHistoryEntity
import com.veera.scammessagedetector.model.AnalysisResult
import com.veera.scammessagedetector.model.DeepAnalysisResult
import com.veera.scammessagedetector.model.RiskLevel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// ===========================================================================
//  HEURISTIC RULE ENGINE — private to this file
// ===========================================================================

/**
 * Weight applied to the confidence score for each matched rule category.
 * Only one hit per category is counted (deduped by [Rule.category]) so that
 * a message with ten "urgency" phrases isn't scored ten times worse than one
 * with a single "shortened URL".
 */
private enum class Severity(val weight: Float) {
    /** Critical signal — strong predictor of phishing (e.g. raw IP in URL). */
    HIGH(0.40f),
    /** Supporting signal — meaningful alone, decisive in combination. */
    MEDIUM(0.25f),
    /** Weak signal — presence alone is insufficient to flag a message. */
    LOW(0.10f),
}

/**
 * A single detection rule mapping a [Regex] pattern to a [Severity] weight.
 *
 * @param category  Human-readable name, also used as the deduplication key.
 * @param severity  Score contribution if this rule fires.
 * @param pattern   Compiled regex applied to the full message text.
 * @param tokenize  Transforms a [MatchResult] into a display-friendly token string
 *                  shown in the "Flagged signals" chip list. Defaults to the raw
 *                  matched value in lowercase.
 */
private data class Rule(
    val category: String,
    val severity: Severity,
    val pattern: Regex,
    val tokenize: (MatchResult) -> String = { it.value.lowercase().trim() },
)

/** A single regex hit ready for display and scoring. */
private data class MatchedIndicator(
    val displayToken: String,
    val category: String,
    val severity: Severity,
)

private const val BASE_SCORE           = 0.05f
private const val THRESHOLD_SUSPICIOUS = 0.25f
private const val THRESHOLD_DANGEROUS  = 0.60f

/**
 * Ordered list of detection rules applied by the heuristic engine.
 *
 * **Time Complexity — O(N · M):**
 *  - N = input token/character count (message length).
 *  - M = rule count (currently 9).
 *  - Each [Regex] is compiled **once** at class-load time into a finite-state automaton
 *    (NFA). Kotlin's `Regex` delegates to `java.util.regex.Pattern`, which is immutable
 *    and thread-safe, so no re-compilation occurs per call.
 *  - A single NFA scan of N characters runs in O(N) for the bounded alternation groups
 *    used here (no unbounded backtracking). Applying M such patterns is O(N · M).
 *  - Pathological O(2^N) backtracking is avoided by keeping alternation groups short
 *    and avoiding nested quantifiers within the same group.
 */
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

// ---------------------------------------------------------------------------
//  Radar-chart axis definitions
// ---------------------------------------------------------------------------

/**
 * Maps each radar-chart axis to the rule [categories][Rule.category] that contribute to it.
 * These four axes surface the primary attack vectors as independently interpretable signals.
 */
private val AXIS_CATEGORY_MAP: Map<String, Set<String>> = mapOf(
    "Urgency"            to setOf("Urgency Language", "Account Threat"),
    "Link Malice"        to setOf("Shortened URL", "Raw IP Address in URL", "Suspicious Domain", "Suspicious TLD"),
    "Financial Bait"     to setOf("Financial Bait"),
    "Credential Request" to setOf("Credential Phishing", "Impersonation Language", "Phishing Call-to-Action"),
)

/**
 * Maximum possible weight sum per axis — used to normalise axis scores to [0f, 1f].
 *
 * Calculated as the sum of [Severity.weight] for every rule in the corresponding
 * [AXIS_CATEGORY_MAP] entry. Values exceeding 1.0 are clamped by [coerceIn].
 */
private val AXIS_MAX_WEIGHT: Map<String, Float> = mapOf(
    "Urgency"            to 0.50f, // MEDIUM (0.25) + MEDIUM (0.25)
    "Link Malice"        to 1.45f, // 3 × HIGH (0.40) + MEDIUM (0.25) — capped at 1.0 in practice
    "Financial Bait"     to 0.10f, // LOW (0.10)
    "Credential Request" to 0.45f, // MEDIUM (0.25) + LOW (0.10) + LOW (0.10)
)

// ===========================================================================
//  REPOSITORY IMPLEMENTATION
// ===========================================================================

/**
 * Production implementation of [ScamRepository].
 *
 * Hilt injects this class wherever [ScamRepository] is requested, as configured
 * in [com.veera.scammessagedetector.di.AppModule].
 *
 * @param dao Room DAO for persisting and observing scan history.
 */
@Singleton
class ScamRepositoryImpl @Inject constructor(
    private val dao: ScanHistoryDao,
) : ScamRepository {

    /**
     * Runs all [RULES] against [text] and aggregates a confidence-weighted verdict.
     *
     * Pipeline:
     *  1. Find all regex matches across all rules → [MatchedIndicator] list.
     *  2. Deduplicate by display token (prevents the same URL appearing twice).
     *  3. Deduplicate by category for scoring (one weight per category regardless
     *     of match count — prevents score inflation from repetitive phrases).
     *  4. Sum category weights + [BASE_SCORE] and clamp to [0, 1].
     *  5. Map to [RiskLevel] via threshold comparison.
     */
    override suspend fun analyze(text: String): AnalysisResult {
        val allHits: List<MatchedIndicator> = RULES.flatMap { rule ->
            rule.pattern.findAll(text).map { match ->
                MatchedIndicator(
                    displayToken = rule.tokenize(match),
                    category     = rule.category,
                    severity     = rule.severity,
                )
            }
        }
        val uniqueHits  = allHits.distinctBy { it.displayToken.lowercase() }
        val scoringHits = allHits.distinctBy { it.category }
        val rawScore    = BASE_SCORE + scoringHits.sumOf { it.severity.weight.toDouble() }.toFloat()
        val confidence  = rawScore.coerceIn(0f, 1f)
        val riskLevel   = when {
            confidence >= THRESHOLD_DANGEROUS  -> RiskLevel.DANGEROUS
            confidence >= THRESHOLD_SUSPICIOUS -> RiskLevel.SUSPICIOUS
            else                               -> RiskLevel.SAFE
        }
        val axisScores: Map<String, Float> = AXIS_CATEGORY_MAP.mapValues { (axisName, categories) ->
            val axisWeight = scoringHits
                .filter { it.category in categories }
                .sumOf { it.severity.weight.toDouble() }
                .toFloat()
            (axisWeight / (AXIS_MAX_WEIGHT[axisName] ?: 1f)).coerceIn(0f, 1f)
        }

        return AnalysisResult(
            riskLevel     = riskLevel,
            confidence    = confidence,
            explanation   = buildExplanation(riskLevel, scoringHits),
            flaggedTokens = uniqueHits.map { it.displayToken },
            axisScores    = axisScores,
        )
    }

    /**
     * Constructs a human-readable explanation from the scoring hits.
     *
     * Uses a declarative sentence template per [RiskLevel], then appends category
     * breakdowns by severity tier. The closing sentence delivers context-appropriate
     * safety advice.
     *
     * @param riskLevel   The final verdict level.
     * @param scoringHits Deduplicated-by-category hit list used for scoring.
     */
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
                val s = if (highHits.size > 1) "s" else ""
                append("Critical signal$s detected: ${highHits.joinToString(" and ") { it.category.lowercase() }}. ")
            }
            if (mediumHits.isNotEmpty()) {
                val s = if (mediumHits.size > 1) "s" else ""
                append("Supporting indicator$s: ${mediumHits.joinToString(", ") { it.category.lowercase() }}. ")
            }
            if (lowHits.isNotEmpty()) {
                val s = if (lowHits.size > 1) "s" else ""
                append("Weaker signal$s also present: ${lowHits.joinToString(", ") { it.category.lowercase() }}. ")
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

    /**
     * Strips characters frequently used in Indirect Prompt Injection attacks
     * before passing user-controlled text to an AI model.
     *
     * Defended vectors:
     *  - `[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]` — null bytes and
     *    non-printable ASCII controls (tab, newline, carriage-return are preserved).
     *  - `[\u200B-\u200F\u2028\u2029\uFEFF]` — zero-width spaces, directional marks,
     *    line/paragraph separators, and BOM (prime vectors for hiding instructions).
     *  - `\u202E` — RTL override, used to disguise URLs as benign strings.
     *  - Three or more consecutive newlines collapsed to two (prevents
     *    context-window padding attacks that push system instructions off the top).
     */
    override fun sanitize(text: String): String = text
        .replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"), "")
        .replace(Regex("[\u200B-\u200F\u2028\u2029\uFEFF]"), "")
        .replace("\u202E", "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /**
     * Produces a contextually appropriate simulated [DeepAnalysisResult] by
     * inspecting the dominant social-engineering signal in [sanitizedText].
     *
     * The `when` branches are ordered by specificity: the most diagnostic signals
     * (urgency/fear, credential harvesting) are checked first, and the catch-all
     * "Multi-Vector" branch fires when no single tactic dominates.
     *
     * Replace the body of this function (and the caller in the ViewModel) with a
     * real AI SDK call once the integration socket is wired up.
     *
     * @param sanitizedText Output of [sanitize] — safe for inclusion in an AI prompt.
     */
    override fun simulateDeepResult(sanitizedText: String): DeepAnalysisResult {
        val lower = sanitizedText.lowercase()
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

    override fun getHistory(): Flow<List<ScanHistoryEntity>> = dao.observeAll()

    /**
     * Converts [result] to a [ScanHistoryEntity] and persists it.
     *
     * [flaggedTokens] are pipe-delimited (`|`) since the token strings may contain
     * commas and parentheses (e.g. "Suspicious domain (chase-secure-login.net)")
     * but never pipe characters.
     */
    override suspend fun saveToHistory(inputText: String, result: AnalysisResult) {
        dao.insert(
            ScanHistoryEntity(
                inputText     = inputText,
                riskLevel     = result.riskLevel.name,
                confidence    = result.confidence,
                explanation   = result.explanation,
                flaggedTokens = result.flaggedTokens.joinToString("|"),
            )
        )
    }

    override suspend fun deleteHistoryEntry(id: Long) = dao.deleteById(id)

    override suspend fun clearHistory() = dao.deleteAll()

    /** Convenience: checks if any of [keywords] appears in this string (case-insensitive). */
    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it, ignoreCase = true) }
}
