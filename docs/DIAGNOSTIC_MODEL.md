# VehiCare AI — Diagnostic Model

Companion document to *VehiCare AI: Development of an Intelligent Vehicle Health Assessment and
Diagnostic System Using Bayesian Inference for Symptom-Based Diagnosis and Probabilistic Issue
Ranking*.

> **Illustrative values.** Every prior, likelihood, threshold and grouping in the knowledge base is an
> **illustrative prototype value** authored for this thesis prototype. They are informed by commonly
> documented symptom/fault relationships, but they are **not validated automotive statistics** and
> have not been calibrated against a vehicle fleet or workshop dataset. Do not cite them as
> empirical probabilities.

---

## 1. Scope and goal

The diagnostic core answers one question: **given the symptoms a non-expert reports, which vehicle
issues are most plausible, and how well do the reported observations support each of them?**

It deliberately returns three things, kept strictly separate:

| Output | Meaning | Never implies |
|---|---|---|
| Posterior probability per hypothesis | Relative support among the considered possibilities | Certainty, or that the issue is present |
| Severity per hypothesis | How serious that issue *would be* if confirmed | Urgency derived from the probability |
| Safety alerts | Direct mapping from reported symptoms to safety advice | A diagnosis, or a ranked result |

Implementation: `domain/diagnostic/**` (pure Kotlin, no Android, no UI, no Hilt annotations).
Entry point:

```kotlin
interface BayesianDiagnosticEngine {
    val engineVersion: String
    val knowledgeBaseVersion: String
    fun analyzeSymptoms(evidence: SymptomEvidence, vehicle: VehicleContext): DiagnosticAnalysis
}
```

---

## 2. Chosen approach: Naive Bayes with grouped evidence

### 2.1 Formula

```
P(H | E) = P(E | H) · P(H) / P(E)
```

with

```
P(E) = SUM_k P(E | H_k) · P(H_k)
```

over the candidate hypothesis set, which always includes the residual hypothesis
`other_undetermined` that absorbs uncertainty.

### 2.2 Conditional independence assumption (documented limitation)

The model assumes **conditional independence between evidence groups**, not between every individual
observation. Naive Bayes cannot represent "this symptom makes that symptom more likely", so
correlated observations are handled by grouping (Section 3) rather than by naive multiplication.
This assumption is the model's main simplification and is stated in the app on the Diagnostic Method
screen and in the Assessment Report disclaimer.

### 2.3 Numerics

Scoring is done entirely in **log space**:

```
logScore(H) = ln P(H)
            + SUM over triggered groups g            ln P(g | H)
            + SUM over present, non-group evidence e ln P(e | H)
            + SUM over absent,  non-group evidence e ln P(not e | H)
```

Posteriors are then obtained by normalising with the log-sum-exp trick:

```
posterior(H) = exp(logScore(H) - maxLog) / SUM_k exp(logScore(H_k) - maxLog)
```

This keeps long evidence lists from underflowing to zero and makes the result order-independent.
`P(not e | H)` is floored at `1e-6` (`DiagnosticEngineConfig.minimumComplement`) so that a
likelihood of exactly 1.0 cannot produce `ln(0) = -inf`.

### 2.4 Determinism

* Same input ⇒ same output: the engine is a pure function of (evidence, vehicle context, knowledge
  base version, engine version).
* Ties are broken deterministically: **higher severity first, then hypothesis id** (lexicographic).
* Timestamps come from an injectable clock, so tests are reproducible.
* Every analysis returns `engineVersion` (`vehicare-bayes-1.0.0`) and `knowledgeBaseVersion`
  (`1.0.0`), both stored with the assessment in Room.

---

## 3. Evidence handling rules

| Rule | Statement | Rationale |
|---|---|---|
| **E1** | A reported symptom is positive evidence and contributes `ln P(e \| H)`. | Direct Bayesian update. |
| **E2** | An explicit "No" contributes `ln(1 - P(e \| H))`. | Negative evidence is real evidence. |
| **E3** | A skipped or "Unsure" answer contributes **nothing** (log term 0.0). | Missing data must not be silently treated as absence. |
| **E4** | Evidence ids unknown to the knowledge base are ignored. | Defensive against stale drafts. |
| **E5** | An id reported as both present and absent is unusable: it is removed from **both** sets. | Prevents double counting contradictory input. |
| **G1** | A group contributes **one** likelihood `P(g \| H)` and its members are then excluded from individual scoring. | Correlated findings describe one physical cause; multiplying them would over-count. |
| **G2** | A group is *triggered* when at least `minMembers` (default 2) of its members are present **and** none of its answered members is explicitly absent. Missing members are ignored. | Consistent with E3, and prevents a single denial from being overridden by a group. |
| **G3** | A member that is present while its group is *not* triggered is scored with its own individual likelihood. | Keeps single-symptom evidence working. |
| **L1** | Unlisted (hypothesis, evidence) pairs use `likelihoodFloor = 0.05`, never 0. | A zero would permanently veto a hypothesis; the floor keeps it merely improbable. |
| **L2** | The residual hypothesis has no above-floor likelihoods, so it absorbs the mass that the modelled hypotheses cannot explain. | Explicit uncertainty instead of forced choices. |

---

## 4. Knowledge base (version 1.0.0)

21 hypotheses: 20 ranked issues plus the residual. 65 atomic evidence items, 14 evidence groups.

### 4.1 Hypotheses, priors and severity

`P(H)` values below are the authored starting likelihoods. They are *relative*: the engine
normalises over the closed candidate set, so a uniform scaling of every prior leaves the posteriors
unchanged. The residual prior (0.075) is the largest single term on purpose — the model starts out
more willing to say "something else" than to commit to a specific fault.

| # | id | Issue | System | Prior P(H) | Severity |
|---|---|---|---|---|---|
| 1 | `weak_battery` | Weak or failing battery | Electrical | 0.070 | High |
| 2 | `brake_pad_wear` | Worn brake pads | Braking | 0.065 | High |
| 3 | `worn_spark_plugs` | Worn or fouled spark plugs | Engine | 0.060 | Medium |
| 4 | `low_coolant` | Low coolant level or coolant leak | Cooling | 0.055 | High |
| 5 | `faulty_alternator` | Faulty alternator | Electrical | 0.050 | High |
| 6 | `clogged_air_filter` | Clogged air filter | Engine | 0.050 | Low |
| 7 | `brake_rotor_issues` | Brake rotor or disc problem | Braking | 0.045 | High |
| 8 | `failing_ignition_coil` | Failing ignition coil | Engine | 0.045 | Medium |
| 9 | `cooling_system_overheating` | Cooling system overheating | Cooling | 0.045 | Critical |
| 10 | `starter_motor_failure` | Starter motor failure | Starting & ignition | 0.045 | High |
| 11 | `faulty_oxygen_sensor` | Faulty oxygen sensor | Exhaust | 0.045 | Medium |
| 12 | `low_engine_oil` | Low engine oil or oil pressure | Engine | 0.040 | Critical |
| 13 | `low_transmission_fluid` | Low or degraded transmission fluid | Transmission | 0.040 | Medium |
| 14 | `clogged_fuel_filter` | Clogged fuel filter | Fuel | 0.040 | Medium |
| 15 | `tire_wheel_imbalance` | Tire or wheel imbalance | Tires | 0.040 | Medium |
| 16 | `faulty_fuel_pump` | Faulty fuel pump | Fuel | 0.035 | High |
| 17 | `thermostat_malfunction` | Thermostat malfunction | Cooling | 0.035 | High |
| 18 | `transmission_malfunction` | Internal transmission malfunction | Transmission | 0.035 | Critical |
| 19 | `serpentine_belt_issue` | Serpentine belt or tensioner issue | Engine | 0.035 | High |
| 20 | `radiator_fan_failure` | Radiator fan failure | Cooling | 0.030 | High |
| — | `other_undetermined` | Other / undetermined (residual, never ranked) | Other | 0.075 | Low |

### 4.2 Evidence groups (correlation handling)

Each group carries **one** likelihood per hypothesis. Members are disjoint across groups, which is
asserted by a unit test.

| Group | Members | Example: `P(group \| weak_battery)` |
|---|---|---|
| `g_battery_start_cluster` | slow cranking, dim lights, clicking on start | 0.85 |
| `g_dead_crank_cluster` | does not crank, engine clicking | 0.55 (battery) / 0.85 (starter) |
| `g_overheat_cluster` | currently overheating, steam, coolant loss | 0.90 (`cooling_system_overheating`) |
| `g_brake_noise_cluster` | squeaking, grinding | 0.80 (`brake_pad_wear`) |
| `g_brake_safety_cluster` | reduced braking, soft pedal, pulling | 0.50 (`brake_pad_wear`) |
| `g_transmission_slip_cluster` | slipping, delayed engagement, harsh shifts | 0.70 (`low_transmission_fluid`) |
| `g_misfire_cluster` | hesitation/misfire, rough idle | 0.65 (`worn_spark_plugs`) |
| `g_power_loss_cluster` | significant power loss, intermittent power loss | 0.45 (`clogged_fuel_filter`) |
| `g_oil_pressure_cluster` | oil pressure warning, engine knocking | 0.75 (`low_engine_oil`) |
| `g_wheel_imbalance_cluster` | high-speed vibration, steering vibration | 0.85 (`tire_wheel_imbalance`) |
| `g_fuel_odour_cluster` | fuel smell, visible fuel leak | floor (`faulty_fuel_pump`) |
| `g_steering_fault_cluster` | loose steering, hard steering, pulling | floor (no dedicated hypothesis — see §8) |
| `g_coolant_level_cluster` | low coolant, sweet smell | 0.85 (`low_coolant`) |
| `g_exhaust_smoke_cluster` | blue smoke, white smoke | 0.35 (`low_coolant`) |

*Illustrative* single-evidence likelihoods follow the same logic. `weak_battery` for example uses
slow cranking 0.80, dim lights 0.70, clicking on start 0.60, cranks-but-won't-start 0.50, battery
aged 3+ years 0.70, battery warning light 0.65 and does-not-crank 0.35. Everything unlisted falls to
the 0.05 floor.

### 4.3 Sufficiency thresholds

Defined in `DiagnosticEngineConfig` and enforced before any ranking is reported:

| Threshold | Value | Purpose |
|---|---|---|
| `minAnsweredEvidence` | 3 findings | Below this, no ranking is attempted at all. |
| `minTopPosterior` | 0.35 | The best-supported hypothesis must clear this. |
| `minEvidenceStrength` | 0.40 | The leader must also match a reasonable share of the findings it was scored against. |
| positive support | at least 1 matched finding | Purely negative answers cannot produce a ranking: nothing is affirmatively supported. |

When any condition fails, the engine returns `SufficiencyStatus.INSUFFICIENT_EVIDENCE`, no ranked
issues, and the message *"Unable to identify a sufficiently supported possible issue from the
provided information. Consider adding more symptom details or consulting a qualified mechanic."*
**Safety alerts are still returned** in that case, because safety never depends on sufficiency.

Rationale for a conservative 0.35: with a 0.05 floor and a 21-hypothesis space, thin evidence
produces a deliberately flat posterior distribution. Reporting a "winner" from such a distribution
would overstate what the model knows, so the engine prefers to say so.

### 4.4 Evidence strength

Every ranked issue reports `matchedEvidenceCount / consideredEvidenceCount` alongside its
percentage. The UI always shows the percentage **and** this strength, and always explains that
percentages are relative estimates among the considered possibilities — a 75% posterior means "three
quarters of the retained probability mass", not "75% certain".

---

## 5. Worked examples

### 5.1 Hand-verified two-hypothesis case (specification Section 6.5)

Priors `P(H1) = 0.6`, `P(H2) = 0.4`; one reported symptom with `P(E|H1) = 0.2`, `P(E|H2) = 0.9`.

```
P(E) = 0.6 · 0.2 + 0.4 · 0.9 = 0.12 + 0.36 = 0.48
P(H1|E) = 0.12 / 0.48 = 0.25
P(H2|E) = 0.36 / 0.48 = 0.75
```

Asserted by `NaiveBayesDiagnosticEngineTest.hand verified case yields posteriors 0_25 and 0_75`.

### 5.2 Hand-verified conflicting-evidence case

Same priors, with `H1` predicting `e2` (0.9) and not `e1` (0.2), and `H2` predicting `e1` (0.9) and
not `e2` (0.2). The user reports `e1` present and `e2` explicitly denied:

```
H1: 0.6 · 0.2 · (1 - 0.9) = 0.012
H2: 0.4 · 0.9 · (1 - 0.2) = 0.288
P(E) = 0.300  ->  H1 = 0.04,  H2 = 0.96
```

Asserted by `hand verified conflicting evidence case matches hand calculation`, together with the
requirement that the denied `e2` surfaces as *contradicting evidence* for `H1`.

### 5.3 Grouped evidence is not multiplied naively

For a two-member cluster with member likelihoods 0.80 and 0.70, the grouped model uses the stated
group likelihood (0.85). A test compares it against the same knowledge base with the group likelihood
set to the naive product `0.8 × 0.7 = 0.56` and asserts that the naive product yields a strictly
**lower** posterior for the same two red flags. That is the quantitative justification for rule G1.

### 5.4 Demonstration data (Section 9) — computed, never hardcoded

Seeded vehicle: **Toyota Vios, 2020, Gasoline, Automatic**. Seeded symptoms: slow engine cranking,
dim dashboard lights, occasional difficulty starting. The seeded assessment therefore carries:

| Present evidence | Absent evidence (explicit "No") |
|---|---|
| `slow_cranking`, `dim_lights`, `cranks_no_start` | engine noises, power loss, all six warning lights, all six braking findings, all three smoke findings, `clicking_on_start` |

Selected categories: Starting & Ignition, Electrical, Dashboard Warning Lights.

With the group `g_battery_start_cluster` triggered (two of its three members present, none denied),
the log scores are dominated by the prior and one group likelihood each:

| Hypothesis | ln P(H) | ln P(group) | ln P(cranks_no_start) | Rank |
|---|---|---|---|---|
| `weak_battery` | -2.66 | -0.16 | -0.69 | **1** |
| `faulty_alternator` | -3.00 | -0.51 | -0.80 | **2** |
| `starter_motor_failure` | -3.10 | -0.60 | -1.05 | **3** |
| `worn_spark_plugs` | -2.81 | -1.90 | -0.51 | 4 |
| `other_undetermined` | -2.59 | -3.00 | -3.00 | (residual) |

Ordering `weak battery → faulty alternator → starter motor` is asserted by a unit test; the
percentages themselves are never hardcoded anywhere in the app or the seed.

---

## 6. Safety layer (independent of probability)

`domain/safety/RuleBasedSafetyEvaluator` maps **symptoms directly** to alerts. It never sees the
posterior distribution, so a rare but dangerous condition cannot be hidden by a low probability, and
a high-probability harmless issue cannot suppress a warning.

| Rule | Triggering findings | Level |
|---|---|---|
| `brake_performance` | reduced braking, soft/spongy pedal, pulling to one side, burning smell while braking | Urgent |
| `overheating_current` | currently overheating, steam from engine bay, temperature warning light | Urgent |
| `overheating_frequent` | frequent overheating, repeated coolant loss | Advisory |
| `fire_risk` | smoke from engine bay, electrical burning smell | Urgent |
| `oil_pressure` | oil pressure warning light | Urgent |
| `fuel_leak` | visible fuel leak, fuel smell | Urgent |
| `steering_fault` | loose/vague steering, hard steering | Urgent |
| `sudden_power_loss` | sudden loss of power while driving, intermittent power loss | Urgent |
| `engine_knock` | heavy knocking | Advisory |
| `stranded_start_risk` | any two of: does not crank, clicking on start, cranks but will not start | Advisory |
| `braking_abs` | any two of: ABS light, brake grinding, brake vibration | Advisory |

Alerts are ordered urgent-first, then by id, and are shown above the ranking in results and inside the
report and the PDF. Wording is deliberately general: no diagnosis, no "keep driving" advice, and no
implication of emergency-service capability.

---

## 7. Questionnaire → evidence mapping

43 questions: 10 base questions, 22 category-specific questions and 11 rule-based follow-ups.
`QuestionnaireEngine` decides which are relevant and converts answers into evidence.

* Positive options contribute present evidence; "No" options contribute absent evidence; "Unsure"
  contributes nothing (rules E1–E3).
* Follow-up conditions are **declarative** (`ShowCondition(presentAny, presentAll, minReportedSeverity)`)
instead of lambdas, so a draft in Room can be replayed after process death and the logic stays
unit-testable.
* Question selection is evaluated to a fixpoint, because answering one follow-up can unlock another.
* A typical session lands between 8 and 15 questions; a unit test asserts that range for a
  representative path.

---

## 8. Limitations (stated openly)

1. **Illustrative values.** No calibration against real failure data; the priors and likelihoods are
   authored, not learned. Section 6.4 of the specification requires them to be labelled as such,
   and they are (here, in code comments, and in the app's Diagnostic Method screen).
2. **Conditional independence between groups.** Real fault signatures correlate across groups
   (for example overheating and coolant loss vs. electrical load). The model only neutralises
   correlation *within* the declared groups.
3. **Closed-set normalisation.** Posteriors are relative to the candidate set, so they inflate as the
   candidate set shrinks. The UI mitigates this by always showing the undetermined share and the
   evidence strength, and by never calling a percentage a confidence.
4. **No dedicated suspension/steering hypothesis.** The specification's 20 hypotheses include tire
   and wheel imbalance but no suspension/steering fault. Suspension observations therefore act as
   cross-category signals and safety triggers only. If a user selects *only* the suspension category,
   the engine honestly reports *insufficient evidence* rather than inventing an issue — asserted by
   `KnowledgeBaseIntegrityTest`.
5. **Self-reported input.** Everything depends on what a non-expert notices and reports; the app
   cannot read onboard diagnostics in this prototype.
6. **Single-vehicle view of likelihoods.** Vehicle age, mileage and fuel type are passed to the
   engine for context and explanations, but the prototype does not yet modulate priors by them.

---

## 9. Validation and tests

`./gradlew testDebugUnitTest` (40+ tests) covers:

* Bayes calculations and normalisation, including both hand-verified cases above.
* Ranking order and deterministic tie-breaking (severity, then id).
* Grouped evidence vs. naive multiplication; single-member fallback; group blocking on denial.
* Missing, unknown, conflicting and purely negative evidence; empty submissions.
* Invalid priors/likelihoods/floors rejected loudly at knowledge-base load.
* Reproducibility (identical input ⇒ identical analysis, including a fixed clock).
* Safety-critical detection, including the low-probability/high-severity case where the engine
  refuses to rank but must still warn.
* Questionnaire evidence mapping, dynamic follow-ups, category filtering, progress and severity
  bounds.
* Knowledge-base integrity: 20 ranked + residual hypotheses, complete UI/report fields, group
  disjointness, category coverage.
* The demonstration-data ordering (Section 5.4 above).
