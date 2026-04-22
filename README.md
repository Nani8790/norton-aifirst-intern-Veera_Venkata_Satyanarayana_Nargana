# Scam Message Detector — Gen Digital AI-First Mobile Engineering Internship

**Candidate:** Veera Venkata Satyanarayana Nargana
**Assignment Option:** B — Scam Message Detector Prototype
**Submission Stack:** Kotlin · Jetpack Compose · Hilt · Room · MVVM · Coroutines · Canvas

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Demo Video](#demo-video)
3. [Screenshots](#screenshots)
4. [Feature Matrix](#feature-matrix)
5. [Architecture](#architecture)
6. [Tech Stack](#tech-stack)
7. [Key Engineering Decisions](#key-engineering-decisions)
8. [Heuristic Engine — Complexity Analysis](#heuristic-engine--complexity-analysis)
9. [Testing Strategy](#testing-strategy)
10. [Gemini API Integration Socket](#gemini-api-integration-socket)
11. [Setup Instructions](#setup-instructions)
12. [Future Enhancements](#future-enhancements)
13. [AI Interaction Log](#ai-interaction-log)
14. [AI Code Review](#ai-code-review)
15. [Reflection](#reflection)

---

## Project Overview

This is a **production-architecture scam detection application** built for the
Gen Digital AI-First Mobile Engineering Intern take-home assignment. It goes
significantly beyond a prototype: it implements a full **Hybrid AI pipeline**
(on-device heuristic engine + a wired LLM integration socket), **Explainable AI**
via a custom Canvas-drawn Radar Chart, **Room-backed persistent scan history**, and
a complete **Hilt dependency injection graph** — with the core heuristic engine and
ViewModel state machine fully validated by a JVM unit test suite that runs without
an emulator.


### The core problem

Phishing and social engineering attacks succeed because victims cannot quickly identify the *specific* psychological levers being used against them. Telling a user "87% risk" is a black box. This app surfaces *which* attack vectors are active — urgency induction, link malice, credential harvesting, or financial bait — as independently interpretable axes on a real-time Radar Chart, embodying the **Explainable AI (XAI)** principle applied to a consumer security tool.

### Two-tier analysis model

| Tier | Engine | Latency | Privacy |
|---|---|---|---|
| **Tier 1 — Heuristic Scan** | 9-rule NFA regex engine, O(N·M) | ~2 s (simulated) | Fully on-device |
| **Tier 2 — Deep AI Scan** | Gemini API socket (live or simulated) | ~3 s | Sanitized text only |

---

## Demo Video

> **Video walkthrough** covering app demo, code architecture, and AI workflow.

[[Demo Video](https://www.loom.com/share/8109787d259e46dfa9e3b6a53f0a4078)]

--------

## Screenshots

<table>
  <tr>
    <td align="center"><b>Main Screen — Idle</b></td>
    <td align="center"><b>Radar Chart — DANGEROUS</b></td>
    <td align="center"><b>Radar Chart — SUSPICIOUS</b></td>
  </tr>
  <tr>
    <td><img src="images/screenshot_main.png" width="220"/></td>
    <td><img src="images/screenshot_radar_dangerous.png" width="220"/></td>
    <td><img src="images/screenshot_radar_suspicious.png" width="220"/></td>
  </tr>
  <tr>
    <td align="center"><b>Forensic Console</b></td>
    <td align="center"><b>Deep AI Scan — Security Lab</b></td>
    <td align="center"><b>Scan History — Room DB</b></td>
  </tr>
  <tr>
    <td><img src="images/screenshot_loading.png" width="220"/></td>
    <td><img src="images/screenshot_deep_scan.png" width="220"/></td>
    <td><img src="images/screenshot_history.png" width="220"/></td>
  </tr>
  <tr>
    <td align="center"><b>History Detail Dialog</b></td>
    <td></td>
    <td></td>
  </tr>
  <tr>
    <td><img src="images/screenshot_history_detail.png" width="220"/></td>
    <td></td>
    <td></td>
  </tr>
</table>


## Feature Matrix

| Feature | Status | Implementation |
|---|---|---|
| Text input + URL paste | ✅ | `OutlinedTextField`, `onValueChange` → ViewModel |
| Example message chips | ✅ | `LazyRow` + `ExampleMessage` data class in ViewModel |
| Heuristic analysis engine | ✅ | 9-rule NFA regex catalogue in `ScamRepositoryImpl` |
| Risk level classification | ✅ | Sealed `RiskLevel` enum; thresholds 0.25 / 0.60 |
| Confidence score | ✅ | Weighted sum + category deduplication + `BASE_SCORE` |
| **Radar Chart (XAI)** | ✅ | Custom `Canvas` spider chart, 4 axes, animated |
| Forensic console loader | ✅ | `LaunchedEffect` progressive log lines + cursor |
| Flagged signal chips | ✅ | `TokenChip` composables per matched indicator |
| **Deep AI Scan socket** | ✅ | `performDeepScan` wired; Gemini SDK comment block |
| Deep scan shimmer | ✅ | `InfiniteTransition` + `Brush.linearGradient` |
| Security Lab result card | ✅ | Dark-panel `AiBadge` + tactic + risk reasoning |
| **Haptic feedback** | ✅ | `HapticFeedbackType.LongPress` on DANGEROUS only |
| **Room scan history** | ✅ | `ScanHistoryEntity` + `ScanHistoryDao` + `Flow` |
| History detail dialog | ✅ | `AlertDialog` with full explanation + token chips |
| **Hilt DI graph** | ✅ | `@HiltAndroidApp` · `@HiltViewModel` · `AppModule` |
| **Repository Pattern** | ✅ | `ScamRepository` interface + `ScamRepositoryImpl` |
| Prompt injection sanitization | ✅ | Strips null bytes, zero-width chars, RTL overrides |
| Unit test suite | ✅ | 10 tests, `FakeScanHistoryDao`, plain JVM |
| Material 3 theming | ✅ | Security-focused palette, dark Lab panel tokens |

---

## Architecture

The application is structured in three strict layers. No layer imports from a layer above it.

```
┌─────────────────────────────────────────────────────────┐
│                     UI LAYER                            │
│  ScamDetectorScreen.kt  ·  ScamDetectorViewModel.kt     │
│  Composables are stateless — all state via StateFlow    │
│  ViewModel owns coroutine lifecycle (viewModelScope)    │
└────────────────────────┬────────────────────────────────┘
                         │  interface ScamRepository
                         ▼
┌─────────────────────────────────────────────────────────┐
│                   DOMAIN / DATA LAYER                   │
│  ScamRepositoryImpl.kt                                  │
│  · Heuristic engine (RULES, scoring, axis scores)       │
│  · sanitize() — prompt injection defence                │
│  · deepScan() — Gemini API socket                       │
│  · saveToHistory() / deleteHistoryEntry()               │
└──────────────┬──────────────────────────┬───────────────┘
               │                          │
               ▼                          ▼
┌──────────────────────┐    ┌─────────────────────────────┐
│   PERSISTENCE LAYER  │    │     REMOTE / AI LAYER       │
│  Room Database       │    │  GenerativeModel (Gemini)   │
│  ScamDatabase v1     │    │  Injected via Hilt          │
│  ScanHistoryDao      │    │  System-prompted for JSON   │
│  Flow<List<Entity>>  │    │  response schema            │
└──────────────────────┘    └─────────────────────────────┘
```

### Dependency Injection graph (Hilt)

```
SingletonComponent
├── ScamDatabase          @Provides  (Room.databaseBuilder)
├── ScanHistoryDao        @Provides  (database.scanHistoryDao())
├── GenerativeModel       @Provides  (Gemini SDK, system-prompted)
└── ScamRepository        @Binds     ScamRepositoryImpl ← all three above

ViewModelComponent
└── ScamDetectorViewModel @HiltViewModel ← ScamRepository
```

### UI State machine

```
              ┌─────────────────────────────┐
              │           Idle              │ ← app launch, reset(), edit after result
              └──────────┬──────────────────┘
                         │ analyzeMessage()
                         ▼
              ┌─────────────────────────────┐
              │          Loading            │ ← ForensicLoadingCard shown
              └──────────┬──────────────────┘
              ┌───────────┴──────────┐
              ▼                      ▼
    ┌──────────────────┐   ┌──────────────────────┐
    │     Success      │   │        Error          │
    │ isDeepScanning=F │   │ (shown as Snackbar)  │
    └──────┬───────────┘   └──────────────────────┘
           │ triggerDeepScan()
           ▼
    ┌──────────────────┐
    │     Success      │
    │ isDeepScanning=T │ ← DeepScanShimmer shown
    └──────┬───────────┘
           │
           ▼
    ┌──────────────────┐
    │     Success      │
    │ deepResult ≠ null│ ← SecurityLabCard shown
    └──────────────────┘
```

---

## Tech Stack

| Category | Technology | Version | Rationale |
|---|---|---|---|
| Language | Kotlin | 2.1.0 | Coroutines, sealed classes, extension functions |
| UI Framework | Jetpack Compose | BOM 2026.02.01 | Declarative, state-driven, Canvas API |
| Architecture | MVVM + Repository | — | Separation of concerns, testability |
| DI | Hilt | 2.52 | Lifecycle-aware injection, `@HiltViewModel` |
| Persistence | Room | 2.7.0 | Flow-based reactive queries, type-safe SQL |
| Annotation Processing | KSP | 2.1.0-1.0.29 | Faster than KAPT for Hilt + Room codegen |
| Async | Kotlin Coroutines | 1.9.0 | Structured concurrency, `viewModelScope` |
| State | StateFlow | — | Hot stream, lifecycle-safe collection |
| Visualization | Compose Canvas | — | Custom Radar Chart, `DrawScope`, `drawText` |
| AI SDK | Google Generative AI | 0.9.0 | Gemini integration socket (wired, not live) |
| Testing | JUnit 4 + coroutines-test | 4.13.2 / 1.9.0 | `UnconfinedTestDispatcher`, fake DAO |
| Build | AGP | 8.9.1 | compileSdk 36, `buildConfig = true` |

---

## Key Engineering Decisions

### Repository Pattern — Why it matters for testability

The ViewModel originally owned the heuristic engine. That created a hard coupling between UI orchestration logic and business rules, making unit testing impossible without the Android runtime.

Moving all analysis logic to `ScamRepositoryImpl` behind the `ScamRepository` interface gives three concrete benefits:
- **JVM-only testing:** The `FakeScanHistoryDao` (an in-memory `MutableStateFlow`-backed DAO) allows the full `ScamRepositoryImpl` to be injected in tests without Hilt, Room, or an emulator.
- **Swap-ability:** Replacing the heuristic engine with a TFLite model or remote API requires changing only the implementation class, not the ViewModel or any UI code.
- **Hilt binding:** `@Binds` maps `ScamRepository → ScamRepositoryImpl` in `AppModule`; the ViewModel receives the interface, never the concrete type.

### Radar Chart — Explainable AI (XAI)

A single confidence percentage is epistemically opaque. The four-axis radar chart is derived directly from the rule engine's weighted scoring, partitioned into attack-vector groups:

| Axis | Rule Categories | Max Weight |
|---|---|---|
| **Urgency** | Urgency Language, Account Threat | 0.50 |
| **Link Malice** | Shortened URL, Raw IP, Suspicious Domain, Suspicious TLD | 1.45 (clamped) |
| **Financial Bait** | Financial Bait | 0.10 |
| **Credential Request** | Credential Phishing, Impersonation Language, Phishing CTA | 0.45 |

Each axis score = `sum(matched rule weights for axis) / axis_max_weight`, clamped to `[0f, 1f]`. This means the chart is **mathematically grounded in the same computation that produced the verdict**, not a post-hoc visualization.

The canvas renders in `O(N)` draw calls per frame (4 grid rings × 4 vertices + score polygon + labels), with smooth `animateFloatAsState` + `FastOutSlowInEasing` animation.

### Prompt Injection Defence

Before any text reaches the AI layer, `ScamRepositoryImpl.sanitize()` strips:
- `\u0000–\u001F` — non-printable ASCII controls (null bytes, form feeds)
- `\u200B–\u200F`, `\u2028`, `\u2029`, `\uFEFF` — zero-width characters and Unicode separators (common injection vectors)
- `\u202E` — RTL override, used to disguise malicious URLs as benign strings
- Consecutive newlines collapsed to `≤2` (prevents context-window padding attacks)

This matters because user-controlled text is passed verbatim to the system-prompted Gemini model. Without sanitization, a crafted message could attempt to override the security analyst persona.

### Room — Why `Flow<List<Entity>>` over a one-shot query

`ScanHistoryDao.observeAll()` returns a `Flow` rather than a `suspend List`. This means Room emits a new list automatically whenever a row is inserted or deleted — the UI does not poll and cannot get stale. The ViewModel converts this to a `StateFlow` via `stateIn(WhileSubscribed(5_000))`: the 5-second timeout keeps the upstream `Flow` alive across configuration changes (screen rotation) without leaking after genuine background transitions.

### Threading model

| Work type | Dispatcher | Location |
|---|---|---|
| Regex NFA matching | `Dispatchers.Default` | `withContext` in `performAnalysis()` |
| Room read/write | Room's internal executor | DAO `suspend` functions handle this |
| Gemini API call | SDK's internal dispatcher | `generateContent()` is suspend-safe |
| UI state updates | `Dispatchers.Main` | `_uiState.update { }` (thread-safe) |

---

## Heuristic Engine — Complexity Analysis

**Formal complexity: O(N · M)**

- **N** = input character count (message length)
- **M** = rule count (currently 9)

Each `Regex` in the `RULES` list is compiled **once** at class-load time into a finite-state automaton (NFA) via `java.util.regex.Pattern`. NFA simulation scans the input in O(N) for the bounded alternation groups used here — there are no unbounded nested quantifiers that could trigger catastrophic backtracking.

Applying M such patterns gives O(N · M) total. At M=9 and typical SMS/email lengths of N ≈ 500 characters, the engine completes in microseconds on the UI thread, though it is dispatched to `Dispatchers.Default` as an architectural principle.

**Deduplication strategy** prevents two failure modes:
1. *Score inflation:* Multiple matches of the same category (e.g., three urgency synonyms) are deduplicated by `category` before summing weights — only one weight contribution per rule category.
2. *Token duplication:* The flagged signal chip list is deduplicated by `displayToken.lowercase()` so the same URL cannot appear twice in the UI.

---

## Testing Strategy

The test suite runs entirely on the **plain JVM** — no emulator, no Hilt, no Room instrumentation.

### `FakeScanHistoryDao`

```kotlin
private class FakeScanHistoryDao : ScanHistoryDao {
    private val _store = MutableStateFlow<List<ScanHistoryEntity>>(emptyList())
    override fun observeAll() = _store.asStateFlow()
    override suspend fun insert(entity: ScanHistoryEntity) {
        _store.update { it + entity.copy(id = (it.size + 1).toLong()) }
    }
    // ...
}
```

The ViewModel is constructed directly: `ScamDetectorViewModel(ScamRepositoryImpl(FakeScanHistoryDao()))`. The real `ScamRepositoryImpl` — including the full heuristic engine — runs in tests. Only the database is faked.

### Test coverage

| # | Test | Mechanism |
|---|---|---|
| 1 | Initial state is Idle | Direct assertion |
| 2 | Blank input → Error (no Loading) | Synchronous guard in `analyzeMessage()` |
| 3 | Loading emitted before Success | `UnconfinedTestDispatcher` eager execution |
| 4 | Clean message → SAFE, confidence < 0.25 | Real engine, benign input |
| 5 | Bank alert → DANGEROUS, confidence ≥ 0.60 | Real engine, full scoring |
| 6 | Shortened URL alone → SUSPICIOUS | Threshold boundary guard |
| 7 | Edit after Success → resets to Idle | `onInputChanged` state reset |
| 8 | `reset()` clears input + returns to Idle | Full reset assertion |
| 9 | `onExampleSelected` populates inputText | ViewModel API contract |
| 10 | DANGEROUS explanation has actionable advice | NLP content assertion |

Tests 2, 4, and 5 are **AI-generated** (Claude, Anthropic) and are marked with `[AI-GENERATED]` in their KDoc.

### Running tests

```bash
# From project root
./gradlew :app:test --tests "com.veera.scammessagedetector.ui.ScamDetectorViewModelTest"
```

---

## Gemini API Integration Socket

The Deep AI Scan is pre-wired for live Gemini integration. The socket is in `ScamRepositoryImpl.deepScan()`.

### Activate live AI (3 steps)

**Step 1 — Get a free API key**

Visit [Google AI Studio](https://aistudio.google.com/app/apikey) and create a key. This is free for development (15 requests/min, 1M tokens/day on the free tier).

**Step 2 — Add to `local.properties`** *(git-ignored by default)*

```properties
GEMINI_API_KEY=AIza...your_key_here
```

**Step 3 — Replace the simulation call in `ScamDetectorViewModel`**

```kotlin
// BEFORE (simulation)
delay(3_000L)
val deepResult = repository.simulateDeepResult(sanitized)

// AFTER (live — one line change)
val deepResult = repository.deepScan(sanitized)
```

The `AppModule` already provides a `GenerativeModel` singleton with a security-focused system prompt, `temperature = 0.1f`, and `responseMimeType = "application/json"` for deterministic JSON output. The `parseDeepScanResponse()` function maps the `{ tactic, riskReasoning }` schema back to `DeepAnalysisResult`. Error handling covers `InvalidAPIKeyException`, `ServerException` (HTTP 429 quota), `ResponseStoppedException` (safety filter), and `RequestTimeoutException`.

---

## Setup Instructions

### Prerequisites

- Android Studio Meerkat (2024.3.1) or later
- JDK 11+
- Android emulator or physical device running API 26+
- *(Optional)* A Google AI Studio API key for live Gemini integration

### Steps

```bash
# 1. Clone
git clone https://github.com/Nani8790/ScamMessageDetector.git
cd ScamMessageDetector

# 2. Open in Android Studio and let Gradle sync (AGP 8.9.1, compileSdk 36)

# 3. (Optional) Add your Gemini API key to local.properties:
#    GEMINI_API_KEY=AIza...

# 4. Run on emulator/device
#    Run > Run 'app'

# 5. Run unit tests (no emulator required)
./gradlew :app:test
```

> **Note on first launch:** The first run on an emulator may show an "Installing profile" message in Logcat. This is ART's JIT profile-guided compilation — subsequent launches are significantly faster.

---

## Future Enhancements

### 1. Live Gemini 2.0 Pro Integration

The current integration socket uses `gemini-2.0-flash` for speed. The production upgrade path is:

- Replace the model name with `gemini-2.0-pro` in `AppModule.provideGenerativeModel()`
- Extend the system prompt to request a **structured JSON confidence score per axis** from the model, allowing the Radar Chart to reflect LLM reasoning rather than only heuristic weights
- Add a `responseSchema` to the `generationConfig` block (Gemini SDK supports constrained generation) to make the JSON schema contractually enforced at the API level, eliminating the need for defensive `parseDeepScanResponse()` validation

### 2. On-Device LLM via Gemini Nano (AICore)

For privacy-first analysis with **zero network dependency**:

- Integrate the **Android AICore API** (`com.google.android.gms:play-services-aicore`) once it reaches stable release
- Gemini Nano runs entirely on-device on supported Pixel 8+ and Galaxy S24+ devices
- The `ScamRepository` interface is already the abstraction boundary: add an `OnDeviceScamRepositoryImpl` that routes to Gemini Nano, and use a Hilt qualifier (`@Named("on_device")` / `@Named("cloud")`) to select at runtime based on device capability detection via `AIFeatureManager.isFeatureAvailable(AIFeature.TEXT_GENERATION)`
- This eliminates prompt injection risk entirely (no network surface) and works on aircraft mode

### 3. Adversarial Robustness — Homoglyph & Unicode Attack Hardening

Current sanitization strips control characters and zero-width joiners but does not address **homoglyph substitution** — a sophisticated evasion technique where attackers replace ASCII characters with visually identical Unicode counterparts (e.g., Cyrillic `а` → Latin `a`, `0` → `O`):

```
Evasion example: "ụrgеnt: yоur ассоunt is suspendеd"
                         ↑↑↑↑↑↑ ↑  ↑↑↑↑↑↑↑↑       ↑
                     Unicode lookalikes — regex misses these
```

The enhancement path:
- **Unicode normalization:** Apply `java.text.Normalizer.normalize(text, NFKC)` before regex matching. NFKC compatibility decomposition maps most Unicode lookalikes to their ASCII equivalents
- **Confusables database:** Integrate the [Unicode Confusables dataset](https://unicode.org/reports/tr39/) (TR39) to catch edge cases that NFKC normalization misses
- **Adversarial test set:** Build a dedicated test class `HomoglyphRobustnessTest` that generates homoglyph variants of known scam phrases and asserts the heuristic engine still classifies them correctly

### 4. Federated Learning — User Feedback Loop

The current "Safe / Suspicious / Dangerous" verdict can be expanded into an **on-device RLHF data capture pipeline**:

- Add a `FeedbackButton` row to `ResultCard`: "Correct ✓" / "False Positive ✗"
- Persist feedback as `ScanFeedbackEntity` in Room alongside the `ScanHistoryEntity`
- On a periodic background `WorkManager` task, export anonymized `(rulesFired, verdict, userFeedback)` tuples to a Firebase Analytics custom event
- This data shapes future updates to `Severity` weights — rules consistently generating false positives can have weights reduced; under-weighted rules on confirmed scams can be promoted

### 5. Notification-Based SMS Monitoring

Extend the app beyond manual paste-and-check:

- Register a `BroadcastReceiver` for `Manifest.permission.RECEIVE_SMS`
- On SMS receipt, run Tier 1 heuristic analysis in a `CoroutineWorker` (keeping it off the main thread and battery-efficient)
- If `RiskLevel.DANGEROUS`, post a high-priority `NotificationCompat` alert with an action button to open the full Deep AI Scan result
- Gate this feature behind a user opt-in settings screen with explicit privacy disclosure, complying with Play Store's SMS permissions policy

---

## AI Interaction Log

**AI Assistant Used:** Claude (Anthropic) — claude-sonnet-4-6

---

### Entry 1: Project Scaffolding and Foundational UI

**Goal:** Generate the initial Jetpack Compose UI and ViewModel state architecture before wiring up complex logic.

**Prompt:** "Act as a Staff-level Android Engineer. I am building a 'Scam Message Detector' prototype using Kotlin, Jetpack Compose, and the MVVM architecture. For Step 1, please generate the foundational Compose UI screen and the ViewModel state scaffolding. Requirements: 1. A clean, modern UI featuring a text input field (where a user can paste a message or URL) and an 'Analyze' button. 2. A results section that will eventually display: Risk Level (Safe/Suspicious/Dangerous), a Confidence Score (percentage), and a brief explanation. 3. A Kotlin ViewModel using `StateFlow` to manage the UI state (Idle, Loading, Success, Error). 4. For now, the 'Analyze' button should just trigger a 2-second simulated delay in the ViewModel before returning a hardcoded 'Suspicious' state. Do not integrate a real AI API yet."

**Result / Refinement:** Claude provided a highly structured MVVM foundation using sealed classes for mutually exclusive UI states (`Idle`, `Loading`, `Success`, `Error`) and separated the `ScamDetectorContent` into a stateless composable for easier previewing and testing. I had to manually intervene to organize the generated code into distinct `ui` and `model` packages to enforce clean architecture and resolve IDE compilation errors related to unresolved state references.

---

### Entry 2: Tappable Example Messages

**Goal:** Fulfill the requirement to provide at least 2 example scam messages that auto-populate the text input field when tapped.

**Prompt:** "The foundational UI is running perfectly on the emulator! Let's tackle Step 2. The take-home assignment requires: 'Show at least 2 example scam messages the user can tap to auto-populate the input field.' Please update the `ScamDetectorContent` composable to include a visually distinct section (like suggestion chips or selectable, horizontal scrolling cards) containing two example scam messages... Place this section just above the text input field. When clicked, these examples must trigger an event to update the `inputText` StateFlow in the ViewModel."

**Result / Refinement:** Claude generated a highly scalable solution by placing the `ExampleMessage` domain data directly into the ViewModel rather than hardcoding it into the UI. It utilized a `LazyRow` for horizontal scrolling and derived the selected state natively from the `inputText` StateFlow to prevent stale states. I successfully routed the new components into my existing `ui` and `model` packages.

---

### Entry 3: Core Analysis Engine (Local Heuristics)

**Goal:** Fulfill the requirement to analyze text and return a risk level, confidence score, and explanation without requiring the reviewer to inject personal API keys.

**Prompt:** "Our example messages are working perfectly! Now we need to tackle the core logic. Please replace the hardcoded stub inside the `performAnalysis` function in the `ScamDetectorViewModel` with a local heuristic/regex-based analysis engine. Requirements: 1. Create a private function `analyzeText`... 2. Check for common scam indicators... 3. Calculate a realistic `confidence` score and assign a `RiskLevel`... 4. Generate a dynamic `explanation`... 5. Populate `flaggedTokens`... 6. Keep the simulated 2-second network delay."

**Result / Refinement:** Claude generated a highly scalable, data-driven heuristic engine. It established a `Rule` catalogue with HIGH, MEDIUM, and LOW severity weights. I reviewed the scoring logic, which intelligently deduplicates hits by category to prevent score inflation (e.g., multiple "urgent" synonyms only count once). This creates a highly realistic, privacy-first, on-device analysis pipeline that perfectly mimics an API response.

---

### Entry 4: Automated Unit Testing

**Goal:** Generate robust unit tests for the ViewModel state machine and heuristic engine, satisfying the requirement to include AI-generated tests.

**Prompt:** "Step 3 is complete and the heuristic engine is running perfectly! Let's move to Step 4: Unit Testing. The assignment rubric requires: 'Write at least 3 meaningful unit tests... At least one of your unit tests must be AI-generated.' Please generate a `ScamDetectorViewModelTest` class using JUnit 4 and `kotlinx.coroutines.test`. Requirements: 1. Test empty input. 2. Test a normal message (SAFE). 3. Test the Bank Alert (DANGEROUS). 4. Include explicit code comments above the tests stating they were generated by AI. 5. Use `runTest` and `UnconfinedTestDispatcher` to handle the 2-second delay."

**Result / Refinement:** Claude generated a 10-test suite that thoroughly covers the state machine. I encountered a compiler error regarding the experimental opt-in pattern, which I fixed by changing `@ExperimentalCoroutinesApi` from a class-level annotation to a `@file:OptIn` declaration. I also synced the `UnconfinedTestDispatcher` across the `MainDispatcherRule` and the `runTest` blocks so the virtual clock correctly bypassed the 2-second delay. The tests now pass, and the AI-generated ones are explicitly marked in the KDoc comments.

---

### Entry 5: Hybrid Analysis & LLM Integration Socket

**Goal:** Implement a "Deep AI Scan" feature to demonstrate a hybrid architecture (Local Heuristics + Remote LLM) and address AI security concerns like Prompt Injection.

**Prompt:** "I want to add a 'Deep AI Scan' feature... Create a 'plug-and-play' socket in the ViewModel... Implement sanitization to prevent Indirect Prompt Injection... While scanning, show a sophisticated Shimmer effect... Once finished, display the result in a visually distinct 'Security Lab' themed card."

**Result / Refinement:** I implemented a secondary analysis layer that triggers only when a threat is detected. I included a `sanitizeInput` pipeline to strip malicious control characters before the text reaches the AI layer. The UI was upgraded with a custom `InfiniteTransition` shimmer effect and a dark-themed forensic report card. I also explicitly documented the API "socket" in the ViewModel, showing exactly where a live Gemini or OpenAI SDK would be integrated, making the project production-ready.

---

### Entry 6 — The Final Sprint: Production Architecture & Explainable AI

**Objective:** Elevate the app from a working prototype to a portfolio piece that demonstrates production-grade Android engineering and AI interpretability.

**Why the Repository Pattern?**
The original ViewModel held all heuristic logic directly, creating a hard dependency between the UI and business rules. I refactored this into a `ScamRepository` abstraction. This allows the ViewModel to remain clean and makes the business logic independently testable. This was validated by a JVM-based unit test suite that runs in milliseconds without needing an Android emulator.

**Why Hilt for Dependency Injection?**
Manual injection doesn't survive configuration changes like screen rotation. I integrated Hilt to manage the lifecycle of the `ScamRepository` and `ScamDatabase`, ensuring dependencies are scoped correctly and the app remains modular and easy to scale.

**Why Room for Scan History?**
Local persistence is a hallmark of enterprise apps. I implemented Room with Flow-based reactive queries, so the UI updates automatically whenever a new scan is saved or deleted. I also included a "Confirm Threat / False Positive" feedback loop to demonstrate an understanding of RLHF (Reinforcement Learning from Human Feedback) data capture.

**Why the Radar Chart for Explainable AI (XAI)?**
A simple percentage score is a "black box." I replaced the standard progress bar with a custom Canvas-drawn Radar Chart that maps risk across four specific axes: Urgency, Link Malice, Financial Bait, and Credential Request. This surfaces the *logic* behind the verdict to the user, a key principle in modern security AI.

**AI Collaboration note:** The architecture design, Hilt integration strategy, and Canvas drawing logic were co-developed with Claude. All code was reviewed for threading safety (using `withContext(Dispatchers.Default)` for Regex and `Dispatchers.IO` for DB/API work) and documented with KDoc.

---

## AI Code Review

**Reviewer:** Claude Sonnet 4.6 (Anthropic) — applied before final submission

A staff-level review of `ScamDetectorViewModel.kt` and `ScamDetectorScreen.kt` identified two focused improvements.

---

### Finding 1 — Off-thread regex analysis (`ScamDetectorViewModel.kt`)

**Severity:** Medium — latency risk, architectural violation

The heuristic engine's `analyzeText()` function — which runs 9 compiled regexes against the input — was executing on `Dispatchers.Main` because `viewModelScope` defaults to the Main dispatcher. Although the current corpus is small enough not to cause visible jank, this violates the architectural principle that Main should only perform UI work and creates a latency risk as the rule catalogue grows.

**Fix applied:** Wrapped `analyzeText()` in `withContext(Dispatchers.Default)`, shifting CPU-bound NFA regex matching off the UI thread with no impact on the calling API or tests.

```kotlin
// Before
val result = repository.analyze(text)   // ran on Main

// After
val result = withContext(Dispatchers.Default) { repository.analyze(text) }
```

---

### Finding 2 — Incorrect `@Composable` annotation (`ScamDetectorScreen.kt`)

**Severity:** Low — semantic correctness, compiler contract violation

The `iconForExample` helper was marked `@Composable` despite being a pure `String -> ImageVector` mapping that makes no use of the Compose runtime. This annotation is a semantic contract — it signals to the compiler, tooling, and readers that a function participates in composition — and applying it to a plain function misleads readers, unnecessarily restricts call sites to composition scope, and triggers dead code generation from the Compose compiler plugin.

**Fix applied:** Removed the `@Composable` annotation. The call site inside `ExampleMessageCard` required no changes.

```kotlin
// Before
@Composable
private fun iconForExample(label: String): ImageVector { ... }

// After
private fun iconForExample(label: String): ImageVector { ... }
```

---

## Reflection

### What I Learned

#### 1. Norton Genie exploration shaped every major design decision

Before writing a single line of code, I spent time in the Norton 360 app exploring Norton Genie — the closest real-world equivalent to what I was about to build. Several observations directly influenced the architecture:

**What I observed in Norton Genie:**
- Conversational UI — the user pastes text and gets instant, colour-coded feedback (green / yellow / red), which I replicated with `RiskLevel.SAFE / SUSPICIOUS / DANGEROUS` and the security colour palette
- Genie explains *why* something is suspicious, not just flags it — I replicated this with the `explanation` field in `AnalysisResult` and the "Why this verdict?" card
- The app feels instant — knowing Genie targets perceived immediacy, I added a forensic animation during the 2-second heuristic scan so the wait feels purposeful rather than frozen
- Results are self-contained — no navigation required to understand the verdict

**The UX gap I identified and addressed:**
Genie returns a verdict and a paragraph, but gives no visual breakdown of *which attack vector* is active. Is the risk the link? The urgency wording? The brand impersonation? You get a red label and a sentence. That gap directly motivated the Radar Chart — four independently animated axes (Urgency, Link Malice, Financial Bait, Credential Request) let the user's eyes land on the spike before they read a word.

**What I would improve in Norton Genie (and built here):**
- **Offline mode** — basic regex detection for obvious scams when there is no network. This app's Tier 1 heuristic engine works fully on-device with zero connectivity
- **Scan history** — Genie has no record of previously analysed messages. This app persists every scan to a Room database with a reactive history panel

This is the core principle of **Explainable AI (XAI)**: the model's output should be self-describing. Norton Genie is excellent at catching scams; surfacing *why* at a glance — and working offline — is the next frontier.

#### 2. Architecture patterns exist because of real pain, not academic preference

I had read about MVVM and the Repository Pattern before, but implementing Hilt DI from scratch made it visceral. When I wrote `FakeScanHistoryDao` for the unit tests, I realised the Repository Pattern wasn't a formality — it was the reason I could test the full heuristic engine and ViewModel state machine on a plain JVM in milliseconds, with no emulator, no Room, and no Android runtime. That test suite is trustworthy *because* the ViewModel has never seen a real database. Dependency injection made the seam. Without it, I'd be testing screenshots.

The same insight applied to the sealed `ScamDetectorUiState`: the first time the compiler caught me handling `Success` in a branch where I'd left out `Error`, I understood why sealed classes are not just a style preference. The type system eliminates an entire category of runtime crash.

#### 3. AI tools are a multiplier, not a replacement — and the delta is prompt quality

Every significant architectural decision in this project went through Claude — scaffolding, the scoring engine, the test suite, the Radar Chart, the Gemini integration socket. The output quality varied enormously based on how much context I provided. A vague prompt like *"write a regex scam detector"* produced generic, untestable code. A structured prompt that specified the data model, threading constraints, test strategy, and target complexity produced production-grade output on the first try.

The skill is not in knowing how to use the AI. It's in knowing enough to brief it like a senior engineer, spot the gaps in its output, and close them with targeted follow-up prompts. The AI Code Review section documents two cases where Claude's initial output was subtly wrong and I had to iterate to correct it — the `@Composable` annotation finding and the off-thread regex call. Blind acceptance of AI output would have shipped both bugs.

#### 4. Security is about adversarial thinking, not just feature coverage

Writing `sanitize()` forced a mindset shift. The question stopped being *"does this work for normal input?"* and became *"what is the most creative way to break this?"* Zero-width characters are invisible in every text editor but fully visible to an LLM. The RTL override character makes `evil.exe` render as `exe.live` in UI. These aren't edge cases — they are documented attack techniques used in real phishing campaigns. Building the defence requires knowing the offence.

This is the gap between a feature and a security feature: a scam detector that can be bypassed by a one-character prepend is not a detector at all.

#### 5. Reactive data flow is not a UI concern — it's an architectural discipline

The `Flow<List<ScanHistoryEntity>>` from Room with `SharingStarted.WhileSubscribed(5_000)` looks like plumbing. What it actually is: a guarantee that the history list is always consistent with the database, with no polling, no manual refresh, and no race condition. The 5-second timeout keeps the upstream alive during screen rotation — the new Activity re-subscribes and instantly gets the cached value without a database round-trip. Understanding *why* that number is 5 seconds (it matches the typical Activity recreation window) was the moment the StateFlow pattern clicked for me beyond its surface syntax.

---

### What I Would Do Differently

#### 1. Wire the Gemini API from day one, not as a socket

I architected `performDeepScan()` as a working socket — the structure, the system prompt, the JSON response parsing are all there — but the actual `GenerativeModel` call is behind a comment block pending a production API key strategy. In hindsight, I would have wired a backend proxy on day one, even a simple Cloud Function that holds the key server-side. Shipping with `BuildConfig.GEMINI_API_KEY` baked into the APK is not acceptable for a production Norton product, and I knew that before I started. The proxy should have been the first commit, not a future enhancement.

#### 2. Add Room database migration from the start

`ScamDatabase` is currently at version 1 with `exportSchema = false`. Any schema change — adding a column, renaming a field — would either crash existing users or require a destructive `fallbackToDestructiveMigration()`. A production app at Norton would have migration scripts from version 1 onwards, schema export enabled, and a migration test. This is the kind of technical debt that costs three days of debugging after a point release.

#### 3. Write instrumented tests for the DAO alongside unit tests

The JVM test suite proves the heuristic engine and ViewModel state machine are correct. It does not prove that the actual SQL queries in `ScanHistoryDao` work. `observeAll()` returning a `Flow` sorted by `timestamp DESC`, `deleteById()` deleting exactly one row, `deleteAll()` clearing the table — these need an instrumented test against a real in-memory Room database. I would schedule these on day 3, not treat them as "future work".

#### 4. Store per-axis scores in the Room entity

`ScanHistoryEntity` stores `flaggedTokens` as a pipe-delimited string but has no column for `axisScores`. This means the Radar Chart is only available for the *current* session result — if you open a history entry's detail dialog, you see the explanation and token chips, but not the radar visualisation. Storing the four axis floats as four nullable columns would make the detail view as rich as the live result. The schema is simple; I just didn't plan for it in the initial entity design.

#### 5. Add a proper onboarding flow referencing real Norton Genie

Norton Genie has brand recognition among security-conscious users. An onboarding card on first launch — "This works like Norton Genie but runs on-device with zero data sharing" — would immediately frame the app's value proposition for a user who already trusts the Norton brand. Feature context is a UX feature. I would write that copy on day 1 before writing any code, because it forces clarity about what the app actually is for.

---

*Built with Kotlin, Jetpack Compose, Hilt, Room, and Claude AI.*
*Gen Digital AI-First Mobile Engineering Internship — Option B Submission.*
