package com.example.vehicare.domain.diagnostic.knowledgebase

/** Input control for a questionnaire step. */
enum class QuestionInput { SINGLE, MULTI, SLIDER }

/**
 * One answer option mapped to knowledge-base evidence.
 *
 * "Unsure" and skipped answers map to NO evidence at all (missing evidence is not negative
 * evidence). Explicit "No" options map to [absentEvidenceIds].
 */
data class AnswerOption(
    val id: String,
    val label: String,
    val presentEvidenceIds: List<String> = emptyList(),
    val absentEvidenceIds: List<String> = emptyList()
)

/**
 * Declarative follow-up condition, evaluated against the evidence implied by current answers.
 * Declarative (and not a lambda) so drafts survive process death and the logic stays testable.
 */
data class ShowCondition(
    val presentAny: Set<String> = emptySet(),
    val presentAll: Set<String> = emptySet(),
    val minReportedSeverity: Int? = null
) {
    fun isSatisfiedBy(presentEvidenceIds: Set<String>, reportedSeverity: Int?): Boolean {
        if (presentAny.isNotEmpty() && presentEvidenceIds.none { it in presentAny }) return false
        if (presentAll.isNotEmpty() && !presentEvidenceIds.containsAll(presentAll)) return false
        if (minReportedSeverity != null && (reportedSeverity ?: 0) < minReportedSeverity) return false
        return true
    }
}

data class Question(
    val id: String,
    val prompt: String,
    val helpText: String,
    val input: QuestionInput,
    /** Categories this question belongs to; empty means it is part of the base question set. */
    val categoryIds: List<String> = emptyList(),
    val options: List<AnswerOption> = emptyList(),
    /** Optional questions can be skipped without answering. */
    val optional: Boolean = true,
    val showIf: ShowCondition? = null
)

/**
 * The VehiCare AI question bank: 10 base questions (the specification's table), category-specific
 * questions and rule-based dynamic follow-ups. Only the questions that are relevant to the selected
 * categories and to the answers already given are ever asked, keeping a session to roughly 8-15
 * steps (Section 5.9).
 */
object QuestionBank {

    const val ONSET_QUESTION_ID = "q_onset"
    const val SEVERITY_QUESTION_ID = "q_severity"

    /** Options used by several "which lights are on" style questions. */
    private val yesNo = listOf(
        AnswerOption("yes", "Yes"),
        AnswerOption("no", "No")
    )

    private val unsureYesNo = listOf(
        AnswerOption("yes", "Yes"),
        AnswerOption("no", "No"),
        AnswerOption("unsure", "Unsure")
    )

    val baseQuestions: List<Question> = listOf(
        Question(
            id = "q_engine_noise",
            prompt = "Does the engine produce an unusual sound?",
            helpText = "Think about knocking, ticking, grinding or whining that was not there before.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("none", "No", absentEvidenceIds = listOf("engine_clicking_ticking", "engine_knocking", "engine_grinding", "engine_whining")),
                AnswerOption("clicking", "Clicking or ticking", listOf("engine_clicking_ticking")),
                AnswerOption("knocking", "Knocking", listOf("engine_knocking")),
                AnswerOption("grinding", "Grinding", listOf("engine_grinding")),
                AnswerOption("whining", "Whining", listOf("engine_whining")),
                AnswerOption("other", "Other")
            )
        ),
        Question(
            id = "q_starting",
            prompt = "How does the vehicle behave when starting?",
            helpText = "Describe the most typical starting behaviour over the last few days.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("normal", "Starts normally", absentEvidenceIds = listOf("slow_cranking", "cranks_no_start", "no_crank", "starts_then_stalls", "clicking_on_start")),
                AnswerOption("slow_crank", "Cranks slowly", listOf("slow_cranking")),
                AnswerOption("no_start", "Cranks but won't start", listOf("cranks_no_start")),
                AnswerOption("no_crank", "Doesn't crank", listOf("no_crank")),
                AnswerOption("stalls", "Starts then stalls", listOf("starts_then_stalls"))
            )
        ),
        Question(
            id = "q_overheating",
            prompt = "Has the temperature gauge reached a high or dangerous level?",
            helpText = "A gauge in the red zone or a temperature warning light counts as overheating.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("no", "No", absentEvidenceIds = listOf("overheating_occasional", "overheating_frequent", "overheating_current")),
                AnswerOption("occasionally", "Occasionally", listOf("overheating_occasional")),
                AnswerOption("frequently", "Frequently", listOf("overheating_frequent")),
                AnswerOption("currently", "Currently overheating", listOf("overheating_current"))
            )
        ),
        Question(
            id = "q_performance",
            prompt = "Have you noticed reduced engine power?",
            helpText = "For example slow acceleration, or struggling to maintain speed uphill.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("no", "No", absentEvidenceIds = listOf("loss_of_power_mild", "loss_of_power_significant", "intermittent_power_loss")),
                AnswerOption("mild", "Mild", listOf("loss_of_power_mild")),
                AnswerOption("significant", "Significant", listOf("loss_of_power_significant")),
                AnswerOption("intermittent", "Intermittent loss", listOf("intermittent_power_loss"))
            )
        ),
        Question(
            id = "q_warning_lights",
            prompt = "Which dashboard warning lights are on?",
            helpText = "Select every light that is illuminated while the engine is running.",
            input = QuestionInput.MULTI,
            options = listOf(
                AnswerOption("check_engine", "Check Engine", listOf("check_engine_light")),
                AnswerOption("battery", "Battery", listOf("warning_battery_light")),
                AnswerOption("oil", "Oil Pressure", listOf("oil_pressure_warning")),
                AnswerOption("temperature", "Temperature", listOf("temperature_warning_light")),
                AnswerOption("abs", "ABS", listOf("abs_light")),
                AnswerOption("airbag", "Airbag", listOf("airbag_light")),
                AnswerOption(
                    "none",
                    "None",
                    absentEvidenceIds = listOf(
                        "check_engine_light", "warning_battery_light", "oil_pressure_warning",
                        "temperature_warning_light", "abs_light", "airbag_light"
                    )
                ),
                AnswerOption("other", "Other")
            )
        ),
        Question(
            id = "q_smoke",
            prompt = "Have you noticed unusual exhaust smoke?",
            helpText = "Colour matters: white can mean coolant, blue can mean oil, black can mean excess fuel.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("none", "None", absentEvidenceIds = listOf("smoke_white", "smoke_blue", "smoke_black")),
                AnswerOption("white", "White", listOf("smoke_white")),
                AnswerOption("blue", "Blue", listOf("smoke_blue")),
                AnswerOption("black", "Black", listOf("smoke_black")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_braking",
            prompt = "Any unusual braking behaviour?",
            helpText = "Select everything you notice, for example noise, vibration or a change in pedal feel.",
            input = QuestionInput.MULTI,
            options = listOf(
                AnswerOption(
                    "none",
                    "No",
                    absentEvidenceIds = listOf(
                        "brake_squeaking", "brake_grinding", "brake_vibration",
                        "brake_soft_pedal", "brake_reduced_performance", "brake_pulling_side"
                    )
                ),
                AnswerOption("squeaking", "Squeaking", listOf("brake_squeaking")),
                AnswerOption("grinding", "Grinding", listOf("brake_grinding")),
                AnswerOption("vibration", "Vibration", listOf("brake_vibration")),
                AnswerOption("soft_pedal", "Soft pedal", listOf("brake_soft_pedal")),
                AnswerOption("reduced", "Reduced braking performance", listOf("brake_reduced_performance"))
            )
        ),
        Question(
            id = "q_electrical",
            prompt = "Are dashboard or headlights dim or flickering?",
            helpText = "Dim lights while idling, or lights that brighten when you rev the engine, are relevant.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("no", "No", absentEvidenceIds = listOf("dim_lights")),
                AnswerOption("occasionally", "Occasionally", listOf("dim_lights")),
                AnswerOption("yes", "Yes", listOf("dim_lights"))
            )
        ),
        Question(
            id = ONSET_QUESTION_ID,
            prompt = "When did the issue begin?",
            helpText = "This helps put the findings in context; it does not change the probability maths.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("today", "Today"),
                AnswerOption("within_week", "Within a week"),
                AnswerOption("within_month", "Within a month"),
                AnswerOption("more_than_month", "More than a month ago"),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = SEVERITY_QUESTION_ID,
            prompt = "How severe is the problem?",
            helpText = "1 means a minor annoyance, 5 means the vehicle is difficult or unsafe to use.",
            input = QuestionInput.SLIDER,
            options = emptyList(),
            optional = false
        )
    )

    /** Rule-based follow-ups: asked only when the triggering finding was reported. */
    val followUpQuestions: List<Question> = listOf(
        Question(
            id = "q_difficulty_starting",
            prompt = "Do you sometimes have difficulty starting the engine?",
            helpText = "For example it needs longer cranking than usual before it fires.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("yes", "Yes", listOf("cranks_no_start")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("cranks_no_start"))
            ),
            showIf = ShowCondition(presentAny = setOf("slow_cranking", "dim_lights", "starts_then_stalls"))
        ),
        Question(
            id = "q_battery_age",
            prompt = "Is the battery three or more years old?",
            helpText = "Most batteries last three to five years; age raises the chance of a weak battery.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("yes", "Yes", listOf("battery_older_than_3y")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("battery_older_than_3y")),
                AnswerOption("unsure", "Unsure")
            ),
            showIf = ShowCondition(presentAny = setOf("slow_cranking", "no_crank", "clicking_on_start", "dim_lights"))
        ),
        Question(
            id = "q_clicking_start",
            prompt = "Do you hear a clicking sound when you try to start?",
            helpText = "A single loud click with no cranking usually points at the starter circuit.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("yes", "Yes", listOf("clicking_on_start")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("clicking_on_start")),
                AnswerOption("unsure", "Unsure")
            ),
            showIf = ShowCondition(presentAny = setOf("slow_cranking", "no_crank", "cranks_no_start"))
        ),
        Question(
            id = "q_coolant_level",
            prompt = "Is the coolant level low in the reservoir?",
            helpText = "Check only when the engine is cold. Never open the radiator cap while hot.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("low", "Low", listOf("coolant_low")),
                AnswerOption("ok", "At the correct level", absentEvidenceIds = listOf("coolant_low")),
                AnswerOption("unsure", "Unsure")
            ),
            showIf = ShowCondition(presentAny = setOf("overheating_occasional", "overheating_frequent", "overheating_current"))
        ),
        Question(
            id = "q_coolant_loss",
            prompt = "Do you have to top up the coolant repeatedly?",
            helpText = "Repeated topping up usually means coolant is escaping somewhere.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("yes", "Yes", listOf("coolant_loss")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("coolant_loss")),
                AnswerOption("unsure", "Unsure")
            ),
            showIf = ShowCondition(presentAny = setOf("overheating_occasional", "overheating_frequent", "overheating_current"))
        ),
        Question(
            id = "q_steam",
            prompt = "Have you seen steam from the engine bay?",
            helpText = "Steam usually means coolant is escaping under pressure.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("yes", "Yes", listOf("steam_from_engine_bay")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("steam_from_engine_bay")),
                AnswerOption("unsure", "Unsure")
            ),
            showIf = ShowCondition(presentAny = setOf("overheating_frequent", "overheating_current", "coolant_loss"))
        ),
        Question(
            id = "q_radiator_fan",
            prompt = "Does the radiator fan run when the engine is hot?",
            helpText = "With the engine hot and idling, the fan should cycle on and off.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("yes", "Yes", absentEvidenceIds = listOf("fan_not_running")),
                AnswerOption("no", "No", listOf("fan_not_running")),
                AnswerOption("unsure", "Unsure")
            ),
            showIf = ShowCondition(presentAny = setOf("overheating_occasional", "overheating_frequent", "overheating_current"))
        ),
        Question(
            id = "q_gauge",
            prompt = "Does the temperature gauge fluctuate?",
            helpText = "A gauge that swings between normal and hot can indicate a thermostat problem.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("steady", "Stays steady", absentEvidenceIds = listOf("gauge_fluctuating")),
                AnswerOption("fluctuates", "Fluctuates", listOf("gauge_fluctuating")),
                AnswerOption("unsure", "Unsure")
            ),
            showIf = ShowCondition(presentAny = setOf("overheating_occasional", "overheating_frequent", "overheating_current"))
        ),
        Question(
            id = "q_brake_pedal",
            prompt = "How does the brake pedal feel?",
            helpText = "A soft or spongy pedal can indicate air or a fluid leak in the hydraulic system.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("firm", "Firm and normal", absentEvidenceIds = listOf("brake_soft_pedal")),
                AnswerOption("soft", "Soft or spongy", listOf("brake_soft_pedal")),
                AnswerOption("unsure", "Unsure")
            ),
            showIf = ShowCondition(presentAny = setOf("brake_squeaking", "brake_grinding", "brake_vibration", "brake_reduced_performance"))
        ),
        Question(
            id = "q_brake_pull",
            prompt = "Does the vehicle pull to one side when braking?",
            helpText = "Pulling usually indicates uneven braking effort between wheels.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("no", "No", absentEvidenceIds = listOf("brake_pulling_side")),
                AnswerOption("yes", "Yes", listOf("brake_pulling_side")),
                AnswerOption("unsure", "Unsure")
            ),
            showIf = ShowCondition(presentAny = setOf("brake_squeaking", "brake_grinding", "brake_vibration", "brake_soft_pedal"))
        ),
        Question(
            id = "q_brake_smell",
            prompt = "Do you notice a burning smell while braking?",
            helpText = "A burning smell can mean overheating brakes or a seized caliper.",
            input = QuestionInput.SINGLE,
            options = listOf(
                AnswerOption("no", "No", absentEvidenceIds = listOf("brake_burning_smell")),
                AnswerOption("yes", "Yes", listOf("brake_burning_smell")),
                AnswerOption("unsure", "Unsure")
            ),
            showIf = ShowCondition(presentAny = setOf("brake_grinding", "brake_reduced_performance", "brake_pulling_side"))
        )
    )

    /** Category-specific questions asked when the matching category was selected. */
    val categoryQuestions: List<Question> = listOf(
        Question(
            id = "q_transmission_slip",
            prompt = "Does the engine rev without the vehicle accelerating?",
            helpText = "That sensation is usually described as the transmission slipping.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("transmission"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("transmission_slipping")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("transmission_slipping")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_transmission_delay",
            prompt = "Is there a delay when selecting drive or reverse?",
            helpText = "A pause of a second or more before the vehicle moves is worth reporting.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("transmission"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("delayed_engagement")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("delayed_engagement")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_transmission_shifts",
            prompt = "Are the gear changes harsh or jerky?",
            helpText = "Note whether this happens when cold, hot or all the time.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("transmission"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("hard_shifting")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("hard_shifting")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_transmission_fluid",
            prompt = "Any reddish fluid leaking under the middle of the vehicle?",
            helpText = "Transmission fluid is typically red or reddish-brown.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("transmission"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("transmission_fluid_leak")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("transmission_fluid_leak")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_fuel_economy",
            prompt = "Is fuel consumption higher than usual?",
            helpText = "Compare against your normal driving routine over a full tank.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("fuel"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("poor_fuel_economy")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("poor_fuel_economy")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_fuel_smell",
            prompt = "Do you smell fuel around the vehicle?",
            helpText = "A fuel smell is a safety concern, so report it even if it is intermittent.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("fuel"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("fuel_smell")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("fuel_smell")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_hesitation_load",
            prompt = "Does the vehicle hesitate when accelerating uphill or under load?",
            helpText = "Hesitation under load often points to a fuel or ignition delivery limit.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("fuel", "performance"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("hesitation_under_load")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("hesitation_under_load")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_exhaust_noise",
            prompt = "Is the exhaust noticeably louder than usual?",
            helpText = "A sudden increase can indicate a leak or a failed joint.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("exhaust"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("exhaust_noise_loud")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("exhaust_noise_loud")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_rotten_egg",
            prompt = "Is there a rotten-egg smell from the exhaust?",
            helpText = "That smell often indicates a catalytic converter or mixture problem.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("exhaust"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("rotten_egg_smell")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("rotten_egg_smell")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_steering_pull",
            prompt = "Does the vehicle pull to one side while driving straight?",
            helpText = "Check on a level road with correct tire pressures.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("suspension"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("steering_pull")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("steering_pull")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_steering_play",
            prompt = "Does the steering feel loose or vague?",
            helpText = "Excessive free play in the steering wheel is a safety-relevant finding.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("suspension"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("steering_loose")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("steering_loose")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_clunk",
            prompt = "Do you hear clunking over bumps?",
            helpText = "Clunks from the suspension are often worn bushes or links.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("suspension"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("clunk_over_bumps")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("clunk_over_bumps")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_vibration_speed",
            prompt = "Do you feel vibration at higher speeds?",
            helpText = "Vibration that grows with speed usually points at wheel balance or tires.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("suspension", "sounds"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("tire_vibration_high_speed", "steering_vibration")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("tire_vibration_high_speed", "steering_vibration")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_tire_wear",
            prompt = "Is the tire wear uneven across the tread?",
            helpText = "Uneven wear points at alignment, balance or suspension geometry.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("suspension"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("uneven_tire_wear")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("uneven_tire_wear")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_rough_idle",
            prompt = "Does the engine idle roughly or shake?",
            helpText = "A rough idle with the vehicle stationary is a classic misfire sign.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("engine", "performance"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("rough_idle")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("rough_idle")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_misfire",
            prompt = "Does it hesitate or stutter under acceleration?",
            helpText = "Describe whether it happens when cold, warm or all the time.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("engine", "performance"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("misfire_hesitation")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("misfire_hesitation")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_oil_level",
            prompt = "Is the engine oil level low?",
            helpText = "Check with the engine off and the vehicle on level ground.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("engine"),
            options = listOf(
                AnswerOption("low", "Low", listOf("oil_level_low")),
                AnswerOption("ok", "At the correct level", absentEvidenceIds = listOf("oil_level_low")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_oil_leak",
            prompt = "Are there oil spots or a visible oil leak?",
            helpText = "Look under the vehicle after it has been parked for a while.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("engine"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("oil_leak_visible")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("oil_leak_visible")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_belt_squeal",
            prompt = "Is there a squealing noise from the belt area?",
            helpText = "Often heard on cold start or when turning the steering wheel.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("engine", "sounds"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("belt_squeal")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("belt_squeal")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_sudden_power_loss",
            prompt = "Has the engine ever lost power suddenly while driving?",
            helpText = "This is safety-relevant information, so please report it accurately.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("performance"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("sudden_power_loss_driving")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("sudden_power_loss_driving")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_burning_smell",
            prompt = "Do you smell electrical burning?",
            helpText = "An electrical burning smell is safety-relevant and should never be ignored.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("electrical", "warning_lights"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("electrical_burning_smell")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("electrical_burning_smell")),
                AnswerOption("unsure", "Unsure")
            )
        ),
        Question(
            id = "q_engine_bay_smoke",
            prompt = "Have you seen smoke from the engine bay?",
            helpText = "Engine bay smoke is safety-relevant regardless of its source.",
            input = QuestionInput.SINGLE,
            categoryIds = listOf("engine", "cooling", "electrical", "warning_lights"),
            options = listOf(
                AnswerOption("yes", "Yes", listOf("smoke_from_engine_bay")),
                AnswerOption("no", "No", absentEvidenceIds = listOf("smoke_from_engine_bay")),
                AnswerOption("unsure", "Unsure")
            )
        )
    )

    val allQuestions: List<Question> = baseQuestions + categoryQuestions + followUpQuestions

    fun question(id: String): Question? = allQuestions.firstOrNull { it.id == id }
}
