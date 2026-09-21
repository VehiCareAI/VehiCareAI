package com.example.vehicare.utils

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.vehicare.domain.diagnostic.models.DiagnosticAnalysis
import com.example.vehicare.ui.components.PRELIMINARY_DISCLAIMER
import com.example.vehicare.ui.components.RELATIVE_ESTIMATE_NOTE
import com.example.vehicare.ui.components.REPORT_DISCLAIMER
import java.io.File
import java.io.FileOutputStream

/**
 * Builds the assessment report PDF using only the platform [PdfDocument] API - no third-party
 * dependency (Section 2 / 5.14). The file is written into the app cache so it can be shared without
 * requesting storage permissions.
 */
class PdfReportExporter(private val context: Context) {

    fun export(
        assessmentId: String,
        vehicleName: String,
        generatedAt: Long,
        analysis: DiagnosticAnalysis,
        settingsNote: String
    ): File {
        return withDocument { document ->
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN

        val titlePaint = Paint().apply {
            color = android.graphics.Color.parseColor("#0B2545")
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val headingPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#123A6B")
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#141A22")
            textSize = 11f
            isAntiAlias = true
        }
        val mutedPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#5A6577")
            textSize = 10f
            isAntiAlias = true
        }
        val warningPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#B3261E")
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }

        fun newPageIfNeeded(required: Float) {
            if (y + required > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber += 1
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
            }
        }

        fun writeLine(text: String, paint: Paint, spacing: Float = 6f) {
            val lines = wrap(text, paint, PAGE_WIDTH - MARGIN * 2)
            lines.forEach { line ->
                newPageIfNeeded(paint.textSize + spacing)
                canvas.drawText(line, MARGIN, y, paint)
                y += paint.textSize + spacing
            }
        }

        writeLine("VehiCare AI", titlePaint)
        writeLine("Intelligent Vehicle Health Assessment", mutedPaint)
        y += 8f
        writeLine("Assessment report", headingPaint)
        writeLine("Vehicle: $vehicleName", bodyPaint)
        writeLine("Report ID: ${Formats.reportId(assessmentId)}", bodyPaint)
        writeLine("Generated: ${Formats.dateTime(generatedAt)}", bodyPaint)
        writeLine("Status: Preliminary assessment", bodyPaint)
        writeLine(settingsNote, mutedPaint)
        y += 8f

        writeLine("Reported symptoms", headingPaint)
        if (analysis.reportedEvidence.isEmpty()) {
            writeLine("No symptoms were recorded.", bodyPaint)
        } else {
            analysis.reportedEvidence.forEach { writeLine("- ${it.label}", bodyPaint) }
        }
        if (analysis.absentEvidence.isNotEmpty()) {
            writeLine("Explicitly reported as not present: " + analysis.absentEvidence.joinToString(", ") { it.label }, mutedPaint)
        }
        y += 8f

        if (analysis.hasSafetyAlerts) {
            writeLine("Safety alerts", headingPaint)
            analysis.safetyAlerts.forEach { alert ->
                writeLine("${alert.level.label}: ${alert.title}", warningPaint)
                writeLine(alert.message, bodyPaint)
            }
            y += 8f
        }

        if (!analysis.isSufficient) {
            writeLine("Sufficient evidence was not available", headingPaint)
            writeLine(
                "Unable to identify a sufficiently supported possible issue from the provided information. " +
                    "Consider adding more symptom details or consulting a qualified mechanic.",
                bodyPaint
            )
        } else {
            writeLine("Ranked possible issues", headingPaint)
            analysis.rankedIssues.forEach { issue ->
                writeLine("${issue.rank}. ${issue.name} - ${Formats.percent(issue.posteriorProbability)} estimate", bodyPaint)
                writeLine("System: ${issue.system.label} | Severity: ${issue.severity.label}", mutedPaint)
                writeLine(
                    "Evidence strength: ${issue.matchedEvidenceCount} of " +
                        "${issue.consideredEvidenceCount} considered findings support this",
                    mutedPaint
                )
                writeLine(issue.summary, bodyPaint)
                writeLine(issue.explanation, bodyPaint)
                if (issue.supportingEvidence.isNotEmpty()) {
                    writeLine("Supporting: " + issue.supportingEvidence.joinToString(", ") { it.label }, mutedPaint)
                }
                if (issue.contradictingEvidence.isNotEmpty()) {
                    writeLine("Contradicting: " + issue.contradictingEvidence.joinToString(", ") { it.label }, mutedPaint)
                }
                if (issue.missingEvidence.isNotEmpty()) {
                    writeLine("Not provided: " + issue.missingEvidence.joinToString(", ") { it.label }, mutedPaint)
                }
                if (issue.recommendedChecks.isNotEmpty()) {
                    writeLine("Recommended checks:", mutedPaint)
                    issue.recommendedChecks.forEach { check -> writeLine("- $check", mutedPaint) }
                }
                issue.safetyWarnings.forEach { warning -> writeLine(warning, warningPaint) }
                y += 4f
            }
            writeLine(RELATIVE_ESTIMATE_NOTE, mutedPaint)
        }

        y += 8f
        writeLine("Next steps", headingPaint)
        writeLine(
            "Have the recommended checks carried out by a qualified professional, and keep this report for " +
                "your service records.",
            bodyPaint
        )
        y += 8f
        writeLine(PRELIMINARY_DISCLAIMER, bodyPaint)
        writeLine(REPORT_DISCLAIMER, mutedPaint)

        document.finishPage(page)

        val directory = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(directory, "${Formats.reportId(assessmentId)}.pdf")
        FileOutputStream(file).use { output -> document.writeTo(output) }
        file
        }
    }

    /**
     * Runs [block] against a [PdfDocument] and always closes it, so a drawing or write failure can
     * never leak the native document.
     */
    private fun <T> withDocument(block: (PdfDocument) -> T): T {
        val document = PdfDocument()
        return try {
            block(document)
        } finally {
            document.close()
        }
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            val words = paragraph.split(" ").filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                lines += ""
                return@forEach
            }
            var current = StringBuilder()
            words.forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                when {
                    paint.measureText(candidate) <= maxWidth -> current = StringBuilder(candidate)
                    // A single word wider than the line is emitted as-is; never an empty line.
                    current.isEmpty() -> lines += word
                    else -> {
                        lines += current.toString()
                        current = StringBuilder(word)
                    }
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
        }
        return lines
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
    }
}
