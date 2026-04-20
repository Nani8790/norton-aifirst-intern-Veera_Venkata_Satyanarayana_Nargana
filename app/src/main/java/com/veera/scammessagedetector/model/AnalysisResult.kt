package com.veera.scammessagedetector.model



/**
 * Represents the severity of a detected scam risk.
 * Using a sealed interface instead of an enum gives us the
 * flexibility to attach richer metadata to each level later.
 */
enum class RiskLevel {
    SAFE,
    SUSPICIOUS,
    DANGEROUS;

    val displayLabel: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * The core result model returned from the analysis engine.
 *
 * @param riskLevel     Categorical risk assessment.
 * @param confidence    Score in [0.0, 1.0] representing overall threat probability.
 * @param explanation   Human-readable narrative of why this verdict was reached.
 * @param flaggedTokens Specific tokens (words, URLs) that triggered detection rules.
 * @param axisScores    Per-axis interpretability scores for the Radar Chart.
 *                      Keys: "Urgency", "Link Malice", "Financial Bait", "Credential Request".
 *                      Values: normalised weight sum for each axis, clamped to [0f, 1f].
 */
data class AnalysisResult(
    val riskLevel: RiskLevel,
    val confidence: Float,
    val explanation: String,
    val flaggedTokens: List<String> = emptyList(),
    val axisScores: Map<String, Float> = emptyMap(),
) {
    /** Convenience: confidence expressed as a 0–100 integer percentage. */
    val confidencePercent: Int get() = (confidence * 100).toInt()

    init {
        require(confidence in 0f..1f) {
            "Confidence must be between 0.0 and 1.0, got $confidence"
        }
    }
}