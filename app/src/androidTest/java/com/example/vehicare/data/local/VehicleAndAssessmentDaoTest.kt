package com.example.vehicare.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.vehicare.data.local.database.VehiCareDatabase
import com.example.vehicare.data.local.entities.AssessmentEntity
import com.example.vehicare.data.local.entities.AssessmentResultEntity
import com.example.vehicare.data.local.entities.AssessmentSafetyAlertEntity
import com.example.vehicare.data.local.entities.MaintenanceRecordEntity
import com.example.vehicare.data.local.entities.VehicleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * In-memory Room tests for the vehicle/assessment tables: CRUD, draft round trips, the vehicle ->
 * assessment foreign key and the `CASCADE` behaviour that Section 8 requires.
 */
@RunWith(AndroidJUnit4::class)
class VehicleAndAssessmentDaoTest {

    private lateinit var database: VehiCareDatabase

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VehiCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun vehicle(id: String = "v1", make: String = "Toyota", model: String = "Vios") =
        VehicleEntity(
            id = id,
            nickname = "",
            make = make,
            model = model,
            year = 2020,
            vehicleType = "Sedan",
            fuelType = "Gasoline",
            transmission = "Automatic",
            engineDisplacement = "",
            mileageKm = 68_500,
            licensePlate = "SAMPLE",
            vin = "",
            notes = "",
            isSample = true,
            createdAt = 1_000L,
            updatedAt = 1_000L
        )

    private fun assessment(
        id: String = "a1",
        vehicleId: String = "v1",
        status: String = "completed",
        completedAt: Long? = 5_000L,
        present: Set<String> = linkedSetOf("slow_cranking", "dim_lights"),
        answers: Map<String, String> = linkedMapOf("q_starting" to "slow_crank")
    ) = AssessmentEntity(
        id = id,
        vehicleId = vehicleId,
        assessmentTypeId = "symptom_based",
        selectedCategoryIds = listOf("starting", "electrical"),
        answers = answers,
        presentEvidenceIds = present,
        absentEvidenceIds = linkedSetOf("clicking_on_start"),
        reportedSeverity = 3,
        onsetId = "within_week",
        status = status,
        engineVersion = "engine-1",
        knowledgeBaseVersion = "kb-1",
        disclaimerAccepted = true,
        resolved = false,
        startedAt = 1_000L,
        completedAt = completedAt
    )

    private fun resultRow(assessmentId: String = "a1") = AssessmentResultEntity(
        id = "$assessmentId#1",
        assessmentId = assessmentId,
        hypothesisId = "weak_battery",
        rank = 1,
        posteriorProbability = 0.4,
        severity = "HIGH",
        supportingEvidenceIds = listOf("slow_cranking"),
        missingEvidenceIds = emptyList(),
        contradictingEvidenceIds = emptyList(),
        generatedAt = 5_000L
    )

    private fun alertRow(assessmentId: String = "a1") = AssessmentSafetyAlertEntity(
        assessmentId = assessmentId,
        alertId = "engine_knock",
        title = "Abnormal engine noise",
        message = "Consider limiting driving until the source is identified.",
        level = "ADVISORY"
    )

    private fun maintenanceRow(vehicleId: String = "v1") = MaintenanceRecordEntity(
        id = "m1",
        vehicleId = vehicleId,
        serviceType = "Oil change",
        description = "",
        serviceDate = 900L,
        mileageKm = null,
        cost = null,
        notes = ""
    )

    @Test
    fun vehicleCrudRoundTrip() = runBlocking {
        val dao = database.vehicleDao()
        dao.upsert(vehicle())
        assertEquals(1, dao.count())
        assertNotNull(dao.getById("v1"))
        assertEquals("Toyota", dao.observeById("v1").first()?.make)

        dao.upsert(vehicle(make = "Toyota", model = "Corolla"))
        assertEquals(1, dao.count())
        assertEquals("Corolla", dao.getById("v1")?.model)
        assertEquals(listOf("v1"), dao.observeAll().first().map { it.id })

        dao.deleteById("v1")
        assertEquals(0, dao.count())
        assertNull(dao.observeById("v1").first())
    }

    @Test
    fun draftSurvivesAndResumesWithItsAnswers() = runBlocking {
        val dao = database.assessmentDao()
        database.vehicleDao().upsert(vehicle())
        dao.upsert(assessment(id = "draft1", status = "draft", completedAt = null))

        val drafts = dao.observeLatestDraft("draft")
        val loaded = drafts.first()
        assertNotNull(loaded)
        assertEquals(linkedMapOf("q_starting" to "slow_crank"), loaded?.answers)
        assertEquals(linkedSetOf("slow_cranking", "dim_lights"), loaded?.presentEvidenceIds)
        assertEquals(linkedSetOf("clicking_on_start"), loaded?.absentEvidenceIds)
        assertEquals("within_week", loaded?.onsetId)
        assertEquals(3, loaded?.reportedSeverity)
        assertEquals(listOf("starting", "electrical"), loaded?.selectedCategoryIds)

        // Completing the draft removes it from the resumable set without losing the row.
        dao.upsert(assessment(id = "draft1", status = "completed"))
        assertNull(drafts.first())
        assertEquals(1, dao.countAll())
        assertEquals(1, dao.countByStatus("completed"))
        assertEquals(0, dao.countDrafts("draft"))
    }

    @Test
    fun deletingVehicleCascadesToAssessmentsResultsAndAlerts() = runBlocking {
        database.vehicleDao().upsert(vehicle())
        database.assessmentDao().upsert(assessment())
        database.assessmentResultDao().upsertAll(listOf(resultRow()))
        database.assessmentSafetyAlertDao().upsertAll(listOf(alertRow()))
        database.maintenanceRecordDao().upsert(maintenanceRow())

        assertEquals(1, database.assessmentDao().countAll())
        assertEquals(1, database.assessmentResultDao().getForAssessment("a1").size)
        assertEquals(1, database.assessmentSafetyAlertDao().getForAssessment("a1").size)

        // The repository deletes children explicitly, then the vehicle; results and alerts are only
        // linked to the assessment, so they must disappear through the FK CASCADE.
        database.assessmentDao().deleteForVehicle("v1")
        database.maintenanceRecordDao().deleteForVehicle("v1")
        database.vehicleDao().deleteById("v1")

        assertEquals(0, database.vehicleDao().count())
        assertEquals(0, database.assessmentDao().countAll())
        assertEquals(0, database.assessmentResultDao().getForAssessment("a1").size)
        assertEquals(0, database.assessmentSafetyAlertDao().getForAssessment("a1").size)
        assertTrue(database.maintenanceRecordDao().observeForVehicle("v1").first().isEmpty())
    }

    @Test
    fun deletingAssessmentDirectlyAlsoRemovesItsResultsAndAlerts() = runBlocking {
        database.vehicleDao().upsert(vehicle())
        database.assessmentDao().upsert(assessment())
        database.assessmentResultDao().upsertAll(listOf(resultRow()))
        database.assessmentSafetyAlertDao().upsertAll(listOf(alertRow()))

        database.assessmentDao().deleteById("a1")

        assertEquals(0, database.assessmentResultDao().getForAssessment("a1").size)
        assertEquals(0, database.assessmentSafetyAlertDao().getForAssessment("a1").size)
        assertEquals(1, database.vehicleDao().count())
    }

    @Test
    fun assessmentNeedsAnExistingVehicle() {
        val failure = runCatching { runBlocking { database.assessmentDao().upsert(assessment()) } }
        assertTrue("an orphan assessment must be rejected", failure.isFailure)
    }
}
