package com.veera.scammessagedetector.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.veera.scammessagedetector.data.local.ScanHistoryEntity
import com.veera.scammessagedetector.model.AnalysisResult
import com.veera.scammessagedetector.model.DeepAnalysisResult
import com.veera.scammessagedetector.model.RiskLevel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Design tokens — semantic risk-level colors
// ---------------------------------------------------------------------------

private val ColorSafe                = Color(0xFF1B5E20)
private val ColorSafeContainer       = Color(0xFFE8F5E9)
private val ColorSuspicious          = Color(0xFFE65100)
private val ColorSuspiciousContainer = Color(0xFFFFF3E0)
private val ColorDangerous           = Color(0xFFB71C1C)
private val ColorDangerousContainer  = Color(0xFFFFEBEE)

// Security Lab dark-panel tokens
private val ColorLabBackground = Color(0xFF0D1117)
private val ColorLabSurface    = Color(0xFF161B22)
private val ColorLabAccent     = Color(0xFF00B4D8)
private val ColorLabBorder     = Color(0xFF30363D)
private val ColorLabText       = Color(0xFFCDD9E5)
private val ColorLabTextMuted  = Color(0xFF8B949E)

// Shimmer gradient tiles
private val ColorShimmer1 = Color(0xFF1C2233)
private val ColorShimmer2 = Color(0xFF2A3347)
private val ColorShimmer3 = Color(0xFF1C2233)

/**
 * Maps a [RiskLevel] to its primary foreground color used for text, icons,
 * gauge arcs, and chip borders.
 */
private fun RiskLevel.primaryColor() = when (this) {
    RiskLevel.SAFE       -> ColorSafe
    RiskLevel.SUSPICIOUS -> ColorSuspicious
    RiskLevel.DANGEROUS  -> ColorDangerous
}

/**
 * Maps a [RiskLevel] to its light container background color used for
 * the risk badge row.
 */
private fun RiskLevel.containerColor() = when (this) {
    RiskLevel.SAFE       -> ColorSafeContainer
    RiskLevel.SUSPICIOUS -> ColorSuspiciousContainer
    RiskLevel.DANGEROUS  -> ColorDangerousContainer
}

// ---------------------------------------------------------------------------
// Root screen — wires ViewModel to composable tree
// ---------------------------------------------------------------------------

/**
 * Root entry-point composable for the scam detector feature.
 *
 * Collects all [StateFlow]s from [ScamDetectorViewModel] and passes them down
 * as plain values to [ScamDetectorContent], keeping the content composable
 * fully stateless and trivially previewable.
 *
 * Haptic feedback fires once each time the state transitions to
 * [ScamDetectorUiState.Success] — the [LaunchedEffect] key resets on every
 * state change, but the [HapticFeedbackType.LongPress] call is gated inside
 * the `is Success` branch so it only fires on that specific transition.
 *
 * @param viewModel Hilt-provided ViewModel; overridable in tests via a fake Hilt module.
 */
@Composable
fun ScamDetectorScreen(
    viewModel: ScamDetectorViewModel = hiltViewModel(),
) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val inputText         by viewModel.inputText.collectAsStateWithLifecycle()
    val history           by viewModel.history.collectAsStateWithLifecycle()
    val snackbarHostState  = remember { SnackbarHostState() }
    val haptic             = LocalHapticFeedback.current

    LaunchedEffect(uiState) {
        val state = uiState
        when (state) {
            is ScamDetectorUiState.Success -> {
                if (state.result.riskLevel == RiskLevel.DANGEROUS)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            is ScamDetectorUiState.Error   ->
                snackbarHostState.showSnackbar(
                    message = state.message
                )
            else -> Unit
        }
    }

    ScamDetectorContent(
        uiState           = uiState,
        inputText         = inputText,
        history           = history,
        snackbarHostState = snackbarHostState,
        exampleMessages   = viewModel.exampleMessages,
        onExampleSelected = viewModel::onExampleSelected,
        onInputChanged    = viewModel::onInputChanged,
        onAnalyzeClicked  = viewModel::analyzeMessage,
        onDeepScanClicked = viewModel::triggerDeepScan,
        onReset           = viewModel::reset,
        onDeleteHistory   = viewModel::deleteHistoryEntry,
        onClearHistory    = viewModel::clearHistory,
    )
}

// ---------------------------------------------------------------------------
// Stateless content composable
// ---------------------------------------------------------------------------

/**
 * Fully stateless content tree for the scam detector screen.
 *
 * All state is received as parameters and all events are dispatched via lambdas,
 * making this composable 100% previewable without a ViewModel or Hilt.
 *
 * Layout:
 *  - [TopAppBar] with reset button when a result is visible.
 *  - Scrollable [Column] containing example chips, input card, result card, and history.
 *  - [SnackbarHost] for transient error messages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScamDetectorContent(
    uiState: ScamDetectorUiState,
    inputText: String,
    history: List<ScanHistoryEntity>,
    snackbarHostState: SnackbarHostState,
    exampleMessages: List<ExampleMessage>,
    onExampleSelected: (ExampleMessage) -> Unit,
    onInputChanged: (String) -> Unit,
    onAnalyzeClicked: () -> Unit,
    onDeepScanClicked: () -> Unit,
    onReset: () -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onClearHistory: () -> Unit,
) {
    val isLoading = uiState is ScamDetectorUiState.Loading

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Scam Detector",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Paste a message or URL to analyze",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (uiState is ScamDetectorUiState.Success) {
                        IconButton(onClick = onReset) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset analysis")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedVisibility(
                visible = !isLoading,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
            ) {
                ExampleMessagesSection(
                    examples = exampleMessages,
                    currentInput = inputText,
                    onExampleSelected = onExampleSelected,
                )
            }

            InputCard(
                inputText = inputText,
                isLoading = isLoading,
                onInputChanged = onInputChanged,
                onAnalyzeClicked = onAnalyzeClicked,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 } togetherWith
                            fadeOut(tween(200))
                },
                label = "result_section_transition",
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { state ->
                when (state) {
                    is ScamDetectorUiState.Idle    -> {}
                    is ScamDetectorUiState.Loading -> ForensicLoadingCard()
                    is ScamDetectorUiState.Error   -> {}
                    is ScamDetectorUiState.Success -> ResultCard(
                        result            = state.result,
                        isDeepScanning    = state.isDeepScanning,
                        deepResult        = state.deepResult,
                        onDeepScanClicked = onDeepScanClicked,
                    )
                }
            }

            if (history.isNotEmpty()) {
                HistorySection(
                    history = history,
                    onDeleteEntry = onDeleteHistory,
                    onClearAll = onClearHistory,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Example messages section
// ---------------------------------------------------------------------------

/**
 * Returns an appropriate icon for an [ExampleMessage] based on its label string.
 * Falls back to a generic science icon for unrecognised labels.
 */
private fun iconForExample(label: String): ImageVector = when {
    label.contains("package", ignoreCase = true) ||
            label.contains("delivery", ignoreCase = true) -> Icons.Default.Email
    label.contains("bank", ignoreCase = true) ||
            label.contains("alert", ignoreCase = true)    -> Icons.Default.AccountBox
    else -> Icons.Outlined.Star
}

/**
 * Horizontal scrolling strip of [ExampleMessageCard] chips.
 *
 * @param examples        List of pre-written scam examples to display.
 * @param currentInput    Current text field value, used to highlight the selected example.
 * @param onExampleSelected Invoked when the user taps a chip.
 */
@Composable
private fun ExampleMessagesSection(
    examples: List<ExampleMessage>,
    currentInput: String,
    onExampleSelected: (ExampleMessage) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Try an example",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = examples, key = { it.label }) { example ->
                ExampleMessageCard(
                    example    = example,
                    isSelected = currentInput == example.body,
                    onClick    = { onExampleSelected(example) },
                )
            }
        }
    }
}

/**
 * A tappable card displaying a single [ExampleMessage].
 *
 * Visual state:
 *  - **Selected**: primary-colored border + tinted container background + "✓ Loaded".
 *  - **Unselected**: outline-variant border + surface background + "Tap to use →".
 *
 * @param example    The example data to display.
 * @param isSelected Whether this card's body matches the current text field value.
 * @param onClick    Invoked on tap.
 */
@Composable
private fun ExampleMessageCard(
    example: ExampleMessage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor     = if (isSelected) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.outlineVariant
    val containerColor  = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                          else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 2.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconForExample(example.label),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Column {
                    Text(
                        text = example.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "From: ${example.senderHint}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = example.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isSelected) "✓ Loaded" else "Tap to use →",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Input card
// ---------------------------------------------------------------------------

/**
 * Card containing the message text field and the Analyze button.
 *
 * The button is disabled while [isLoading] is `true` or [inputText] is blank
 * to prevent concurrent analysis requests.
 *
 * @param inputText       Current text field value.
 * @param isLoading       Whether an analysis is in flight.
 * @param onInputChanged  Invoked on every keystroke.
 * @param onAnalyzeClicked Invoked when the user taps "Analyze Message".
 * @param modifier        Applied to the outer [Card].
 */
@Composable
private fun InputCard(
    inputText: String,
    isLoading: Boolean,
    onInputChanged: (String) -> Unit,
    onAnalyzeClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                label = { Text("Message or URL") },
                placeholder = {
                    Text(
                        text = "e.g. \"Your account has been compromised. Click here to verify: bit.ly/3xFk91\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
                enabled = !isLoading,
                maxLines = 8,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAnalyzeClicked,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && inputText.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Analyzing…")
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Analyze Message", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Forensic loading card — animated console log output
// ---------------------------------------------------------------------------

/** Console lines that appear progressively during the heuristic scan. */
private val CONSOLE_LINES = listOf(
    "> INITIALIZING HEURISTIC KERNEL v2.4.1",
    "> LOADING RULE MATRIX [9 RULES]",
    "> TOKENIZING INPUT SEQUENCE",
    "> NFA PATTERN MATCHING  [O(N\u00B7M)]",
    "> URGENCY LANGUAGE ............. SCANNING",
    "> LINK MALICE VECTORS ........... SCANNING",
    "> CREDENTIAL PHISHING ........... SCANNING",
    "> FINANCIAL BAIT SIGNALS ........ SCANNING",
    "> EVALUATING RISK THRESHOLDS",
    "> COMPUTING RADAR SIGNATURE",
)

/**
 * "Security Lab" console card shown while the heuristic engine runs.
 *
 * Log lines are revealed one-by-one with [delay] inside a [LaunchedEffect],
 * simulating a kernel boot sequence. A blinking cursor indicates the scan is
 * still in progress. Uses the dark [ColorLabBackground] palette to signal a
 * distinct forensic analysis mode.
 */
@Composable
private fun ForensicLoadingCard() {
    var visibleCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        CONSOLE_LINES.forEachIndexed { index, _ ->
            delay(if (index == 0) 80L else 210L)
            visibleCount = index + 1
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursor",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = ColorLabBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = ColorLabAccent,
                    strokeWidth = 1.5.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    text = "FORENSIC ANALYSIS ENGINE",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorLabAccent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            CONSOLE_LINES.take(visibleCount).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = ColorLabText,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (visibleCount > 0) {
                Text(
                    text = "\u2588",
                    color = ColorLabAccent.copy(alpha = cursorAlpha),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Radar Chart (Spider Chart) — AI interpretability visualisation
// ---------------------------------------------------------------------------

/**
 * Four-axis radar chart that surfaces the heuristic engine's per-signal breakdown.
 *
 * **Axes and attack-vector mapping:**
 *  - **Urgency** — urgency language + account-threat rules.
 *  - **Link Malice** — shortened URLs, raw IPs, suspicious domains/TLDs.
 *  - **Financial Bait** — prize/lottery/inheritance language.
 *  - **Credential Req.** — phishing calls-to-action, impersonation, credential requests.
 *
 * **Rendering technique:**
 *  1. A [Canvas]-based polygon grid (rings at 25 / 50 / 75 / 100 %) provides scale context.
 *  2. Four axis lines radiate from the centre at 90° intervals.
 *  3. The score polygon animates from zero via [animateFloatAsState] + [FastOutSlowInEasing].
 *  4. Axis labels are drawn via [drawText] + [rememberTextMeasurer] directly in the DrawScope,
 *     eliminating the need for a separate overlay layout pass.
 *
 * **Time Complexity:** O(N · M) — inherited from the heuristic engine that produces [axisScores].
 *
 * @param axisScores  Map from axis name to normalised score [0f, 1f]. Empty map → all-zero chart.
 * @param riskLevel   Determines the polygon fill/stroke colour via [RiskLevel.primaryColor].
 * @param modifier    Applied to the outer [Canvas].
 */
@Composable
private fun RadarChart(
    axisScores: Map<String, Float>,
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier,
) {
    val axisKeys   = listOf("Urgency", "Link Malice", "Financial Bait", "Credential Request")
    val axisLabels = listOf("URGENCY", "LINK\nMALICE", "FINANCIAL\nBAIT", "CREDENTIAL\nREQ.")
    // Canvas angles: -π/2 = top, 0 = right, π/2 = bottom, π = left
    val angles     = listOf(-Math.PI / 2, 0.0, Math.PI / 2, Math.PI)
    val color      = riskLevel.primaryColor()
    val textMeasurer = rememberTextMeasurer()

    val animScore0 by animateFloatAsState(
        targetValue = (axisScores[axisKeys[0]] ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = FastOutSlowInEasing), label = "radar_0",
    )
    val animScore1 by animateFloatAsState(
        targetValue = (axisScores[axisKeys[1]] ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = FastOutSlowInEasing), label = "radar_1",
    )
    val animScore2 by animateFloatAsState(
        targetValue = (axisScores[axisKeys[2]] ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = FastOutSlowInEasing), label = "radar_2",
    )
    val animScore3 by animateFloatAsState(
        targetValue = (axisScores[axisKeys[3]] ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(1000, easing = FastOutSlowInEasing), label = "radar_3",
    )
    val animScores = listOf(animScore0, animScore1, animScore2, animScore3)

    val labelStyle = TextStyle(
        fontSize = 6.5.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        color = color.copy(alpha = 0.80f),
        textAlign = TextAlign.Center,
    )
    val measuredLabels = axisLabels.map { textMeasurer.measure(it, labelStyle) }

    Canvas(modifier = modifier.size(160.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = min(size.width, size.height) / 2f * 0.50f  // 50 % → room for labels at perimeter

        // ── Grid rings at 25 / 50 / 75 / 100 % ──────────────────────────────
        listOf(0.25f, 0.50f, 0.75f, 1.00f).forEach { level ->
            val gp = Path()
            angles.forEachIndexed { i, angle ->
                val px = (cx + level * r * cos(angle)).toFloat()
                val py = (cy + level * r * sin(angle)).toFloat()
                if (i == 0) gp.moveTo(px, py) else gp.lineTo(px, py)
            }
            gp.close()
            drawPath(gp, color = color.copy(alpha = 0.10f), style = Stroke(0.5.dp.toPx()))
        }

        // ── Axis lines ───────────────────────────────────────────────────────
        angles.forEach { angle ->
            drawLine(
                color       = color.copy(alpha = 0.20f),
                start       = Offset(cx, cy),
                end         = Offset((cx + r * cos(angle)).toFloat(), (cy + r * sin(angle)).toFloat()),
                strokeWidth = 0.5.dp.toPx(),
            )
        }

        // ── Score polygon — filled + stroked ─────────────────────────────────
        val sp = Path()
        angles.forEachIndexed { i, angle ->
            val s  = animScores[i]
            val px = (cx + s * r * cos(angle)).toFloat()
            val py = (cy + s * r * sin(angle)).toFloat()
            if (i == 0) sp.moveTo(px, py) else sp.lineTo(px, py)
        }
        sp.close()
        drawPath(sp, color = color.copy(alpha = 0.25f))
        drawPath(sp, color = color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))

        // ── Vertex dots ───────────────────────────────────────────────────────
        angles.forEachIndexed { i, angle ->
            val s = animScores[i]
            if (s > 0.02f) {
                drawCircle(
                    color  = color,
                    radius = 2.5.dp.toPx(),
                    center = Offset((cx + s * r * cos(angle)).toFloat(), (cy + s * r * sin(angle)).toFloat()),
                )
            }
        }

        // ── Axis labels (drawn via TextMeasurer inside DrawScope) ─────────────
        val labelR = r + 22.dp.toPx()
        angles.forEachIndexed { i, angle ->
            val ml = measuredLabels[i]
            val lx = (cx + labelR * cos(angle)).toFloat()
            val ly = (cy + labelR * sin(angle)).toFloat()
            drawText(
                textLayoutResult = ml,
                topLeft = Offset(lx - ml.size.width / 2f, ly - ml.size.height / 2f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Result card — heuristic verdict + deep scan section
// ---------------------------------------------------------------------------

/**
 * Displays the completed heuristic analysis result and the optional Deep AI scan panel.
 *
 * Layout:
 *  - Header label.
 *  - Risk badge row (left) + [RadarChart] interpretability chart (right).
 *  - Explanation surface.
 *  - Flagged signal chips (hidden when empty).
 *  - Deep AI scan section: button → [ForensicLoadingCard] shimmer → [SecurityLabCard].
 *
 * @param result            Heuristic engine verdict including per-axis [AnalysisResult.axisScores].
 * @param isDeepScanning    Whether the deep scan coroutine is running.
 * @param deepResult        Completed deep scan result, or `null` if not yet triggered.
 * @param onDeepScanClicked Invoked when the user taps "Deep AI Scan".
 */
@Composable
private fun ResultCard(
    result: AnalysisResult,
    isDeepScanning: Boolean,
    deepResult: DeepAnalysisResult?,
    onDeepScanClicked: () -> Unit,
) {
    val riskColor     = result.riskLevel.primaryColor()
    val riskContainer = result.riskLevel.containerColor()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text(
                text = "Analysis Result",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            // Risk badge + Security Gauge side-by-side
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Risk level badge
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(riskContainer)
                        .border(1.dp, riskColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(riskColor),
                    )
                    Text(
                        text = result.riskLevel.displayLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = riskColor,
                    )
                }

                Spacer(Modifier.width(16.dp))

                // Radar chart — per-axis interpretability breakdown
                RadarChart(
                    axisScores = result.axisScores,
                    riskLevel  = result.riskLevel,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Explanation surface
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Why this verdict?",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = result.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp,
                    )
                }
            }

            // Flagged signal chips
            AnimatedVisibility(visible = result.flaggedTokens.isNotEmpty()) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Flagged signals",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        result.flaggedTokens.forEach { token ->
                            TokenChip(text = token, color = riskColor)
                        }
                    }
                }
            }

            // Deep AI scan section — button → shimmer → result
            AnimatedVisibility(visible = result.riskLevel != RiskLevel.SAFE) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    when {
                        deepResult != null -> SecurityLabCard(deepResult = deepResult)
                        isDeepScanning     -> DeepScanShimmer()
                        else               -> DeepScanButton(onClick = onDeepScanClicked)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Deep scan — trigger button
// ---------------------------------------------------------------------------

/**
 * Dark-styled button that initiates the Deep AI scan.
 *
 * Uses [ColorLabBackground] and [ColorLabAccent] to visually signal the transition
 * into the "Security Lab" analysis tier.
 */
@Composable
private fun DeepScanButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ColorLabBackground,
            contentColor   = ColorLabAccent,
        ),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Deep AI Scan",
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Deep scan — shimmer loading effect
// ---------------------------------------------------------------------------

/**
 * "Security Lab" shimmer shown while the deep scan coroutine runs.
 *
 * Two [InfiniteTransition] animations run concurrently on the same transition object:
 *  1. **shimmerOffset** — the x-position of a [Brush.linearGradient] sweeps left-to-right
 *     at a constant [LinearEasing] rate, creating the classic travelling highlight.
 *  2. **textAlpha** — the status label pulses between 40% and 100% opacity with
 *     [FastOutSlowInEasing] for a layered breathing effect.
 *
 * Both use [RepeatMode.Restart] / [RepeatMode.Reverse] respectively so they
 * never stutter at loop boundaries.
 */
@Composable
private fun DeepScanShimmer() {
    val infiniteTransition = rememberInfiniteTransition(label = "deep_scan_shimmer")

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -600f,
        targetValue  = 1200f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )

    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "text_alpha",
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(ColorShimmer1, ColorShimmer2, ColorShimmer3),
        start  = Offset(shimmerOffset, 0f),
        end    = Offset(shimmerOffset + 400f, 0f),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ColorLabBackground)
            .border(1.dp, ColorLabBorder, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(shimmerBrush))
                Box(modifier = Modifier.height(14.dp).width(140.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush))
            }
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 2) 0.65f else 1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(14.dp),
                    color       = ColorLabAccent,
                    strokeWidth = 2.dp,
                    strokeCap   = StrokeCap.Round,
                )
                Text(
                    text  = "Deep AI analysis in progress…",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorLabAccent.copy(alpha = textAlpha),
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.4.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Deep scan — Security Lab result card
// ---------------------------------------------------------------------------

/**
 * Displays a completed [DeepAnalysisResult] in a dark "Security Lab" panel.
 *
 * Visual language:
 *  - Dark [ColorLabBackground] signals a distinct analysis tier.
 *  - Cyan [ColorLabAccent] matches the scan theme and draws the eye to the badge.
 *  - Monospace font for the tactic label reinforces the forensic / technical tone.
 *  - [AiBadge] communicates whether the result is live AI or simulated.
 *
 * @param deepResult The completed deep analysis to display.
 */
@Composable
private fun SecurityLabCard(deepResult: DeepAnalysisResult) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ColorLabBackground)
            .border(1.dp, ColorLabBorder, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ColorLabAccent.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = ColorLabAccent,
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Security Lab",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = ColorLabText,
                        )
                        Text(
                            text = "Deep AI Analysis",
                            style = MaterialTheme.typography.labelSmall,
                            color = ColorLabTextMuted,
                        )
                    }
                }
                AiBadge(isSimulated = deepResult.isSimulated)
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ColorLabBorder))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "PRIMARY TACTIC",
                    style = MaterialTheme.typography.labelSmall,
                    color = ColorLabTextMuted,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = deepResult.tactic,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = ColorLabAccent,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = ColorLabTextMuted,
                    )
                    Text(
                        text = "Risk Reasoning",
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorLabTextMuted,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = deepResult.riskReasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorLabText,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// AI badge
// ---------------------------------------------------------------------------

/**
 * Small pill badge displaying "AI VERIFIED" (live) or "SIMULATED" (offline).
 *
 * Uses a semi-transparent background + contrasting border to read clearly
 * on both [ColorLabBackground] and light surfaces.
 *
 * @param isSimulated `true` when the result was produced by the offline fallback.
 */
@Composable
private fun AiBadge(isSimulated: Boolean) {
    val badgeColor = if (isSimulated) ColorLabTextMuted else ColorLabAccent
    val label      = if (isSimulated) "SIMULATED" else "AI VERIFIED"

    Surface(
        shape = RoundedCornerShape(50),
        color = badgeColor.copy(alpha = 0.12f),
        modifier = Modifier.border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(50)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(badgeColor))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Token chip
// ---------------------------------------------------------------------------

/**
 * Inline pill chip displaying a single flagged signal token.
 *
 * Semi-transparent fill + contrasting border ensures readability on both light
 * and dark surface backgrounds.
 *
 * @param text  The token string to display (e.g. "Shortened URL (bit.ly)").
 * @param color The risk-level color used for text, fill, and border.
 */
@Composable
private fun TokenChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(50)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------
// History section
// ---------------------------------------------------------------------------

/** Formats a Unix epoch millisecond timestamp to a short locale-aware date string. */
private fun Long.toDateString(): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(this))

/**
 * A list of past scan results loaded from the Room database.
 *
 * Displays a header with a "Clear all" icon button, followed by one
 * [HistoryItem] row per [ScanHistoryEntity], separated by [HorizontalDivider]s.
 *
 * @param history       List of persisted scan records, newest-first.
 * @param onDeleteEntry Invoked with the record's primary key when the delete icon is tapped.
 * @param onClearAll    Invoked when the "Clear all" button is tapped.
 * @param modifier      Applied to the outer [Card].
 */
@Composable
private fun HistorySection(
    history: List<ScanHistoryEntity>,
    onDeleteEntry: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Scan History",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onClearAll, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear all history",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            history.forEachIndexed { index, entry ->
                HistoryItem(entry = entry, onDelete = { onDeleteEntry(entry.id) })
                if (index < history.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/**
 * A single row in the history list representing one past scan.
 *
 * Displays:
 *  - A colored risk-level dot.
 *  - The risk level name and timestamp.
 *  - A truncated preview of the scanned message.
 *  - A **view** icon button (cyan) that opens [HistoryDetailDialog].
 *  - A **delete** icon button.
 *
 * @param entry    The persisted scan record to display.
 * @param onDelete Invoked when the delete icon is tapped.
 */
@Composable
private fun HistoryItem(
    entry: ScanHistoryEntity,
    onDelete: () -> Unit,
) {
    val riskLevel = try {
        RiskLevel.valueOf(entry.riskLevel)
    } catch (_: IllegalArgumentException) {
        RiskLevel.SAFE
    }
    val riskColor = riskLevel.primaryColor()
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        HistoryDetailDialog(
            entry     = entry,
            riskLevel = riskLevel,
            onDismiss = { showDialog = false },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(riskColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = riskLevel.displayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = riskColor,
                )
                Text(
                    text = entry.timestamp.toDateString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = entry.inputText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // View details
        IconButton(onClick = { showDialog = true }, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "View scan details",
                modifier = Modifier.size(14.dp),
                tint = ColorLabAccent,
            )
        }
        // Delete entry
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete entry",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Full-detail dialog for a single [ScanHistoryEntity].
 *
 * Surfaces the full message text, risk level, confidence percentage, explanation
 * narrative, and individual flagged signal tokens — information otherwise truncated
 * in the compact [HistoryItem] row.
 *
 * @param entry     The scan record to display.
 * @param riskLevel Pre-parsed [RiskLevel] (avoids re-parsing inside the dialog).
 * @param onDismiss Called when the user taps "Close" or dismisses the dialog.
 */
@Composable
private fun HistoryDetailDialog(
    entry: ScanHistoryEntity,
    riskLevel: RiskLevel,
    onDismiss: () -> Unit,
) {
    val riskColor = riskLevel.primaryColor()
    val tokens    = entry.flaggedTokens.split("|").filter { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(riskColor),
                )
                Text(
                    text = "${riskLevel.displayLabel} — ${entry.timestamp.toDateString()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Message",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = entry.inputText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp),
                        lineHeight = 18.sp,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text(
                            text = "Confidence",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${(entry.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = riskColor,
                        )
                    }
                }
                Text(
                    text = "Explanation",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = entry.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp,
                )
                if (tokens.isNotEmpty()) {
                    Text(
                        text = "Flagged signals",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tokens.forEach { TokenChip(text = it, color = riskColor) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Idle")
@Composable
private fun PreviewIdle() {
    MaterialTheme {
        ScamDetectorContent(
            uiState = ScamDetectorUiState.Idle,
            inputText = "",
            history = emptyList(),
            snackbarHostState = remember { SnackbarHostState() },
            exampleMessages = listOf(
                ExampleMessage("Package Delivery", "USPS Alerts", "USPS: Your package is on hold."),
                ExampleMessage("Bank Alert", "Chase Security", "URGENT: Suspicious login detected."),
            ),
            onExampleSelected = {}, onInputChanged = {}, onAnalyzeClicked = {},
            onDeepScanClicked = {}, onReset = {}, onDeleteHistory = {}, onClearHistory = {},
        )
    }
}

@Preview(showBackground = true, name = "Success — Radar Chart")
@Composable
private fun PreviewSuccessWithGauge() {
    val body = "URGENT: Click here to verify your account"
    MaterialTheme {
        ScamDetectorContent(
            uiState = ScamDetectorUiState.Success(
                inputText = body,
                result = AnalysisResult(
                    riskLevel = RiskLevel.SUSPICIOUS,
                    confidence = 0.55f,
                    explanation = "Urgency language and a phishing call-to-action detected.",
                    flaggedTokens = listOf("urgent", "click here"),
                    axisScores = mapOf(
                        "Urgency" to 1.0f, "Link Malice" to 0.0f,
                        "Financial Bait" to 0.0f, "Credential Request" to 0.45f,
                    ),
                ),
            ),
            inputText = body,
            history = emptyList(),
            snackbarHostState = remember { SnackbarHostState() },
            exampleMessages = emptyList(),
            onExampleSelected = {}, onInputChanged = {}, onAnalyzeClicked = {},
            onDeepScanClicked = {}, onReset = {}, onDeleteHistory = {}, onClearHistory = {},
        )
    }
}

@Preview(showBackground = true, name = "Success — Security Lab card")
@Composable
private fun PreviewSecurityLabCard() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SecurityLabCard(
                deepResult = DeepAnalysisResult(
                    tactic = "Urgency & Fear Induction",
                    riskReasoning = "The message employs high-pressure social engineering by " +
                            "creating an artificial time constraint and threatening account suspension.",
                    isSimulated = true,
                )
            )
        }
    }
}

@Preview(showBackground = true, name = "History section")
@Composable
private fun PreviewHistory() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            HistorySection(
                history = listOf(
                    ScanHistoryEntity(1L, "URGENT: Verify your account now", "DANGEROUS", 0.85f, "Multiple indicators.", "urgent|click here", System.currentTimeMillis()),
                    ScanHistoryEntity(2L, "Hello, your package is arriving today.", "SAFE", 0.05f, "No indicators.", "", System.currentTimeMillis() - 3_600_000),
                ),
                onDeleteEntry = {},
                onClearAll = {},
            )
        }
    }
}
