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
 * @param confidence    Score between 0.0 and 1.0 (e.g., 0.87 = 87%).
 * @param explanation   Short, human-readable summary of why this verdict was reached.
 * @param flaggedTokens Optional list of specific words/URLs that triggered the verdict,
 *                      reserved for the real AI integration in Step 2.
 */
data class AnalysisResult(
    val riskLevel: RiskLevel,
    val confidence: Float,
    val explanation: String,
    val flaggedTokens: List<String> = emptyList()
) {
    /** Convenience: confidence expressed as a 0–100 integer percentage. */
    val confidencePercent: Int get() = (confidence * 100).toInt()

    init {
        require(confidence in 0f..1f) {
            "Confidence must be between 0.0 and 1.0, got $confidence"
        }
    }
}