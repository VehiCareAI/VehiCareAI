# VehiCare AI

**Intelligent Vehicle Health Assessment — Symptom-Based Diagnosis and Probabilistic Issue Ranking**

> *Describe the symptoms. Understand the possibilities. Make informed decisions about your vehicle's health.*

VehiCare AI is a **local-first Android vehicle health assessment assistant** for vehicle owners and
mechanics. A user describes what the vehicle is doing through a guided, adaptive questionnaire and
receives **Bayesian-inferred, probability-ranked possible issues** with plain-language explanations,
supporting and missing evidence, recommended checks, and a **separate safety layer** that never
depends on the ranking.

It is a **diagnostic aid, not a replacement for professional inspection**. Every result is a
preliminary probability estimate, never a confirmed diagnosis.

This build accompanies the thesis *"VehiCare AI: Development of an Intelligent Vehicle Health
Assessment and Diagnostic System Using Bayesian Inference for Symptom-Based Diagnosis and
Probabilistic Issue Ranking"*.

---

## 1. What is implemented

| Area | Status |
|---|---|
| No login, registration, account, email, password or phone number anywhere | ✅ |
| Splash → Home directly; onboarding optional, skippable, shown at most once | ✅ |
| Vehicle management: add, edit, view, delete with confirmation (cascade) | ✅ |
| Assessment setup (4 types), symptom category selection, guided questionnaire | ✅ |
| Dynamic rule-based follow-up questions; irrelevant questions never asked | ✅ |
| Draft persistence in Room (survives back navigation and process death) | ✅ |
| Bayesian engine: grouped-evidence Naive Bayes, log-space, normalised, ranked | ✅ |
| Sufficiency rule (never fabricates a result from thin evidence) | ✅ |
| Ranked issues with supporting, missing and contradicting evidence | ✅ |
| Issue details with causes, checks, severity and professional guidance | ✅ |
| Independent safety evaluator + prominent alerts in results, report and PDF | ✅ |
| Assessment history with search and four filters | ✅ |
| Report screen with Save, Share text, Share PDF, Export PDF | ✅ |
| Health trends with Canvas charts (no chart dependency) | ✅ |
| Profile: display name, units, reminders info, privacy, method, about, help, terms | ✅ |
| Empty / loading / error+retry states; offline by design | ✅ |
| Seed demo data (Toyota Vios 2020) clearly marked and deletable | ✅ |
| Unit tests + instrumented Room tests | ✅ |
| `AGENTS.md`, `README.md`, `docs/DIAGNOSTIC_MODEL.md`, in-app Diagnostic Method page | ✅ |

---

## 2. Technology

| Area | Choice |
|---|---|
| Language / UI | Kotlin 2.2.10, Jetpack Compose, Material 3 (Compose BOM 2026.02.01) |
| Architecture | MVVM with a strict UI / domain / data split, repository pattern |
| Navigation | Navigation Compose 2.9.8, string routes centralized in `navigation/Destinations.kt` |
| Persistence | Room 2.8.5 with KSP, local-first, no network access at all |
| DI | Hilt 2.60.1 |
| Async / state | Coroutines 1.11.0, Flow, `ViewModel` + `StateFlow` + `collectAsStateWithLifecycle` |
| Charts | Custom Compose `Canvas` (no chart dependency) |
| PDF | Android `PdfDocument` + `FileProvider` share intent (no dependency) |
| toolchain | AGP 9.4.0, Gradle 9.6.0, JDK 17 target, minSdk **26**, compileSdk/targetSdk 37 |

`minSdk = 26` is required so `java.time` is available without desugaring, and it lets the project use
adaptive launcher icons everywhere.

Dependencies are intentionally minimal: Compose, Navigation, Room, Hilt and Kotlin coroutines, plus
`material-icons-extended` for the outlined icon set. No analytics, no networking, no chart library.

---

## 3. Build, run and test

```bash
# Build a debug APK
./gradlew assembleDebug

# Unit tests (engine maths, safety, questionnaire, mappers, repositories)
./gradlew testDebugUnitTest

# Instrumented Room/Hilt tests (requires a running emulator or device)
./gradlew connectedDebugAndroidTest

# Install on the connected device
./gradlew installDebug
```

**Environment note (this machine).** There is no system JDK on `PATH`, so command-line builds must
point `JAVA_HOME` at Android Studio's bundled runtime:

```bash
JAVA_HOME="$HOME/Downloads/android-studio/jbr" ./gradlew assembleDebug
```

Android Studio does this automatically. If the emulator is in a boot loop after a snapshot crash,
cold-boot it (Device Manager → Cold Boot Now); otherwise installs fail with
`cmd: Can't find service: package`.

Reports are written to `app/build/reports/tests/testDebugUnitTest/index.html`.

---

## 4. Architecture

```
com.example.vehicare/
├── MainActivity.kt, VehiCareApplication.kt      # @AndroidEntryPoint / @HiltAndroidApp
├── data/
│   ├── local/{entities,dao,converters,database} # Room: entities, DAOs, type converters
│   ├── mapper/                                  # entity <-> domain mappers
│   ├── repository/                              # repository implementations
│   ├── seed/                                    # first-launch sample data
│   └── di/                                      # database, DAO, repository, dispatcher bindings
├── domain/
│   ├── diagnostic/models/                       # evidence, severity, systems, analysis results
│   ├── diagnostic/knowledgebase/                # hypotheses, evidence groups, question bank
│   ├── diagnostic/bayesian/                     # engine interface + Naive Bayes implementation
│   ├── safety/                                  # SafetyEvaluator (independent of ranking)
│   ├── model/                                   # Vehicle, Assessment, trends, preferences
│   ├── repository/                              # repository interfaces (frozen contracts)
│   └── usecase/                                 # questionnaire engine, evaluation use case
├── di/DomainModule.kt                           # engine / knowledge base bindings
├── presentation/<feature>/                      # Screen + ViewModel + UiState per feature
├── navigation/                                  # Destinations.kt, VehiCareNavHost.kt
├── ui/{components,theme}/                       # design system: components, colours, type, dimens
└── utils/                                       # formatting, PDF report exporter
```

Rules enforced across the codebase (see `AGENTS.md`):

* the diagnostic engine is **pure Kotlin** — no Android, no Compose, no Room, no Hilt;
* ViewModels hold no diagnostic maths, expose one `StateFlow<UiState>`, and never touch `Context`;
* Room entities never leave `data/local`; domain models are the cross-layer contract;
* all navigation routes live in one object and screens receive callbacks, not route strings.

---

## 5. The diagnostic core in one page

Full treatment (formulas, priors, group likelihoods, thresholds, worked examples, limitations) is in
[`docs/DIAGNOSTIC_MODEL.md`](docs/DIAGNOSTIC_MODEL.md). The essentials:

* **Approach:** Naive Bayes with **grouped evidence**. `P(H|E) = P(E|H)·P(H) / P(E)`, summed over the
  candidate set which always includes an `other_undetermined` residual hypothesis.
* **Grouped evidence:** physically correlated findings (slow cranking + dim lights + clicking) are
  declared as an evidence group carrying **one** likelihood per hypothesis, so correlated symptoms
  are never multiplied as if independent. A unit test proves a grouped cluster yields a higher
  posterior than the naive product of its members.
* **Log-space maths** (log-sum-exp) for numerical stability; posteriors are normalised and therefore
  **relative estimates among the considered possibilities** — the UI says so everywhere and also
  shows evidence strength (`matched of considered findings`).
* **Missing ≠ negative:** skipped or "Unsure" answers contribute nothing; only an explicit "No"
  contributes `P(not symptom | H)`.
* **Never fabricates:** below the documented sufficiency thresholds the engine returns
  *insufficient evidence* with no ranking (safety alerts still fire).
* **Probability ≠ severity:** severity is a property of the issue, shown as its own badge, meter and
  icon; safety alerts are produced directly from symptoms by a separate evaluator.
* **Deterministic and versioned:** ties break by severity then id; every assessment stores
  `engineVersion` and `knowledgeBaseVersion` and can be reproduced exactly.

---

## 6. Assumptions

Recorded because the specification allowed reasonable assumptions in a one-shot build.

| # | Assumption |
|---|---|
| **A1** | The existing namespace/applicationId `com.example.vehicare` is kept instead of renaming to `com.vehicareai`, to avoid invalidating the working build, manifest and device install. Package *layout* follows the specified structure and the app is branded "VehiCare AI" everywhere user-visible. |
| **A2** | The knowledge base is a **Kotlin object** rather than a JSON asset. The specification allowed either; a Kotlin object keeps the engine free of Android/serialisation dependencies (so it is trivially unit-testable) and is validated on load with `require(...)`, failing loudly on invalid data. |
| **A3** | `android.disallowKotlinSourceSets=false` is set in `gradle.properties`. AGP 9 blocks the `kotlin.sourceSets` route that KSP uses to register generated sources; this is the documented AGP 9 workaround and the only non-default build flag added. |
| **A4** | The nine diagnostic thresholds are illustrative: `minAnsweredEvidence = 3`, `minTopPosterior = 0.35`, `minEvidenceStrength = 0.40`, `likelihoodFloor = 0.05`, `minimumComplement = 1e-6`, missing-evidence threshold 0.45, contradiction threshold 0.35, max 5 missing items shown, group `minMembers = 2`. |
| **A5** | Sample data is seeded on the **first launch** (detected via the onboarding flag) and only into an empty database, so it can never overwrite real records. After the user completes onboarding, seeding never runs again, and deleting all data also clears the flag. |
| **A6** | Results are recomputed from the persisted assessment through the deterministic, versioned engine when a screen displays them, while the persisted `DiagnosticResult`/`DiagnosticSafetyAlert` rows remain the immutable audit snapshot used for history counts and reports. |
| **A7** | The analysis-processing screen shows five **indeterminate** stages (no percentage) and paces them with a short delay so each real pipeline phase is readable. The delay is presentational only; no fake progress is ever displayed. |
| **A8** | The Reports tab hosts health trends (charts + metrics) while the Assessments tab hosts searchable history, and the per-assessment report is pushed from the results screen. The specification named both but did not map them to tabs. |
| **A9** | Light theme only. The specification made a dark theme optional and keeping one scheme guarantees that the safety colours keep their meaning and contrast. |

---

## 7. Deviations and deliberate replacements

* **The previous UI prototype was replaced, not extended.** The repository started as an Android
  Studio template plus a generic vehicle-crud UI (`ui/screens/*`, `data/VehiCareRepository`). Those
  screens had no counterpart in the VehiCare AI information architecture (there is no service-log
  screen or login in this specification), so they were removed and their reusable ideas (design
  tokens, card style, top bar, confirmation dialog, empty/loading states) were re-implemented as the
  shared design system in `ui/components`. The old in-memory repository was replaced by Room.
* **Maintenance records** (optional in the specification) are implemented as an optional log inside
  Vehicle Details: add and delete, with date, mileage, cost and notes.
* **Hilt** is used as specified. Because AGP 9.4 is very new, the integration was validated with a
  build spike before any feature code was written (KSP 2.2.10-2.0.2 + Hilt 2.60.1 + Room 2.8.5 all
  resolve and generate correctly on this toolchain).

---

## 8. Known limitations

1. **Knowledge-base values are illustrative** and not calibrated against real failure data — stated in
   the code comments, this README, `docs/DIAGNOSTIC_MODEL.md` and the in-app Diagnostic Method screen.
2. **Conditional independence is assumed between evidence groups**; only within-group correlation is
   neutralised (by design, see the model document).
3. **Normalised posteriors are relative**, so they inflate as the candidate set shrinks. The UI always
   shows the undetermined share and the evidence strength to compensate.
4. **No suspension/steering hypothesis** exists in the specification's 20-item list, so suspension
   observations act as cross-category signals and safety triggers; selecting only that category
   yields an honest *insufficient evidence* result.
5. **Vehicle attributes do not yet modulate priors** (age/mileage/fuel are passed to the engine for
   context and explanations only).
6. **Self-reported input only** — the prototype cannot read onboard diagnostics, by design (no
   networking, no vehicle interface).
7. **Reminders are in-app information**, not scheduled OS notifications: the app has no background
   workers and no server, and the specification only required a local reminders/info list.

---

## 9. Verification evidence (this build)

Commands (see §3 for the `JAVA_HOME` note):

```bash
./gradlew clean assembleDebug testDebugUnitTest connectedDebugAndroidTest
```

| Check | Observed result |
|---|---|
| `assembleDebug` | **BUILD SUCCESSFUL** (clean build, then incremental re-verification) |
| Unit tests | **106 tests, 0 failures, 0 errors** (engine maths + hand-verified cases, safety, questionnaire, knowledge-base integrity, mappers, converters, repositories) |
| Instrumented tests | **18 tests, 0 failures, exit code 0** on the `Medium_Phone` AVD (Room CRUD, vehicle→assessment cascade, draft save/resume round-trip, results + safety-alert persistence, seed idempotency, preferences round-trip) |

On-device walkthrough on the emulator (scripted UI-driven verification of the real APK):

| Journey step | Observed |
|---|---|
| Fresh install → onboarding (skippable) → Home | works; no auth anywhere |
| Home dashboard | sample vehicle shown with the **Sample data** badge and "7 possible issues" |
| Assessment results (seeded demo symptoms) | leading estimate **74.5%**, undetermined share 0.2%, separate **Severity: High** badge, preliminary badge, relative-estimate note |
| Ranked possible issues | **74.5% → 22.4% → 1.7% → 0.4% → 0.3%** in descending posterior order with independent severity badges, plus the engine/knowledge-base version line |
| Demonstration-data expectation (§9) | **weak battery → faulty alternator → starter motor failure**, computed by the engine (never hardcoded); the ordering is additionally asserted by unit and instrumented tests |
| Issue details | why-it-was-identified, supporting symptoms, missing/contradicting evidence, recommended checks, professional assistance, prior-vs-posterior figures |
| Report | VehiCare AI header, preliminary badge, vehicle/date/Report ID (e.g. `VCA-608CFB4F`), reported symptoms incl. explicit negatives, ranked issues, next steps, disclaimer, and Save / Share / Share PDF / Export PDF / Start New Assessment |
| PDF export | real file written to the app cache (`cache/reports/VCA-608CFB4F.pdf`, 71 KB, valid `%PDF-1.4`, 260 objects) and verified to contain the same content as the report screen |
| Safety alerts | reporting braking + overheating symptoms rendered **four alert cards at the top of results** (urgent braking fault, overheating, repeated high temperature, restart risk), above all ranking cards |
| Static audit | no `INTERNET` permission and no networking code; no auth code (only wording that states there is none); no empty `onClick` handlers; no diagnostic maths in `presentation/**` or `ui/**`; no Room entities outside `data/**` |

---

## 10. Documentation map

| File | Contents |
|---|---|
| `AGENTS.md` | Authoritative project rules: architecture, Bayesian rules, UI/UX standards, navigation, privacy, safety, testing, change management, Definition of Done |
| `README.md` | This file: setup, architecture, assumptions, deviations, limitations |
| `docs/DIAGNOSTIC_MODEL.md` | Bayesian approach, evidence rules, priors, group likelihoods, thresholds, worked examples, safety rules, limitations, test coverage |

In-app equivalents: the **Diagnostic Method** screen (plain-language Bayesian explanation with the
worked example required by Section 5.17), **Terms and Disclaimer**, **Privacy and Data**, **About**
(model versions) and **Help and FAQs**.
