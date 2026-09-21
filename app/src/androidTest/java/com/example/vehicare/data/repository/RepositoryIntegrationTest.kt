package com.example.vehicare.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.vehicare.data.local.database.VehiCareDatabase
import com.example.vehicare.data.seed.DemoDataSeeder
import com.example.vehicare.data.seed.SampleData
import com.example.vehicare.domain.diagnostic.bayesian.NaiveBayesDiagnosticEngine
import com.example.vehicare.domain.model.Assessment
import com.example.vehicare.domain.model.AssessmentStatus
import com.example.vehicare.domain.model.AssessmentType
import com.example.vehicare.domain.model.MaintenanceRecord
import com.example.vehicare.domain.model.MeasurementUnit
import com.example.vehicare.domain.model.Vehicle
import com.example.vehicare.domain.usecase.EvaluateSymptomsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end tests of the four Room-backed repositories against a real (in-memory) database:
 * seeding, drafts, completed results, safety alerts, trends and preferences.
 */
@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {

    private lateinit var database: VehiCareDatabase
    private lateinit var vehicleRepository: VehicleRepositoryImpl
    private lateinit var assessmentRepository: AssessmentRepositoryImpl
    private lateinit var maintenanceRepository: MaintenanceRepositoryImpl
    private lateinit var preferencesRepository: PreferencesRepositoryImpl
    private val engine = NaiveBayesDiagnosticEngine()
    private val evaluateSymptoms = EvaluateSymptomsUseCase(engine)

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VehiCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        assessmentRepository = AssessmentRepositoryImpl(
            database = database,
            assessmentDao = database.assessmentDao(),
            resultDao = database.assessmentResultDao(),
            alertDao = database.assessmentSafetyAlertDao(),
            vehicleDao = database.vehicleDao(),
            symptomDao = database.symptomDao(),
            hypothesisDao = database.diagnosticHypothesisDao(),
            ioDispatcher = Dispatchers.IO
        )
        vehicleRepository = VehicleRepositoryImpl(
            database = database,
            vehicleDao = database.vehicleDao(),
            assessmentDao = database.assessmentDao(),
            maintenanceRecordDao = database.maintenanceRecordDao(),
            demoDataSeeder = DemoDataSeeder(
                database = database,
                vehicleDao = database.vehicleDao(),
                assessmentRepository = assessmentRepository,
                evaluateSymptoms = evaluateSymptoms,
                ioDispatcher = Dispatchers.IO
            ),
            ioDispatcher = Dispatchers.IO
        )
        maintenanceRepository = MaintenanceRepositoryImpl(
            maintenanceRecordDao = database.maintenanceRecordDao(),
            vehicleDao = database.vehicleDao(),
            ioDispatcher = Dispatchers.IO
        )
        preferencesRepository = PreferencesRepositoryImpl(
            preferenceDao = database.preferenceDao(),
            ioDispatcher = Dispatchers.IO
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun assessmentWith(
        id: String = "",
        vehicleId: String,
        present: Set<String>,
        absent: Set<String> = emptySet(),
        answers: Map<String, String> = linkedMapOf("q_severity" to "3"),
        categories: List<String> = emptyList()
    ) = Assessment(
        id = id,
        vehicleId = vehicleId,
        assessmentTypeId = AssessmentType.SYMPTOM_BASED.id,
        selectedCategoryIds = categories,
        answers = answers,
        presentEvidenceIds = present,
        absentEvidenceIds = absent,
        reportedSeverity = 3,
        status = AssessmentStatus.DRAFT,
        startedAt = 1_000L
    )

    // --- seeding --------------------------------------------------------------------------------

    @Test
    fun seedingInsertsTheSampleOnlyIntoAnEmptyDatabase() = runBlocking {
        vehicleRepository.seedIfEmpty()

        val vehicles = vehicleRepository.observeVehicles().first()
        assertEquals(1, vehicles.size)
        assertEquals(Vehicle.SAMPLE_ID, vehicles.first().id)
        assertEquals("2020 Toyota Vios", vehicles.first().displayName)
        assertTrue(vehicles.first().isSample)

        val summaries = assessmentRepository.observeSummaries().first()
        assertEquals(1, summaries.size)
        val summary = summaries.first()
        assertEquals(SampleData.SAMPLE_ASSESSMENT_ID, summary.assessmentId)

        // The demo ranking is computed at seed time and persisted, exactly like a real session.
        val seededVehicle = vehicleRepository.getVehicle(Vehicle.SAMPLE_ID)!!
        val analysis = evaluateSymptoms(
            SampleData.sampleAssessment(seededVehicle.id, System.currentTimeMillis()),
            seededVehicle
        )
        val top = analysis.primaryIssue!!
        assertEquals(
            listOf("weak_battery", "faulty_alternator", "starter_motor_failure"),
            analysis.rankedIssues.take(3).map { it.hypothesisId }
        )
        assertEquals(top.name, summary.topIssueName)
        assertEquals(top.system.label, summary.primarySystem)
        assertEquals(analysis.rankedIssues.size, summary.issueCount)
        assertTrue(summary.issueCount > 0)
        assertEquals(top.posteriorProbability, summary.topProbability!!, 1e-12)
        assertEquals("Engine cranks slowly", summary.mainSymptomLabel)
        assertFalse(summary.hasSafetyAlerts)

        val seededResults = database.assessmentResultDao()
            .getForAssessment(SampleData.SAMPLE_ASSESSMENT_ID)
            .sortedBy { it.rank }
        assertEquals(analysis.rankedIssues.size, seededResults.size)
        assertEquals(top.hypothesisId, seededResults.first().hypothesisId)
        assertEquals(top.posteriorProbability, seededResults.first().posteriorProbability, 1e-12)
        assertEquals(top.severity.name, seededResults.first().severity)

        // Seeding again changes nothing.
        vehicleRepository.seedIfEmpty()
        assertEquals(1, vehicleRepository.countVehicles())
        assertEquals(1, assessmentRepository.counts().assessmentCount)
        assertEquals(1, assessmentRepository.observeSummaries().first().size)
        assertEquals(
            seededResults.size,
            database.assessmentResultDao().getForAssessment(SampleData.SAMPLE_ASSESSMENT_ID).size
        )
    }

    @Test
    fun seedingDoesNotRunOnceAVehicleExists() = runBlocking {
        vehicleRepository.save(Vehicle(id = "", make = "Honda", model = "City", year = 2019))

        vehicleRepository.seedIfEmpty()

        assertEquals(1, vehicleRepository.countVehicles())
        assertEquals(0, database.assessmentDao().countAll())
        assertEquals("Honda", vehicleRepository.observeVehicles().first().first().make)
    }

    // --- vehicles -------------------------------------------------------------------------------

    @Test
    fun vehicleSaveGeneratesIdsAndDeleteCascadesAssessments() = runBlocking {
        val vehicleId = vehicleRepository.save(Vehicle(id = "", make = "Toyota", model = "Vios", year = 2020))
        assertTrue(vehicleId.isNotBlank())

        val assessmentId = assessmentRepository.saveDraft(
            assessmentWith(vehicleId = vehicleId, present = linkedSetOf("slow_cranking", "dim_lights"))
        )
        assertEquals(1, vehicleRepository.assessmentCountFor(vehicleId))

        vehicleRepository.delete(vehicleId)

        assertNull(vehicleRepository.getVehicle(vehicleId))
        assertEquals(0, vehicleRepository.countVehicles())
        assertNull(assessmentRepository.getAssessment(assessmentId))
        assertEquals(0, database.assessmentDao().countAll())
    }

    @Test
    fun deleteAllRemovesVehiclesAssessmentsAndMaintenanceRecords() = runBlocking {
        vehicleRepository.seedIfEmpty()
        val vehicleId = SampleData.sampleVehicle(System.currentTimeMillis()).id
        maintenanceRepository.save(
            MaintenanceRecord(id = "", vehicleId = vehicleId, serviceType = "Oil change", serviceDate = 1L)
        )

        vehicleRepository.deleteAll()

        assertEquals(0, vehicleRepository.countVehicles())
        assertEquals(0, database.assessmentDao().countAll())
        assertTrue(maintenanceRepository.observeForVehicle(vehicleId).first().isEmpty())
    }

    // --- drafts and completion ------------------------------------------------------------------

    @Test
    fun draftIsResumedAndCompletionPersistsResults() = runBlocking {
        val vehicleId = vehicleRepository.save(Vehicle(id = "", make = "Toyota", model = "Vios", year = 2020))
        val vehicle = vehicleRepository.getVehicle(vehicleId)!!

        val draftId = assessmentRepository.saveDraft(
            assessmentWith(
                vehicleId = vehicleId,
                present = linkedSetOf("slow_cranking", "dim_lights"),
                answers = linkedMapOf("q_starting" to "slow_crank")
            )
        )
        val resumed = assessmentRepository.observeDraft().first()!!
        assertEquals(draftId, resumed.id)
        assertEquals(AssessmentStatus.DRAFT, resumed.status)
        assertNull(resumed.completedAt)
        assertEquals(linkedMapOf("q_starting" to "slow_crank"), resumed.answers)

        // Continuing the questionnaire keeps the same draft row and its original start time.
        val continued = resumed.copy(
            answers = resumed.answers + ("q_severity" to "3"),
            presentEvidenceIds = LinkedHashSet(resumed.presentEvidenceIds + "cranks_no_start"),
            absentEvidenceIds = linkedSetOf("clicking_on_start")
        )
        assertEquals(draftId, assessmentRepository.saveDraft(continued))
        val resumedAgain = assessmentRepository.observeDraft().first()!!
        assertEquals(resumed.startedAt, resumedAgain.startedAt)
        assertEquals(setOf("q_starting", "q_severity"), resumedAgain.answers.keys)
        assertEquals(
            linkedSetOf("slow_cranking", "dim_lights", "cranks_no_start"),
            resumedAgain.presentEvidenceIds
        )

        val analysis = evaluateSymptoms(resumedAgain, vehicle)
        assertTrue("demo evidence must be sufficient for a ranking", analysis.isSufficient)
        assertEquals(draftId, assessmentRepository.complete(resumedAgain, analysis))

        // The draft is gone and the completed assessment carries the engine's versions.
        assertNull(assessmentRepository.observeDraft().first())
        val stored = assessmentRepository.getAssessment(draftId)!!
        assertEquals(AssessmentStatus.COMPLETED, stored.status)
        assertNotNull(stored.completedAt)
        assertEquals(analysis.engineVersion, stored.engineVersion)
        assertEquals(analysis.knowledgeBaseVersion, stored.knowledgeBaseVersion)
        assertEquals(analysis.reportedEvidence.map { it.id }.toSet(), stored.presentEvidenceIds)

        // Every ranked issue is persisted with its rank, probability, severity and evidence ids.
        val results = database.assessmentResultDao().getForAssessment(draftId).sortedBy { it.rank }
        assertEquals(analysis.rankedIssues.size, results.size)
        assertEquals((1..analysis.rankedIssues.size).toList(), results.map { it.rank })
        val top = analysis.primaryIssue!!
        val storedTop = results.first()
        assertEquals(top.hypothesisId, storedTop.hypothesisId)
        assertEquals(top.posteriorProbability, storedTop.posteriorProbability, 1e-12)
        assertEquals(top.severity.name, storedTop.severity)
        assertEquals(top.supportingEvidence.map { it.id }, storedTop.supportingEvidenceIds)
        assertEquals(top.missingEvidence.map { it.id }, storedTop.missingEvidenceIds)
        assertEquals(top.contradictingEvidence.map { it.id }, storedTop.contradictingEvidenceIds)
        assertEquals(0, database.assessmentSafetyAlertDao().getForAssessment(draftId).size)

        // The history row is built from the rank-1 result and the persisted vehicle.
        val summary = assessmentRepository.observeSummaries().first().single()
        assertEquals(top.name, summary.topIssueName)
        assertEquals(top.system.label, summary.primarySystem)
        assertEquals(top.posteriorProbability, summary.topProbability!!, 1e-12)
        assertEquals(analysis.rankedIssues.size, summary.issueCount)
        assertEquals("2020 Toyota Vios", summary.vehicleName)
        assertFalse(summary.hasSafetyAlerts)
        assertEquals("Engine cranks slowly", summary.mainSymptomLabel)

        // The knowledge-base catalogue snapshot was refreshed so reports keep their wording.
        assertTrue(database.symptomDao().count() > 0)
        assertTrue(database.diagnosticHypothesisDao().count() > 0)

        val counts = assessmentRepository.counts()
        assertEquals(1, counts.vehicleCount)
        assertEquals(1, counts.assessmentCount)
        assertEquals(0, counts.draftCount)
        assertEquals(1, counts.openConcernCount)
        assertEquals(0, counts.resolvedCount)
    }

    @Test
    fun safetyAlertsArePersistedIndependentlyOfTheRanking() = runBlocking {
        val vehicleId = vehicleRepository.save(Vehicle(id = "", make = "Toyota", model = "Vios", year = 2020))
        val vehicle = vehicleRepository.getVehicle(vehicleId)!!

        val assessment = assessmentWith(
            vehicleId = vehicleId,
            present = linkedSetOf("fuel_leak_visible", "fuel_smell", "oil_pressure_warning"),
            answers = linkedMapOf("q_severity" to "5")
        )
        val draftId = assessmentRepository.saveDraft(assessment)
        val analysis = evaluateSymptoms(assessment, vehicle)
        assertTrue("a fuel leak and an oil-pressure warning must raise alerts", analysis.hasSafetyAlerts)

        assessmentRepository.complete(assessment.copy(id = draftId), analysis)

        val alerts = database.assessmentSafetyAlertDao().getForAssessment(draftId)
        assertEquals(analysis.safetyAlerts.size, alerts.size)
        assertEquals(analysis.safetyAlerts.map { it.id }.toSet(), alerts.map { it.alertId }.toSet())
        assertTrue(alerts.all { it.level == "URGENT" })
        assertEquals(
            analysis.safetyAlerts.first { it.id == "fuel_leak" }.message,
            alerts.first { it.alertId == "fuel_leak" }.message
        )

        val summary = assessmentRepository.observeSummaries().first().single()
        assertTrue(summary.hasSafetyAlerts)
    }

    // --- resolution, deletion and trends ---------------------------------------------------------

    @Test
    fun resolvingAndDeletingAnAssessmentUpdatesHistoryAndCounts() = runBlocking {
        val vehicleId = vehicleRepository.save(Vehicle(id = "", make = "Toyota", model = "Vios", year = 2020))
        val vehicle = vehicleRepository.getVehicle(vehicleId)!!
        val assessment = assessmentWith(
            vehicleId = vehicleId,
            present = linkedSetOf("slow_cranking", "dim_lights", "cranks_no_start")
        )
        val assessmentId = assessmentRepository.saveDraft(assessment)
        assessmentRepository.complete(
            assessment.copy(id = assessmentId),
            evaluateSymptoms(assessment, vehicle)
        )
        assertTrue(database.assessmentResultDao().getForAssessment(assessmentId).isNotEmpty())

        assessmentRepository.setResolved(assessmentId, true)
        assertTrue(assessmentRepository.observeSummaries().first().single().resolved)
        assertEquals(1, assessmentRepository.counts().resolvedCount)
        assertEquals(0, assessmentRepository.counts().openConcernCount)
        assertEquals(assessmentId, assessmentRepository.latestSummaryForVehicle(vehicleId)?.assessmentId)

        assessmentRepository.setResolved(assessmentId, false)
        assertFalse(assessmentRepository.observeSummaries().first().single().resolved)

        assessmentRepository.delete(assessmentId)
        assertTrue(assessmentRepository.observeSummaries().first().isEmpty())
        assertEquals(0, database.assessmentResultDao().getForAssessment(assessmentId).size)
        assertNull(assessmentRepository.getAssessment(assessmentId))
    }

    @Test
    fun trendsAggregateCompletedAssessments() = runBlocking {
        val vehicleId = vehicleRepository.save(Vehicle(id = "", make = "Toyota", model = "Vios", year = 2020))
        val vehicle = vehicleRepository.getVehicle(vehicleId)!!

        val first = assessmentWith(
            vehicleId = vehicleId,
            present = linkedSetOf("slow_cranking", "dim_lights", "cranks_no_start")
        )
        val firstId = assessmentRepository.saveDraft(first)
        val firstAnalysis = evaluateSymptoms(first, vehicle)
        assertTrue(firstAnalysis.isSufficient)
        assessmentRepository.complete(first.copy(id = firstId), firstAnalysis)

        val second = assessmentWith(
            vehicleId = vehicleId,
            present = linkedSetOf("slow_cranking", "dim_lights", "cranks_no_start", "warning_battery_light")
        )
        val secondId = assessmentRepository.saveDraft(second)
        val secondAnalysis = evaluateSymptoms(second, vehicle)
        assertTrue(secondAnalysis.isSufficient)
        assessmentRepository.complete(second.copy(id = secondId), secondAnalysis)

        val trends = assessmentRepository.observeTrends().first()

        assertTrue(trends.hasData)
        assertEquals(2, trends.totalAssessments)
        assertEquals(2, trends.unresolvedCount)
        assertEquals(0, trends.resolvedCount)
        assertEquals(2, trends.assessmentsOverTime.sumOf { it.value })
        assertEquals(2, trends.probabilityTrend.size)
        assertTrue(trends.probabilityTrend.all { it.probability in 0.0..1.0 })
        assertTrue(trends.symptomsBySystem.any { it.count >= 2 })
        assertNotNull(trends.mostReportedSystem)
        assertTrue(trends.repeatedSymptoms.any { it.label == "Engine cranks slowly" && it.occurrences == 2 })

        // Trends stay empty (and never crash) once the completed assessments are gone. Deleting all
        // data also clears the cached knowledge-base catalogue.
        assessmentRepository.deleteAll()
        assertFalse(assessmentRepository.observeTrends().first().hasData)
        assertEquals(0, database.symptomDao().count())
        assertEquals(0, database.diagnosticHypothesisDao().count())
        assertNotNull(vehicleRepository.getVehicle(vehicleId))
    }
}
