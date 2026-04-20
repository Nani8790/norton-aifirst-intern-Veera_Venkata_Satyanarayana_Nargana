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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veera.scammessagedetector.model.AnalysisResult
import com.veera.scammessagedetector.model.RiskLevel

// ---------------------------------------------------------------------------
// Risk-level color tokens
// ---------------------------------------------------------------------------

private val ColorSafe               = Color(0xFF2E7D32)
private val ColorSafeContainer      = Color(0xFFE8F5E9)
private val ColorSuspicious         = Color(0xFFE65100)
private val ColorSuspiciousContainer = Color(0xFFFFF3E0)
private val ColorDangerous          = Color(0xFFB71C1C)
private val ColorDangerousContainer  = Color(0xFFFFEBEE)

// Security Lab dark-theme tokens
private val ColorLabBackground = Color(0xFF0D1117) // deep-dark panel
private val ColorLabSurface    = Color(0xFF161B22) // card surface
private val ColorLabAccent     = Color(0xFF00B4D8) // cyan — AI badge / scan line
private val ColorLabBorder     = Color(0xFF30363D) // subtle border
private val ColorLabText       = Color(0xFFCDD9E5) // primary text on dark
private val ColorLabTextMuted  = Color(0xFF8B949E) // secondary text on dark

// Shimmer tile colours (used by the brush gradient)
private val ColorShimmer1 = Color(0xFF1C2233)
private val ColorShimmer2 = Color(0xFF2A3347)
private val ColorShimmer3 = Color(0xFF1C2233)

private fun RiskLevel.primaryColor() = when (this) {
    RiskLevel.SAFE       -> ColorSafe
    RiskLevel.SUSPICIOUS -> ColorSuspicious
    RiskLevel.DANGEROUS  -> ColorDangerous
}

private fun RiskLevel.containerColor() = when (this) {
    RiskLevel.SAFE       -> ColorSafeContainer
    RiskLevel.SUSPICIOUS -> ColorSuspiciousContainer
    RiskLevel.DANGEROUS  -> ColorDangerousContainer
}

// ---------------------------------------------------------------------------
// Root screen — wires ViewModel to UI
// ---------------------------------------------------------------------------

@Composable
fun ScamDetectorScreen(
    viewModel: ScamDetectorViewModel = viewModel(),
) {
    val uiState          by viewModel.uiState.collectAsStateWithLifecycle()
    val inputText        by viewModel.inputText.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        if (uiState is ScamDetectorUiState.Error) {
            snackbarHostState.showSnackbar(
                message = (uiState as ScamDetectorUiState.Error).message
            )
        }
    }

    ScamDetectorContent(
        uiState           = uiState,
        inputText         = inputText,
        snackbarHostState = snackbarHostState,
        exampleMessages   = viewModel.exampleMessages,
        onExampleSelected = viewModel::onExampleSelected,
        onInputChanged    = viewModel::onInputChanged,
        onAnalyzeClicked  = viewModel::analyzeMessage,
        onDeepScanClicked = viewModel::triggerDeepScan,
        onReset           = viewModel::reset,
    )
}

// ---------------------------------------------------------------------------
// Stateless content composable
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScamDetectorContent(
    uiState: ScamDetectorUiState,
    inputText: String,
    snackbarHostState: SnackbarHostState,
    exampleMessages: List<ExampleMessage>,
    onExampleSelected: (ExampleMessage) -> Unit,
    onInputChanged: (String) -> Unit,
    onAnalyzeClicked: () -> Unit,
    onDeepScanClicked: () -> Unit,
    onReset: () -> Unit,
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
                    is ScamDetectorUiState.Loading -> LoadingCard()
                    is ScamDetectorUiState.Error   -> {}
                    is ScamDetectorUiState.Success -> ResultCard(
                        result            = state.result,
                        isDeepScanning    = state.isDeepScanning,
                        deepResult        = state.deepResult,
                        onDeepScanClicked = onDeepScanClicked,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Example messages section
// ---------------------------------------------------------------------------

private fun iconForExample(label: String): ImageVector = when {
    label.contains("package", ignoreCase = true) ||
            label.contains("delivery", ignoreCase = true) -> Icons.Default.LocalShipping
    label.contains("bank", ignoreCase = true) ||
            label.contains("alert", ignoreCase = true)    -> Icons.Default.AccountBalance
    else -> Icons.Outlined.Science
}

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
                imageVector = Icons.Outlined.Science,
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

@Composable
private fun ExampleMessageCard(
    example: ExampleMessage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
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
                modifier = Modifier.fillMaxWidth().height(160.dp),
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
                contentPadding = ButtonDefaults.ContentPadding,
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
// Loading card
// ---------------------------------------------------------------------------

@Composable
private fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                strokeCap = StrokeCap.Round,
            )
            Text(
                text = "Scanning for threats…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Result card — heuristic verdict + deep scan section
// ---------------------------------------------------------------------------

@Composable
private fun ResultCard(
    result: AnalysisResult,
    isDeepScanning: Boolean,
    deepResult: DeepAnalysisResult?,
    onDeepScanClicked: () -> Unit,
) {
    val riskColor     = result.riskLevel.primaryColor()
    val riskContainer = result.riskLevel.containerColor()

    val animatedConfidence by animateFloatAsState(
        targetValue = result.confidence,
        animationSpec = tween(durationMillis = 800, delayMillis = 100),
        label = "confidence_bar",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header ───────────────────────────────────────────────────────
            Text(
                text = "Analysis Result",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            // ── Risk level badge ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(riskContainer)
                    .border(1.dp, riskColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(12.dp).clip(CircleShape).background(riskColor),
                )
                Text(
                    text = result.riskLevel.displayLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = riskColor,
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Confidence score ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Confidence", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${result.confidencePercent}%", style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold, color = riskColor)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { animatedConfidence },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = riskColor,
                trackColor = riskColor.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round,
            )

            Spacer(Modifier.height(16.dp))

            // ── Explanation ──────────────────────────────────────────────────
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
                        Icon(Icons.Default.Warning, contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

            // ── Flagged tokens ───────────────────────────────────────────────
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

            // ── Deep AI scan section ─────────────────────────────────────────
            // Only shown for non-SAFE results. Transitions through three states:
            //   1. Button — scan not yet requested
            //   2. Shimmer — scan in progress
            //   3. Security Lab card — result available
            AnimatedVisibility(visible = result.riskLevel != RiskLevel.SAFE) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    when {
                        deepResult != null  -> SecurityLabCard(deepResult = deepResult)
                        isDeepScanning      -> DeepScanShimmer()
                        else                -> DeepScanButton(onClick = onDeepScanClicked)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Deep scan — trigger button
// ---------------------------------------------------------------------------

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
            imageVector = Icons.Outlined.Psychology,
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
// Deep scan — shimmer pulse loading effect
// ---------------------------------------------------------------------------

/**
 * A "Security Lab" style shimmer displayed while the deep scan coroutine runs.
 *
 * Technique: an [InfiniteTransition] animates the x-offset of a
 * [Brush.linearGradient], creating the classic travelling-highlight shimmer.
 * The pulsing "SCANNING…" label uses a separate alpha animation on the same
 * transition for a layered feel with no extra coroutines.
 */
@Composable
private fun DeepScanShimmer() {
    val infiniteTransition = rememberInfiniteTransition(label = "deep_scan_shimmer")

    // Horizontal sweep animation for the gradient highlight
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -600f,
        targetValue  = 1200f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )

    // Separate pulse for the status text
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

            // Animated header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush),
                )
                Box(
                    modifier = Modifier
                        .height(14.dp)
                        .width(140.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush),
                )
            }

            // Shimmer content lines
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 2) 0.65f else 1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush),
                )
            }

            // Animated status label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CircularProgressIndicator(
                    modifier  = Modifier.size(14.dp),
                    color     = ColorLabAccent,
                    strokeWidth = 2.dp,
                    strokeCap = StrokeCap.Round,
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
 * Displays the completed [DeepAnalysisResult] in a dark "Security Lab" panel.
 *
 * Visual language:
 *  - Dark background ([ColorLabBackground]) signals a distinct analysis layer.
 *  - Cyan accent ([ColorLabAccent]) matches the scan theme and draws the eye
 *    to the "AI Verified" / "Simulated" badge.
 *  - Monospace font for the tactic label reinforces the technical / forensic tone.
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

            // ── Card header ──────────────────────────────────────────────────
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
                                imageVector = Icons.Outlined.Psychology,
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

                // "AI Verified" or "Simulated" badge
                AiBadge(isSimulated = deepResult.isSimulated)
            }

            // ── Divider ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ColorLabBorder),
            )

            // ── Tactic label ─────────────────────────────────────────────────
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

            // ── Reasoning ────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
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
// AI badge — shown inside SecurityLabCard
// ---------------------------------------------------------------------------

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
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
            )
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
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Idle")
@Composable
private fun PreviewIdle() {
    MaterialTheme {
        ScamDetectorContent(
            uiState = ScamDetectorUiState.Idle,
            inputText = "",
            snackbarHostState = remember { SnackbarHostState() },
            exampleMessages = listOf(
                ExampleMessage("Package Delivery", "USPS Alerts", "USPS: Your package is on hold."),
                ExampleMessage("Bank Alert", "Chase Security", "URGENT: Suspicious login detected."),
            ),
            onExampleSelected = {}, onInputChanged = {}, onAnalyzeClicked = {},
            onDeepScanClicked = {}, onReset = {},
        )
    }
}

@Preview(showBackground = true, name = "Success — Button visible")
@Composable
private fun PreviewSuccessWithDeepButton() {
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
                ),
            ),
            inputText = body,
            snackbarHostState = remember { SnackbarHostState() },
            exampleMessages = listOf(
                ExampleMessage("Package Delivery", "USPS Alerts", "USPS: Your package is on hold."),
                ExampleMessage("Bank Alert", "Chase Security", "URGENT: Suspicious login detected."),
            ),
            onExampleSelected = {}, onInputChanged = {}, onAnalyzeClicked = {},
            onDeepScanClicked = {}, onReset = {},
        )
    }
}

@Preview(showBackground = true, name = "Success — Shimmer")
@Composable
private fun PreviewSuccessShimmer() {
    val body = "URGENT: Click here to verify your account"
    MaterialTheme {
        ScamDetectorContent(
            uiState = ScamDetectorUiState.Success(
                inputText = body,
                result = AnalysisResult(
                    riskLevel = RiskLevel.SUSPICIOUS,
                    confidence = 0.55f,
                    explanation = "Urgency language detected.",
                    flaggedTokens = listOf("urgent"),
                ),
                isDeepScanning = true,
            ),
            inputText = body,
            snackbarHostState = remember { SnackbarHostState() },
            exampleMessages = emptyList(),
            onExampleSelected = {}, onInputChanged = {}, onAnalyzeClicked = {},
            onDeepScanClicked = {}, onReset = {},
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