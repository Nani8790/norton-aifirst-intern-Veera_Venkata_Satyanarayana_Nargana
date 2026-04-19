package com.veera.scammessagedetector.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
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
// Color tokens for risk levels — defined here so they stay co-located with
// the UI layer. Move to the theme file once you have a design system.
// ---------------------------------------------------------------------------
private val ColorSafe = Color(0xFF2E7D32)
private val ColorSafeContainer = Color(0xFFE8F5E9)
private val ColorSuspicious = Color(0xFFE65100)
private val ColorSuspiciousContainer = Color(0xFFFFF3E0)
private val ColorDangerous = Color(0xFFB71C1C)
private val ColorDangerousContainer = Color(0xFFFFEBEE)

private fun RiskLevel.primaryColor() = when (this) {
    RiskLevel.SAFE -> ColorSafe
    RiskLevel.SUSPICIOUS -> ColorSuspicious
    RiskLevel.DANGEROUS -> ColorDangerous
}

private fun RiskLevel.containerColor() = when (this) {
    RiskLevel.SAFE -> ColorSafeContainer
    RiskLevel.SUSPICIOUS -> ColorSuspiciousContainer
    RiskLevel.DANGEROUS -> ColorDangerousContainer
}

// ---------------------------------------------------------------------------
// Root screen — wires ViewModel to UI
// ---------------------------------------------------------------------------

@Composable
fun ScamDetectorScreen(
    viewModel: ScamDetectorViewModel = viewModel(),
) {
    // `collectAsStateWithLifecycle` is the lifecycle-aware alternative to
    // `collectAsState`. It stops collecting when the app is backgrounded,
    // preventing unnecessary recompositions and battery drain.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface errors via Snackbar for non-critical feedback.
    LaunchedEffect(uiState) {
        if (uiState is ScamDetectorUiState.Error) {
            snackbarHostState.showSnackbar(
                message = (uiState as ScamDetectorUiState.Error).message
            )
        }
    }

    ScamDetectorContent(
        uiState = uiState,
        inputText = inputText,
        snackbarHostState = snackbarHostState,
        exampleMessages = viewModel.exampleMessages,
        onExampleSelected = viewModel::onExampleSelected,
        onInputChanged = viewModel::onInputChanged,
        onAnalyzeClicked = viewModel::analyzeMessage,
        onReset = viewModel::reset,
    )
}

// ---------------------------------------------------------------------------
// Stateless content composable — separated from the ViewModel wiring so it
// can be previewed and tested in isolation.
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
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset analysis",
                            )
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

            // ----------------------------------------------------------------
            // Example messages — horizontally scrolling cards above the input.
            // Hidden while loading so the interaction surface is unambiguous.
            // ----------------------------------------------------------------
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

            // ----------------------------------------------------------------
            // Input section
            // ----------------------------------------------------------------
            InputCard(
                inputText = inputText,
                isLoading = isLoading,
                onInputChanged = onInputChanged,
                onAnalyzeClicked = onAnalyzeClicked,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // ----------------------------------------------------------------
            // Result section — uses AnimatedContent for smooth transitions
            // ----------------------------------------------------------------
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
                    is ScamDetectorUiState.Idle -> { /* Nothing to show yet */ }
                    is ScamDetectorUiState.Loading -> LoadingCard()
                    is ScamDetectorUiState.Success -> ResultCard(result = state.result)
                    is ScamDetectorUiState.Error -> { /* Handled via Snackbar */ }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Example messages section
// ---------------------------------------------------------------------------

/**
 * Maps a label string to a contextual icon.
 * Extend this as new example categories are added.
 */

private fun iconForExample(label: String): ImageVector = when {
    label.contains("package", ignoreCase = true) ||
            label.contains("delivery", ignoreCase = true) -> Icons.Default.LocalShipping
    label.contains("bank", ignoreCase = true) ||
            label.contains("alert", ignoreCase = true) -> Icons.Default.AccountBalance
    else -> Icons.Outlined.Science
}

/**
 * A horizontally scrolling row of example scam cards.
 *
 * Uses [LazyRow] (not a plain Row) so the list is ready to scale when more
 * examples are added without any layout changes.
 *
 * The selected card is visually highlighted via a primary-color border so the
 * user has clear feedback that the input has been populated.
 *
 * @param examples          The list of examples from the ViewModel.
 * @param currentInput      The live text in the input field — used to highlight
 *                          whichever card is currently loaded.
 * @param onExampleSelected Callback fired when the user taps a card.
 */
@Composable
private fun ExampleMessagesSection(
    examples: List<ExampleMessage>,
    currentInput: String,
    onExampleSelected: (ExampleMessage) -> Unit,
) {
    Column {
        // Section header — aligned with the rest of the screen content
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

        // LazyRow bleeds edge-to-edge; leading/trailing padding via contentPadding
        // keeps the first card aligned with the rest of the screen content.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = examples,
                key = { it.label }, // stable key = no unnecessary recompositions
            ) { example ->
                ExampleMessageCard(
                    example = example,
                    isSelected = currentInput == example.body,
                    onClick = { onExampleSelected(example) },
                )
            }
        }
    }
}

/**
 * A single tappable example card.
 *
 * Design notes:
 *  - Fixed max-width (280 dp) so two cards are partially visible, hinting at scroll.
 *  - Selected state replaces the subtle border with a full primary-color stroke
 *    and shifts the background to primaryContainer — clear feedback without noise.
 *  - Body text is clamped to 3 lines so cards stay a consistent height.
 */
@Composable
private fun ExampleMessageCard(
    example: ExampleMessage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 0.dp else 2.dp,
        ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Card header — icon + label + spoofed sender
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

            // Body preview — clamped to keep cards uniform height
            Text(
                text = example.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
            )

            Spacer(Modifier.height(8.dp))

            // Tap affordance label
            Text(
                text = if (isSelected) "✓ Loaded" else "Tap to use →",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
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
                        text = "e.g. \"Your account has been compromised. " +
                                "Click here to verify: bit.ly/3xFk91\"",
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
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
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
// Result card
// ---------------------------------------------------------------------------

@Composable
private fun ResultCard(result: AnalysisResult) {
    val riskColor = result.riskLevel.primaryColor()
    val riskContainer = result.riskLevel.containerColor()

    // Animate the confidence bar filling from 0 → actual value on first composition.
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

            // Header label
            Text(
                text = "Analysis Result",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            // Risk level badge
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
                // Colored dot indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(riskColor),
                )
                Text(
                    text = result.riskLevel.displayLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = riskColor,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Confidence score row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Confidence",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${result.confidencePercent}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = riskColor,
                )
            }

            Spacer(Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { animatedConfidence },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = riskColor,
                trackColor = riskColor.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round,
            )

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

            // Flagged tokens — shown only when present
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

@Preview(showBackground = true, name = "Idle State")
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
            onExampleSelected = {},
            onInputChanged = {},
            onAnalyzeClicked = {},
            onReset = {},
        )
    }
}

@Preview(showBackground = true, name = "Success — Suspicious")
@Composable
private fun PreviewSuspicious() {
    val body = "Click here to verify your account"
    MaterialTheme {
        ScamDetectorContent(
            uiState = ScamDetectorUiState.Success(
                inputText = body,
                result = AnalysisResult(
                    riskLevel = RiskLevel.SUSPICIOUS,
                    confidence = 0.78f,
                    explanation = "Urgent language and shortened URL detected.",
                    flaggedTokens = listOf("urgent", "click here", "bit.ly/3xFk91"),
                ),
            ),
            inputText = body,
            snackbarHostState = remember { SnackbarHostState() },
            exampleMessages = listOf(
                ExampleMessage("Package Delivery", "USPS Alerts", "USPS: Your package is on hold."),
                ExampleMessage("Bank Alert", "Chase Security", "URGENT: Suspicious login detected."),
            ),
            onExampleSelected = {},
            onInputChanged = {},
            onAnalyzeClicked = {},
            onReset = {},
        )
    }
}

@Preview(showBackground = true, name = "Example Card — Selected")
@Composable
private fun PreviewExampleCardSelected() {
    val example = ExampleMessage(
        label = "Bank Alert",
        senderHint = "Chase Security",
        body = "URGENT: A suspicious login attempt to your Chase account was blocked.",
    )
    MaterialTheme {
        ExampleMessageCard(
            example = example,
            isSelected = true,
            onClick = {},
        )
    }
}