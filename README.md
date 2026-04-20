# Gen Digital Intern Assignment: Scam Message Detector 🛡️

**Candidate:** Veera Venkata Satyanarayana Nargana  
**Option Selected:** Option B (Scam Message Detector Prototype)  
**Tech Stack:** Kotlin, Jetpack Compose, MVVM Architecture, Coroutines, StateFlow

## Project Overview
This project is an AI-driven scam message detector built for the Gen Digital AI-First Mobile Engineering Intern take-home assignment. It allows users to paste suspicious messages or URLs and receive a simulated AI risk assessment (Safe / Suspicious / Dangerous) along with a confidence score.

### Screenshots
*(Add your screenshots here by dragging and dropping them into the GitHub web editor, or using markdown: `![Home Screen](path/to/image.png)`)*

## Setup Instructions
1. Clone this repository.
2. Open the project in Android Studio.
3. Sync Gradle dependencies (ensuring `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, and `kotlinx-coroutines-test` are included).
4. Run the app on an emulator or physical device running API 26+.
5. To run the test suite, execute the `ScamDetectorViewModelTest` file located in the `(test)` directory.

---

## Architecture & Future Enhancements
This prototype was built using clean architecture principles to ensure scalability:
* **Unidirectional Data Flow (UDF):** UI state is modeled using sealed classes to prevent impossible states.
* **State Hoisting:** Composables are stateless where possible, making them highly testable and preview-friendly.
* **Thread Safety:** CPU-bound regex evaluations are shifted off the Main thread using `Dispatchers.Default`.

**Future Enhancements for Production:**
1.  **True LLM Integration:** Replace the local heuristic engine with a remote API call to a GenAI endpoint (e.g., OpenAI or a custom enterprise model) to catch zero-day phishing patterns.
2.  **Dynamic Rule Updates:** Host the heuristic `Rule` catalogue remotely so new scam patterns can be blocked instantly without requiring a Google Play Store app update.
3.  **On-Device ML:** Integrate a lightweight TensorFlow Lite model for privacy-first, offline NLP analysis.

---

## AI Interaction Log

**AI Assistant Used:** Claude

### Entry 1: Project Scaffolding and Foundational UI
* **Goal:** Generate the initial Jetpack Compose UI and ViewModel state architecture before wiring up complex logic.
* **Prompt:** "Act as a Staff-level Android Engineer. I am building a 'Scam Message Detector' prototype using Kotlin, Jetpack Compose, and the MVVM architecture. For Step 1, please generate the foundational Compose UI screen and the ViewModel state scaffolding. Requirements: 1. A clean, modern UI featuring a text input field (where a user can paste a message or URL) and an 'Analyze' button. 2. A results section that will eventually display: Risk Level (Safe/Suspicious/Dangerous), a Confidence Score (percentage), and a brief explanation. 3. A Kotlin ViewModel using `StateFlow` to manage the UI state (Idle, Loading, Success, Error). 4. For now, the 'Analyze' button should just trigger a 2-second simulated delay in the ViewModel before returning a hardcoded 'Suspicious' state. Do not integrate a real AI API yet."
* **Result / Refinement:** Claude provided a highly structured MVVM foundation using sealed classes for mutually exclusive UI states (`Idle`, `Loading`, `Success`, `Error`) and separated the `ScamDetectorContent` into a stateless composable for easier previewing and testing. I had to manually intervene to organize the generated code into distinct `ui` and `model` packages to enforce clean architecture and resolve IDE compilation errors related to unresolved state references.

### Entry 2: Tappable Example Messages
* **Goal:** Fulfill the requirement to provide at least 2 example scam messages that auto-populate the text input field when tapped.
* **Prompt:** "The foundational UI is running perfectly on the emulator! Let's tackle Step 2. The take-home assignment requires: 'Show at least 2 example scam messages the user can tap to auto-populate the input field.' Please update the `ScamDetectorContent` composable to include a visually distinct section (like suggestion chips or selectable, horizontal scrolling cards) containing two example scam messages... Place this section just above the text input field. When clicked, these examples must trigger an event to update the `inputText` StateFlow in the ViewModel."
* **Result / Refinement:** Claude generated a highly scalable solution by placing the `ExampleMessage` domain data directly into the ViewModel rather than hardcoding it into the UI. It utilized a `LazyRow` for horizontal scrolling and derived the selected state natively from the `inputText` StateFlow to prevent stale states. I successfully routed the new components into my existing `ui` and `model` packages.

### Entry 3: Core Analysis Engine (Local Heuristics)
* **Goal:** Fulfill the requirement to analyze text and return a risk level, confidence score, and explanation without requiring the reviewer to inject personal API keys.
* **Prompt:** "Our example messages are working perfectly! Now we need to tackle the core logic. Please replace the hardcoded stub inside the `performAnalysis` function in the `ScamDetectorViewModel` with a local heuristic/regex-based analysis engine. Requirements: 1. Create a private function `analyzeText`... 2. Check for common scam indicators... 3. Calculate a realistic `confidence` score and assign a `RiskLevel`... 4. Generate a dynamic `explanation`... 5. Populate `flaggedTokens`... 6. Keep the simulated 2-second network delay."
* **Result / Refinement:** Claude generated a highly scalable, data-driven heuristic engine. It established a `Rule` catalogue with HIGH, MEDIUM, and LOW severity weights. I reviewed the scoring logic, which intelligently deduplicates hits by category to prevent score inflation (e.g., multiple "urgent" synonyms only count once). This creates a highly realistic, privacy-first, on-device analysis pipeline that perfectly mimics an API response.

### Entry 4: Automated Unit Testing
* **Goal:** Generate robust unit tests for the ViewModel state machine and heuristic engine, satisfying the requirement to include AI-generated tests.
* **Prompt:** "Step 3 is complete and the heuristic engine is running perfectly! Let's move to Step 4: Unit Testing. The assignment rubric requires: 'Write at least 3 meaningful unit tests... At least one of your unit tests must be AI-generated.' Please generate a `ScamDetectorViewModelTest` class using JUnit 4 and `kotlinx.coroutines.test`. Requirements: 1. Test empty input. 2. Test a normal message (SAFE). 3. Test the Bank Alert (DANGEROUS). 4. Include explicit code comments above the tests stating they were generated by AI. 5. Use `runTest` and `UnconfinedTestDispatcher` to handle the 2-second delay."
* **Result / Refinement:** Claude generated a 10-test suite that thoroughly covers the state machine. I encountered a compiler error regarding the experimental opt-in pattern, which I fixed by changing `@ExperimentalCoroutinesApi` from a class-level annotation to a `@file:OptIn` declaration. I also synced the `UnconfinedTestDispatcher` across the `MainDispatcherRule` and the `runTest` blocks so the virtual clock correctly bypassed the 2-second delay. The tests now pass, and the AI-generated ones are explicitly marked in the KDoc comments.

### Entry 5: Hybrid Analysis & LLM Integration Socket
* **Goal:** Implement a "Deep AI Scan" feature to demonstrate a hybrid architecture (Local Heuristics + Remote LLM) and address AI security concerns like Prompt Injection.
* **Prompt:** "I want to add a 'Deep AI Scan' feature... Create a 'plug-and-play' socket in the ViewModel... Implement sanitization to prevent Indirect Prompt Injection... While scanning, show a sophisticated Shimmer effect... Once finished, display the result in a visually distinct 'Security Lab' themed card."
* **Result / Refinement:** I implemented a secondary analysis layer that triggers only when a threat is detected. I included a `sanitizeInput` pipeline to strip malicious control characters before the text reaches the AI layer. The UI was upgraded with a custom `InfiniteTransition` shimmer effect and a dark-themed forensic report card. I also explicitly documented the API "socket" in the ViewModel, showing exactly where a live Gemini or OpenAI SDK would be integrated, making the project production-ready.

### AI Code Review (Claude, Anthropic)
A Staff-level review of `ScamDetectorViewModel.kt` and `ScamDetectorScreen.kt` identified two focused improvements applied before final submission.

1. **Off-thread regex analysis (ScamDetectorViewModel.kt).** The heuristic engine's `analyzeText()` function — which runs 8 compiled regexes against the input — was executing on `Dispatchers.Main` because `viewModelScope` defaults to the Main dispatcher. Although the current corpus is small enough not to cause visible jank, this violates the architectural principle that Main should only perform UI work and creates a latency risk as the rule catalogue grows. Fixed by wrapping `analyzeText()` in `withContext(Dispatchers.Default)`, shifting CPU-bound work off the UI thread with no impact on the calling API or tests.
2. **Incorrect @Composable annotation (ScamDetectorScreen.kt).** The `iconForExample` helper was marked `@Composable` despite being a pure `String -> ImageVector` mapping that makes no use of the Compose runtime. This annotation is a semantic contract — it signals to the compiler, tooling, and readers that a function participates in composition — and applying it to a plain function misleads readers, unnecessarily restricts call sites to composition scope, and triggers dead code generation from the Compose compiler plugin. Fixed by removing the annotation; the call site inside `ExampleMessageCard` required no changes.