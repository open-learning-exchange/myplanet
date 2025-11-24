package org.ole.planet.myplanet.utilities

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import io.realm.Realm
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

object SubmissionPdfGenerator {

    private const val PAGE_WIDTH = 595 // A4 width in points
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 50f
    private const val LINE_HEIGHT = 20f

    fun generateSubmissionPdf(
        context: Context,
        submission: RealmSubmission,
        realm: Realm
    ): File? {
        return try {
            val document = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var yPosition = MARGIN

            val titlePaint = Paint().apply {
                textSize = 20f
                isFakeBoldText = true
            }
            val headerPaint = Paint().apply {
                textSize = 16f
                isFakeBoldText = true
            }
            val normalPaint = Paint().apply {
                textSize = 12f
            }

            // Get exam details
            val examId = getExamId(submission.parentId)
            val exam = realm.where(RealmStepExam::class.java)
                .equalTo("id", examId)
                .findFirst()

            // Title
            canvas.drawText(exam?.name ?: "Submission Report", MARGIN, yPosition, titlePaint)
            yPosition += LINE_HEIGHT * 2

            // Submission metadata
            canvas.drawText("Status: ${submission.status}", MARGIN, yPosition, normalPaint)
            yPosition += LINE_HEIGHT
            canvas.drawText("Date: ${TimeUtils.getFormattedDateWithTime(submission.lastUpdateTime)}", MARGIN, yPosition, normalPaint)
            yPosition += LINE_HEIGHT * 2

            // Get questions and answers
            val questions = realm.where(RealmExamQuestion::class.java)
                .equalTo("examId", examId)
                .findAll()

            questions.forEachIndexed { index, question ->
                // Check if we need a new page
                if (yPosition > PAGE_HEIGHT - MARGIN - 100) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    yPosition = MARGIN
                }

                // Question
                val questionText = "Q${index + 1}: ${question.body ?: ""}"
                yPosition = drawMultilineText(canvas, questionText, MARGIN, yPosition, headerPaint, PAGE_WIDTH - (2 * MARGIN))
                yPosition += LINE_HEIGHT / 2

                // Answer
                val answer = submission.answers?.find { it.questionId == question.id }
                val answerText = formatAnswer(answer, question)
                canvas.drawText("A: $answerText", MARGIN + 20, yPosition, normalPaint)
                yPosition += LINE_HEIGHT * 2
            }

            document.finishPage(page)

            // Save to file
            val fileName = "submission_${submission.id}_${System.currentTimeMillis()}.pdf"
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Submissions")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, fileName)
            val outputStream = FileOutputStream(file)
            document.writeTo(outputStream)
            document.close()
            outputStream.close()

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun generateMultipleSubmissionsPdf(
        context: Context,
        submissions: List<RealmSubmission>,
        examTitle: String,
        realm: Realm
    ): File? {
        return try {
            val document = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var yPosition = MARGIN

            val titlePaint = Paint().apply {
                textSize = 20f
                isFakeBoldText = true
            }
            val headerPaint = Paint().apply {
                textSize = 16f
                isFakeBoldText = true
            }
            val subHeaderPaint = Paint().apply {
                textSize = 14f
                isFakeBoldText = true
            }
            val normalPaint = Paint().apply {
                textSize = 12f
            }

            // Title
            canvas.drawText("$examTitle - All Submissions", MARGIN, yPosition, titlePaint)
            yPosition += LINE_HEIGHT * 2

            // Summary
            canvas.drawText("Total Submissions: ${submissions.size}", MARGIN, yPosition, normalPaint)
            yPosition += LINE_HEIGHT
            canvas.drawText("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}", MARGIN, yPosition, normalPaint)
            yPosition += LINE_HEIGHT * 3

            // Get questions once (they're the same for all submissions)
            val examId = getExamId(submissions.firstOrNull()?.parentId)
            val questions = realm.where(RealmExamQuestion::class.java)
                .equalTo("examId", examId)
                .findAll()

            // List each submission with full details
            submissions.forEachIndexed { submissionIndex, submission ->
                // Check if we need a new page for submission header
                if (yPosition > PAGE_HEIGHT - MARGIN - 100) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    yPosition = MARGIN
                }

                // Submission header
                canvas.drawText("Submission #${submissionIndex + 1}", MARGIN, yPosition, subHeaderPaint)
                yPosition += LINE_HEIGHT * 1.5f
                canvas.drawText("Date: ${TimeUtils.getFormattedDateWithTime(submission.lastUpdateTime)}", MARGIN + 20, yPosition, normalPaint)
                yPosition += LINE_HEIGHT
                canvas.drawText("Status: ${submission.status}", MARGIN + 20, yPosition, normalPaint)
                yPosition += LINE_HEIGHT * 2

                // Questions and answers for this submission
                questions.forEachIndexed { index, question ->
                    // Check if we need a new page
                    if (yPosition > PAGE_HEIGHT - MARGIN - 100) {
                        document.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        yPosition = MARGIN
                    }

                    // Question
                    val questionText = "Q${index + 1}: ${question.body ?: ""}"
                    yPosition = drawMultilineText(canvas, questionText, MARGIN + 20, yPosition, normalPaint, PAGE_WIDTH - (2 * MARGIN) - 20)
                    yPosition += LINE_HEIGHT / 2

                    // Answer
                    val answer = submission.answers?.find { it.questionId == question.id }
                    val answerText = formatAnswer(answer, question)
                    yPosition = drawMultilineText(canvas, "A: $answerText", MARGIN + 40, yPosition, normalPaint, PAGE_WIDTH - (2 * MARGIN) - 40)
                    yPosition += LINE_HEIGHT * 1.5f
                }

                // Add extra space between submissions
                yPosition += LINE_HEIGHT * 2
            }

            document.finishPage(page)

            // Save to file
            val fileName = "submissions_report_${System.currentTimeMillis()}.pdf"
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Submissions")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, fileName)
            val outputStream = FileOutputStream(file)
            document.writeTo(outputStream)
            document.close()
            outputStream.close()

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun drawMultilineText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, maxWidth: Float): Float {
        var currentY = y
        val words = text.split(" ")
        var line = ""

        words.forEach { word ->
            val testLine = if (line.isEmpty()) word else "$line $word"
            val width = paint.measureText(testLine)

            if (width > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, x, currentY, paint)
                currentY += LINE_HEIGHT
                line = word
            } else {
                line = testLine
            }
        }

        if (line.isNotEmpty()) {
            canvas.drawText(line, x, currentY, paint)
            currentY += LINE_HEIGHT
        }

        return currentY
    }

    private fun formatAnswer(answer: org.ole.planet.myplanet.model.RealmAnswer?, question: RealmExamQuestion): String {
        return when {
            answer == null -> "No answer provided"
            !answer.value.isNullOrEmpty() -> answer.value!!
            answer.valueChoices != null && answer.valueChoices!!.isNotEmpty() -> {
                answer.valueChoices!!.joinToString(", ") { choice ->
                    try {
                        val choiceObj = org.json.JSONObject(choice)
                        choiceObj.optString("text", choice)
                    } catch (e: Exception) {
                        choice
                    }
                }
            }
            else -> "No answer provided"
        }
    }

    private fun getExamId(parentId: String?): String? {
        return if (parentId?.contains("@") == true) {
            parentId.split("@")[0]
        } else {
            parentId
        }
    }
}
