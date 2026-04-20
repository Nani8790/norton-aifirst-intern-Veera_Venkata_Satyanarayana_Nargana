package com.veera.scammessagedetector.model

/**
 * The output of the Deep AI scan layer.
 *
 * Lives in the model package so it can be shared across the data (repository)
 * and ui layers without introducing a circular dependency.
 *
 * @param tactic        Primary social-engineering tactic identified,
 *                      e.g. "Urgency & Fear Induction".
 * @param riskReasoning Full paragraph explaining the AI's reasoning for its verdict.
 * @param isSimulated   `true` in demo/offline mode; `false` when a live AI model is
 *                      connected. Surfaces as an "AI Verified" or "Simulated" badge
 *                      in the Security Lab UI panel.
 */
data class DeepAnalysisResult(
    val tactic: String,
    val riskReasoning: String,
    val isSimulated: Boolean,
)
