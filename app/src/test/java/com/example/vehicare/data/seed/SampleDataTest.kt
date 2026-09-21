package com.example.vehicare.data.seed

import com.example.vehicare.domain.diagnostic.bayesian.NaiveBayesDiagnosticEngine
import com.example.vehicare.domain.model.AssessmentStatus
import com.example.vehicare.domain.model.AssessmentType
import com.example.vehicare.domain.model.FuelType
import com.example.vehicare.domain.model.Transmission
import com.example.vehicare.domain.model.Vehicle
import com.example.vehicare.domain.usecase.EvaluateSymptomsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Section 9 demo must be plausible: a real vehicle, real questionnaire answers and evidence that
 * the engine actually ranks into the documented battery/alternator/starter order. No probability is
 * asserted here (and none is hardcoded in the seed) — only the ordering the engine produces.
 */
class SampleDataTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `sample vehicle is the documented Toyota Vios`() {
        val vehicle = SampleData.sampleVehicle(now)

        assertEquals(Vehicle.SAMPLE_ID, vehicle.id)
        assertEquals("Toyota", vehicle.make)
        assertEquals("Vios", vehicle.model)
        assertEquals(2020, vehicle.year)
        assertEquals(FuelType.GASOLINE, vehicle.fuelType)
        assertEquals(Transmission.AUTOMATIC, vehicle.transmission)
        assertTrue(vehicle.isSample)
        assertEquals("2020 Toyota Vios", vehicle.displayName)
    }

    @Test
    fun `sample vehicle predates the sample assessment`() {
        val assessment = SampleData.sampleAssessment(Vehicle.SAMPLE_ID, now)
        assertEquals(now - 3L * 24 * 60 * 60 * 1000, SampleData.sampleVehicle(now).createdAt)
        assertTrue(assessment.startedAt < now)
        // The completion time, engine version and knowledge-base version come from the engine
        // analysis at seed time, so an uncompleted sample carries no completion timestamp.
        assertNull(assessment.completedAt)
    }

    @Test
    fun `sample assessment is a completed symptom based assessment`() {
        val assessment = SampleData.sampleAssessment(Vehicle.SAMPLE_ID, now)

        assertEquals(SampleData.SAMPLE_ASSESSMENT_ID, assessment.id)
        assertEquals(Vehicle.SAMPLE_ID, assessment.vehicleId)
        assertEquals(AssessmentType.SYMPTOM_BASED.id, assessment.assessmentTypeId)
        assertEquals(listOf("starting", "electrical", "warning_lights"), assessment.selectedCategoryIds)
        assertEquals(AssessmentStatus.COMPLETED, assessment.status)
        assertTrue(assessment.disclaimerAccepted)
        assertTrue(!assessment.resolved)
        assertEquals(3, assessment.reportedSeverity)
        assertEquals("within_week", assessment.onsetId)
        assertEquals(SampleData.SAMPLE_ANSWERS, assessment.answers)
        assertNull(assessment.completedAt)
    }

    @Test
    fun `sample answers map to the documented demo evidence`() {
        val assessment = SampleData.sampleAssessment(Vehicle.SAMPLE_ID, now)

        assertEquals(
            linkedSetOf("slow_cranking", "dim_lights", "cranks_no_start"),
            assessment.presentEvidenceIds
        )
        assertEquals(
            setOf(
                "clicking_on_start",
                "engine_clicking_ticking", "engine_knocking", "engine_grinding", "engine_whining",
                "loss_of_power_mild", "loss_of_power_significant", "intermittent_power_loss",
                "check_engine_light", "warning_battery_light", "oil_pressure_warning",
                "temperature_warning_light", "abs_light", "airbag_light",
                "brake_squeaking", "brake_grinding", "brake_vibration", "brake_soft_pedal",
                "brake_reduced_performance", "brake_pulling_side",
                "smoke_white", "smoke_blue", "smoke_black"
            ),
            assessment.absentEvidenceIds
        )
    }

    @Test
    fun `the seeded evidence ranks the documented battery, alternator and starter order`() {
        val vehicle = SampleData.sampleVehicle(now)
        val assessment = SampleData.sampleAssessment(vehicle.id, now)
        val useCase = EvaluateSymptomsUseCase(NaiveBayesDiagnosticEngine())

        val analysis = useCase(assessment, vehicle)

        assertTrue("the demo evidence must be sufficient for a ranking", analysis.isSufficient)
        assertEquals(
            listOf("weak_battery", "faulty_alternator", "starter_motor_failure"),
            analysis.rankedIssues.take(3).map { it.hypothesisId }
        )
        // Ranking is by posterior descending, and probabilities stay probabilities.
        assertTrue(analysis.rankedIssues.zipWithNext().all { (first, second) ->
            first.posteriorProbability >= second.posteriorProbability
        })
        assertTrue(analysis.rankedIssues.all { it.posteriorProbability in 0.0..1.0 })
    }
}
