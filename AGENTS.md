# AGENTS.md — VehiCare AI

Authoritative, living source of project-wide rules. **Read this file first**, before any
inspection, edit, or delegation. No other instruction file may conflict with it.

---

## 1. Project overview

- **Name:** VehiCare AI
- **Thesis:** *VehiCare AI: Development of an Intelligent Vehicle Health Assessment and
  Diagnostic System Using Bayesian Inference for Symptom-Based Diagnosis and Probabilistic
  Issue Ranking*
- **Value proposition:** *Describe the symptoms. Understand the possibilities. Make informed
  decisions about your vehicle's health.*
- **Platform:** Android, Kotlin, Jetpack Compose, Material 3, Room (KSP), MVVM + Coroutines/Flow,
  Navigation Compose, Hilt DI.
- **Core research component:** a pure-Kotlin Bayesian diagnostic engine (grouped-evidence
  Naive Bayes, log-space, normalized, ranked by posterior) driven by a documented knowledge
  base.
- **Users:** vehicle owners (non-technical) and mechanics (technical).
- **Philosophy:** fast, accessible, local-first, offline-capable, no mandatory auth, honest
  about uncertainty.

Namespace/applicationId: `com.example.vehicare` (kept from the original project for
continuity; documented as Assumption A1 in README.md). minSdk 26, compileSdk 37, targetSdk 37.

---

## 2. Product principles (non-negotiable)

1. **No login, registration, account, email, password, phone number, or verification.** No auth
   prompts or popups anywhere.
2. Splash -> **Home Dashboard immediately**. Onboarding is optional, skippable, shown once at
   most, and never blocks Home.
3. A user can start an assessment within a few taps.
4. All data (vehicles, assessments, reports) is stored locally in Room. The app is fully
   functional offline. Any future sync must be optional.
5. Results must be understandable to non-technical users.
6. Safety warnings are prominent and are never buried under probability rankings.
7. Probability is never presented as certainty, and probability is never conflated with
   severity or urgency.

---

## 3. Architecture

```
com.example.vehicare/
  MainActivity.kt, VehiCareApplication.kt
  data/       local/{dao,database,entities,converters}, models, repository (impl), mapper
  domain/     diagnostic/{bayesian,knowledgebase,models}, safety, model, repository (ifaces), usecase
  presentation/ <feature>/{<Feature>Screen.kt, <Feature>ViewModel.kt, <Feature>UiState.kt}
  navigation/ Destinations.kt, VehiCareNavHost.kt
  ui/         components, theme
  utils/      formatting, result, dispatchers
```

Responsibilities:

- **Entities** never leave `data/local`. **Domain models** are the contract across layers.
  **UI state models** are presentation-only. Mappers convert between them explicitly.
- **ViewModel** exposes a single `StateFlow<XUiState>`, performs no Android I/O directly, holds
  no `Context`, and contains no diagnostic math. Events in, state out.
- **Repositories** are interfaces in `domain/repository`, implemented in `data/repository`,
  exposed as `Flow` and `suspend` functions, returning domain models.
- **Room:** DAOs are interface-only; list/`Map` columns use `@TypeConverters`; FKs declare
  `ForeignKey.CASCADE` where deletion semantics require it; schema export disabled; IO work on
  `Dispatchers.IO`.
- **Navigation:** all routes are constants in `navigation/Destinations.kt`; screens never build
  route strings inline; arguments are `navArgument` typed; depth stays shallow; back behaviour
  is predictable (questionnaire and forms push on top, main tabs are single-top with state
  restore).
- **Hilt:** `@HiltAndroidApp` on the Application, `@AndroidEntryPoint` on `MainActivity`,
  `@HiltViewModel` on ViewModels, bindings in `di/` modules. No service locators.
- **Diagnostic logic lives only in `domain/diagnostic/**`** and is pure Kotlin: no Android, no
  Compose, no Room, no Hilt annotations, no `Context`.

---

## 4. Bayesian engine rules

- UI-independent, Hilt-independent, deterministic, and reproducible: same input -> same output.
- Input/output models are plain data classes in `domain/diagnostic/models`.
- Approach: **Naive Bayes with grouped evidence**, cross-group conditional independence assumed
  and documented. Correlated symptoms are declared as `EvidenceGroup`s carrying ONE explicit
  likelihood per hypothesis, so correlated evidence is never naively multiplied.
- `P(H | E) = P(E | H) * P(H) / P(E)`, with `P(E) = sum_k P(E | H_k) * P(H_k)` over the candidate
  hypothesis set (including the `other_undetermined` hypothesis).
- Compute log-likelihoods and normalize with log-sum-exp; never multiply raw probabilities across
  many terms.
- **Missing/skipped evidence contributes nothing** (neutral log term 0.0). Explicit "No" answers
  contribute `P(not symptom | H)` = `1 - P(symptom | H)`.
- Unlisted (hypothesis, evidence) pairs use the documented likelihood floor (`0.05`), never 0.
- Priors and likelihoods must be validated in `[0, 1]` at knowledge-base load time and must fail
  loudly (`IllegalArgumentException`) on invalid data. No silent clamping.
- Priors and posteriors are kept distinct in naming (`priorProbability` vs
  `posteriorProbability`); ranking is **by posterior** descending.
- **Sufficiency rule:** insufficient evidence returns an explicit insufficient result with no
  ranked issues; the engine never fabricates a result. Thresholds are documented in
  `docs/DIAGNOSTIC_MODEL.md` and implemented in `DiagnosticEngineConfig`.
- Ties break deterministically: higher severity first, then hypothesis id (lexicographic).
- Posteriors are relative estimates over a closed hypothesis set. The UI must always say so and
  must always show evidence strength (matched/total) next to any percentage.
- Every analysis carries `engineVersion` and `knowledgeBaseVersion`; both are stored with the
  assessment in Room.
- The engine is exposed behind `BayesianDiagnosticEngine` so a remote implementation could be
  added later. No networking in the prototype.
- Safety is **separate**: `SafetyEvaluator` maps symptoms directly to alerts regardless of
  ranking. Probability never suppresses, reorders, or hides a safety alert.
- Unit tests are mandatory for: the hand-verified case (priors 0.6/0.4, likelihoods 0.2/0.9 ->
  posteriors 0.25/0.75), normalization, ranking, tie-breaking, grouped vs naive multiplication,
  missing/conflicting evidence, empty submissions, invalid values, reproducibility, and
  low-probability/high-severity safety detection.

---

## 5. UI/UX standards

- Palette: white backgrounds, soft gray card surfaces, dark charcoal text, **deep navy** for
  headings and primary elements; teal/green = healthy, amber = warning, red = critical/safety;
  blue gradients only as subtle intelligent-diagnostic accents.
- Typography: modern sans-serif; large bold titles, medium section headings, clear body, small
  labels, **large numerals for probabilities**.
- Components: rounded cards (moderate radius), one shared spacing/radius scale (`ui/theme/Dimens.kt`),
  simple line icons, bottom navigation with labels, subtle transitions only. Reusable components
  live in `ui/components` and are reused rather than re-styled per screen.
- Accessibility: sufficient contrast, >=48dp touch targets, content descriptions on icons,
  **never rely on colour alone** for severity or probability (always pair colour with text and
  an icon), every probability shown as text as well as a graphic.
- Responsive: must work on small and large phones; no hardcoded screen widths; respect system
  insets and safe areas.
- Usability beats decoration. No decorative screens, no dead buttons, no placeholder-only actions.

---

## 6. Navigation rules

```
Splash -> (first launch only, skippable) Onboarding -> Home
Home bottom nav: Home | My Vehicles | Assessments | Reports | Profile
Central primary action: Start Assessment
```

- No auth screens, ever. Onboarding is skippable, non-blocking, and shown at most once
  (a Room preference row, no new dependency).
- Routes are centralized constants; deep links are not required.
- Questionnaire and form progress survive back navigation and process death (drafts persisted in
  Room).
- Deleting a vehicle or a report requires an explicit confirmation dialog.
- Keep depth shallow, use `VehiCareTopBar` consistently, and never hide the only way back.

---

## 7. Subagent responsibilities

Subagents mirror the workstreams: (1) UI/Compose, (2) vehicle + database, (3) Bayesian engine
(highest priority), (4) questionnaire/workflow, (5) results + reports, (6) safety + validation,
(7) QA + review. Every subagent must:

1. Read `AGENTS.md` first and follow its conventions.
2. Understand the existing structure before writing anything.
3. Stay inside its assigned module; never edit files owned by another workstream without
   coordination.
4. Define and honour interfaces before implementation.
5. Report: files created/changed, status, unresolved issues, and how it validated its work
   (build/test output).
6. Never swap real code for stubs, and never remove working functionality.

---

## 8. Code quality

- Idiomatic Kotlin; immutable `data class` state; `val` by default; meaningful names; no
  duplicated logic; small focused composables; no diagnostic math in composables or ViewModels.
- Comments explain *why* and document Bayesian formulas/assumptions; do not narrate obvious code.
- Keep dependencies minimal. A new dependency requires justification in the README.
- Never delete working features and never replace real logic with a placeholder to make a screen
  compile.
- Public domain APIs get KDoc where behaviour is non-obvious (engine, safety, sufficiency).

---

## 9. Data and privacy

- Local-first: Room is the single source of truth; the app must work with no network.
- Minimal personal data: an optional local display name only. **No email, no password, no phone
  number, no account.**
- Vehicle identifiers (plate, VIN) are optional and never required.
- No private data in logs. No analytics, no trackers, no networking code.
- Privacy screen explains local storage and offers "delete all data".
- Data layer stays sync-ready (repositories + stable ids + timestamps) so optional sync could be
  added later without redesign.

---

## 10. Safety and disclaimers

- Every result is labelled **preliminary**; never claim a confirmed diagnosis or certainty.
- Safety alerts are evaluated from symptoms directly, are shown at the top of results and inside
  reports, and are never ranked or hidden by probability.
- No "keep driving" guidance when serious safety concerns are reported; give general guidance
  only and never imply emergency-service functionality.
- Always encourage professional inspection; low-probability issues may still carry high severity,
  shown as separate visuals.
- The standard disclaimer appears in results, reports, PDF exports, and the Terms screen.

---

## 11. Testing requirements

JUnit unit tests must cover: Bayes calculations/normalization (hand-verified cases), ranking and
 deterministic tie-breaking, grouped vs naive evidence, missing/skipped/conflicting evidence and
 empty submissions, invalid probability rejection on load, reproducibility, safety-critical
 detection (including low-probability + high-severity), Room DAO/CRUD and cascade behaviour
 in-memory, questionnaire state/dynamic follow-ups/draft persistence, and key UI states and
 navigation routes.

Before declaring completion: `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` must
pass, and instrumented tests must pass when an emulator is available.

---

## 12. Workflow

inspect -> read AGENTS.md -> assess status -> split tasks -> define interfaces -> data/diagnostic
logic -> UI/navigation -> integrate -> test/compile -> review the full user journey -> fix/polish
-> update docs.

---

## 13. Change management

- Preserve working functionality; review related files before changing them.
- Keep changes focused, traceable, and documented; avoid large rewrites of working modules.
- Resolve conflicts by owner: one owner per file/module; coordinate shared files (theme, routes,
  DI modules) explicitly.
- Record architectural decisions (and every deviation) in `README.md`.
- Update this file whenever conventions change.

---

## 14. Completion checklist (Definition of Done)

- [ ] No login, registration, or account requirement anywhere.
- [ ] Splash -> Home works; onboarding skippable and non-blocking.
- [ ] Vehicle management (add, edit, view, delete with confirmation) works.
- [ ] Questionnaire works with dynamic follow-ups and saved progress.
- [ ] Bayesian engine implemented as specified, with documented, versioned model.
- [ ] Results are probability-based, ranked, explained (supporting/missing/contradicting evidence).
- [ ] Probability and severity are visually and conceptually separate.
- [ ] Safety alerts trigger independently of ranking and appear prominently.
- [ ] Assessment history, search, and filters work.
- [ ] Reports can be saved and shared; PDF export works.
- [ ] Room persistence works, including drafts and seed data.
- [ ] UI follows the design system and accessibility rules.
- [ ] Empty, loading, error, and offline states are handled.
- [ ] Unit tests pass and the project compiles.
- [ ] No major navigation or interaction bugs remain.
- [ ] Limitations and disclaimers are clearly communicated; KB values labelled illustrative.
- [ ] `AGENTS.md`, `README.md`, and `docs/DIAGNOSTIC_MODEL.md` are complete and current.

---

## 15. Documentation map

| File | Purpose |
|---|---|
| `AGENTS.md` | This file: authoritative rules for humans and agents. |
| `README.md` | Setup, build/run, architecture, assumptions, deviations. |
| `docs/DIAGNOSTIC_MODEL.md` | Bayesian approach, formulas, priors/likelihoods rationale, thresholds, limitations (all values illustrative). |
