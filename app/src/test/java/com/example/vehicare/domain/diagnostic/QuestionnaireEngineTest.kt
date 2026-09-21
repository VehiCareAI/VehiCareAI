package com.example.vehicare.domain.diagnostic

import com.example.vehicare.domain.diagnostic.knowledgebase.QuestionBank
import com.example.vehicare.domain.usecase.QuestionnaireEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Questionnaire behaviour: evidence mapping, dynamic follow-ups and progress (Section 11). */
class QuestionnaireEngineTest {

    private val engine = QuestionnaireEngine()

    @Test
    fun `question bank covers the specification base questions and follow-ups`() {
        assertTrue(QuestionBank.baseQuestions.size >= 10)
        assertTrue(QuestionBank.followUpQuestions.size >= 5)
        assertTrue(QuestionBank.categoryQuestions.size >= 8)
        assertEquals("q_engine_noise", QuestionBank.baseQuestions.first().id)
        assertTrue(QuestionBank.allQuestions.all { it.prompt.isNotBlank() && it.helpText.isNotBlank() })
    }

    @Test
    fun `positive answers produce present evidence and negative answers produce absent evidence`() {
        val answers = mapOf(
            "q_starting" to "slow_crank",
            "q_electrical" to "no"
        )
        val present = engine.presentEvidence(answers)
        val absent = engine.absentEvidence(answers)

        assertTrue(present.contains("slow_cranking"))
        assertTrue(absent.contains("dim_lights"))
        assertFalse(present.contains("dim_lights"))
    }

    @Test
    fun `unsure answers contribute no evidence`() {
        val unsureOnly = mapOf("q_battery_age" to "unsure", "q_smoke" to "unsure")
        assertTrue(engine.presentEvidence(unsureOnly).isEmpty())
        assertTrue(engine.absentEvidence(unsureOnly).isEmpty())
    }

    @Test
    fun `multi select answers contribute every selected option`() {
        val answers = mapOf("q_warning_lights" to "check_engine,battery")
        val present = engine.presentEvidence(answers)
        assertTrue(present.containsAll(listOf("check_engine_light", "warning_battery_light")))
    }

    @Test
    fun `no lights option is negative evidence for every light`() {
        val absent = engine.absentEvidence(mapOf("q_warning_lights" to "none"))
        assertTrue(absent.contains("check_engine_light"))
        assertTrue(absent.contains("oil_pressure_warning"))
    }

    @Test
    fun `follow-up questions are hidden until their trigger is reported`() {
        val before = engine.visibleQuestions(emptyMap(), setOf("starting", "electrical"))
        assertFalse(before.any { it.id == "q_battery_age" })

        val after = engine.visibleQuestions(
            mapOf("q_starting" to "slow_crank"),
            setOf("starting", "electrical")
        )
        assertTrue(after.any { it.id == "q_battery_age" })
        assertTrue(after.any { it.id == "q_clicking_start" })
        assertTrue(after.any { it.id == "q_difficulty_starting" })
    }

    @Test
    fun `cooling follow-ups appear only after overheating is reported`() {
        val noOverheat = engine.visibleQuestions(emptyMap(), setOf("cooling"))
        assertFalse(noOverheat.any { it.id == "q_coolant_level" })

        val overheat = engine.visibleQuestions(
            mapOf("q_overheating" to "frequently"),
            setOf("cooling")
        )
        assertTrue(overheat.any { it.id == "q_coolant_level" })
        assertTrue(overheat.any { it.id == "q_radiator_fan" })
        assertTrue(overheat.any { it.id == "q_steam" })
    }

    @Test
    fun `braking follow-ups appear only after a braking finding is reported`() {
        val none = engine.visibleQuestions(mapOf("q_braking" to "none"), setOf("braking"))
        assertFalse(none.any { it.id == "q_brake_pedal" })

        val grinding = engine.visibleQuestions(mapOf("q_braking" to "grinding"), setOf("braking"))
        assertTrue(grinding.any { it.id == "q_brake_pedal" })
        assertTrue(grinding.any { it.id == "q_brake_smell" })
    }

    @Test
    fun `category questions only appear for selected categories`() {
        val transmissionOnly = engine.visibleQuestions(emptyMap(), setOf("transmission"))
        assertTrue(transmissionOnly.any { it.id == "q_transmission_slip" })
        assertFalse(transmissionOnly.any { it.id == "q_fuel_smell" })
    }

    @Test
    fun `question set stays within the manageable range for a typical session`() {
        val typical = engine.visibleQuestions(
            mapOf("q_starting" to "slow_crank", "q_electrical" to "occasionally"),
            setOf("starting", "electrical", "warning_lights")
        )
        assertTrue("expected 8..15 questions but got ${typical.size}", typical.size in 8..15)
    }

    @Test
    fun `progress counts answered versus visible questions`() {
        val answers = mapOf("q_starting" to "slow_crank", "q_engine_noise" to "none")
        val (answered, total) = engine.progress(answers, setOf("starting"))
        assertEquals(2, answered)
        assertTrue(total >= 10)
    }

    @Test
    fun `severity and onset are extracted and bounded`() {
        val answers = mapOf("q_severity" to "9", "q_onset" to "within_week")
        assertEquals(5, engine.reportedSeverity(answers))
        assertEquals("within_week", engine.onsetId(answers))
        assertEquals(null, engine.reportedSeverity(mapOf("q_severity" to "abc")))
    }

    @Test
    fun `evidence never contains the same id on both sides`() {
        val answers = mapOf("q_starting" to "slow_crank", "q_electrical" to "no")
        val present = engine.presentEvidence(answers)
        val absent = engine.absentEvidence(answers)
        assertTrue(present.intersect(absent).isEmpty())
    }
}
