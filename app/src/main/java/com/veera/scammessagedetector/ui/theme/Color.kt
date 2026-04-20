package com.veera.scammessagedetector.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Material 3 base palette — security-focused brand colors
// ---------------------------------------------------------------------------

// Primary: deep security blue — interactive elements, top app bar actions
val SecurityBlue80 = Color(0xFFADC6FF)
val SecurityBlue40 = Color(0xFF1A56DB)

// Secondary: slate grey — complements primary without competing with risk colors
val SlateGrey80 = Color(0xFFBEC6DC)
val SlateGrey40 = Color(0xFF565F71)

// Tertiary: warm accent — secondary CTAs
val WarmAmber80 = Color(0xFFEFB8C8)
val WarmAmber40 = Color(0xFF7D5260)

// ---------------------------------------------------------------------------
// Semantic risk signal colors
// ---------------------------------------------------------------------------

/** SAFE verdict — deep green, confident safety. */
val RiskSafe = Color(0xFF1B5E20)

/** SUSPICIOUS verdict — burnt orange, cautionary signal. */
val RiskSuspicious = Color(0xFFE65100)

/** DANGEROUS verdict — deep red, strong alert. */
val RiskDangerous = Color(0xFFB71C1C)

// ---------------------------------------------------------------------------
// Security Lab dark panel tokens
// ---------------------------------------------------------------------------

/** Deep-dark background for the Security Lab AI analysis panel. */
val LabBackground = Color(0xFF0D1117)

/** Cyan accent for AI badge, scan-line, and monospace labels in the Lab panel. */
val LabCyanAccent = Color(0xFF00B4D8)
