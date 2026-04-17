# Gen Digital Intern Assignment: Scam Message Detector 🛡️

**Candidate:** Veera Venkata Satyanarayana Nargana
[cite_start]**Option Selected:** Option B (Scam Message Detector Prototype) [cite: 102]
**Tech Stack:** Kotlin, Jetpack Compose, MVVM Architecture

## Project Overview
[cite_start]This project is an AI-driven scam message detector built for the Gen Digital AI-First Mobile Engineering Intern take-home assignment[cite: 54, 103]. [cite_start]It allows users to paste suspicious messages or URLs and receive a simulated AI risk assessment (Safe / Suspicious / Dangerous) along with a confidence score[cite: 109].

## Setup Instructions
1. Clone this repository.
2. Open the project in Android Studio.
3. Sync Gradle dependencies (ensuring `lifecycle-viewmodel-compose` and `lifecycle-runtime-compose` are included).
4. Run the app on an emulator or physical device running API 26+.

---

## [cite_start]AI Interaction Log [cite: 116]

[cite_start]**AI Assistant Used:** Claude [cite: 115]

### Entry 1: Project Scaffolding and Foundational UI
* **Goal:** Generate the initial Jetpack Compose UI and ViewModel state architecture before wiring up complex logic.
* **Prompt:** "Act as a Staff-level Android Engineer. I am building a 'Scam Message Detector' prototype using Kotlin, Jetpack Compose, and the MVVM architecture. For Step 1, please generate the foundational Compose UI screen and the ViewModel state scaffolding. Requirements: 1. A clean, modern UI featuring a text input field (where a user can paste a message or URL) and an 'Analyze' button. 2. A results section that will eventually display: Risk Level (Safe/Suspicious/Dangerous), a Confidence Score (percentage), and a brief explanation. 3. A Kotlin ViewModel using `StateFlow` to manage the UI state (Idle, Loading, Success, Error). 4. For now, the 'Analyze' button should just trigger a 2-second simulated delay in the ViewModel before returning a hardcoded 'Suspicious' state. Do not integrate a real AI API yet."
* **Result / Refinement:** Claude provided a highly structured MVVM foundation using sealed classes for mutually exclusive UI states (`Idle`, `Loading`, `Success`, `Error`) and separated the `ScamDetectorContent` into a stateless composable for easier previewing and testing. I had to manually intervene to organize the generated code into distinct `ui` and `model` packages to enforce clean architecture and resolve IDE compilation errors related to unresolved state references.

### Entry 2: Tappable Example Messages
* **Goal:** Fulfill the requirement to provide at least 2 example scam messages that auto-populate the text input field when tapped.
* **Prompt:** "The foundational UI is running perfectly on the emulator! Let's tackle Step 2. The take-home assignment requires: 'Show at least 2 example scam messages the user can tap to auto-populate the input field.' Please update the `ScamDetectorContent` composable to include a visually distinct section (like suggestion chips or selectable, horizontal scrolling cards) containing two example scam messages... Place this section just above the text input field. When clicked, these examples must trigger an event to update the `inputText` StateFlow in the ViewModel."
* **Result / Refinement:** Claude generated a highly scalable solution by placing the `ExampleMessage` domain data directly into the ViewModel rather than hardcoding it into the UI. It utilized a `LazyRow` for horizontal scrolling and derived the selected state natively from the `inputText` StateFlow to prevent stale states. I successfully routed the new components into my existing `ui` and `model` packages.

### Entry 3: [Pending]
### Entry 4: [Pending]
### [cite_start]Entry 5: AI Code Review [Pending] [cite: 120]