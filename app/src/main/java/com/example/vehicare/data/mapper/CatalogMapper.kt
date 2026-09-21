package com.example.vehicare.data.mapper

import com.example.vehicare.data.local.entities.DiagnosticHypothesisEntity
import com.example.vehicare.data.local.entities.SymptomEntity
import com.example.vehicare.domain.diagnostic.knowledgebase.Hypothesis
import com.example.vehicare.domain.diagnostic.models.Evidence
import com.example.vehicare.domain.diagnostic.models.SymptomCategory

/**
 * Snapshots of the knowledge base catalogue.
 *
 * The knowledge base itself stays in `domain` and remains authoritative while an id is present. These
 * rows keep the wording a report was written with, so a later knowledge-base revision cannot silently
 * rename the symptoms or issues of an old assessment.
 */

fun Evidence.toSymptomEntity(now: Long): SymptomEntity = SymptomEntity(
    id = id,
    label = label,
    systemId = system.id,
    categoryId = SymptomCategory.all.firstOrNull { it.system == system }?.id,
    safetyRelevant = safetyRelevant,
    updatedAt = now
)

fun Hypothesis.toHypothesisEntity(now: Long): DiagnosticHypothesisEntity =
    DiagnosticHypothesisEntity(
        id = id,
        name = name,
        systemId = system.id,
        severity = severity.name,
        description = description,
        serviceCategory = recommendedServiceCategory,
        updatedAt = now
    )
