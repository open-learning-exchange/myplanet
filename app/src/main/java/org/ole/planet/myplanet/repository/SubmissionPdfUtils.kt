package org.ole.planet.myplanet.repository

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

class SubmissionPdfUtils @Inject constructor(private val databaseService: DatabaseService) {

    private val page_width = 595
    private val page_height = 842
    private val margin = 50f
    private val line_height = 20f

    suspend fun generateSubmissionPdf(
        context: Context,
        submissionId: String,
    ): File? = databaseService.withRealmAsync { realm ->
        try {
            val submission = realm.where(RealmSubmission::class.java).equalTo("id", submissionId).findFirst()
                ?: return@withRealmAsync null

            val document = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(page_width, page_height, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var yPosition = margin

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

            val examId = getExamId(submission.parentId)
            val exam = realm.where(RealmStepExam::class.java)
                .equalTo("id", examId)
                .findFirst()

            canvas.drawText(exam?.name ?: "Submission Report", margin, yPosition, titlePaint)
            yPosition += line_height * 2

            canvas.drawText("Status: ${submission.status}", margin, yPosition, normalPaint)
            yPosition += line_height
            canvas.drawText("Date: ${getFormattedDateWithTime(submission.lastUpdateTime)}", margin, yPosition, normalPaint)
            yPosition += line_height * 2

            val questions = realm.where(RealmExamQuestion::class.java)
                .equalTo("examId", examId)
                .findAll()

            questions.forEachIndexed { index, question ->
                if (yPosition > page_height - margin - 100) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(page_width, page_height, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    yPosition = margin
                }

                val questionText = "Q${index + 1}: ${question.body ?: ""}"
                yPosition = drawMultilineText(canvas, questionText, margin, yPosition, headerPaint, page_width - (2 * margin))
                yPosition += line_height / 2

                val answer = submission.answers?.find { it.questionId == question.id }
                val answerText = formatAnswer(answer)
                canvas.drawText("A: $answerText", margin + 20, yPosition, normalPaint)
                yPosition += line_height * 2
            }

            document.finishPage(page)

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

    suspend fun generateMultipleSubmissionsPdf(
        context: Context,
        submissionIds: List<String>,
        examTitle: String,
    ): File? = databaseService.withRealmAsync { realm ->
        try {
            val submissions = submissionIds.mapNotNull { id ->
                realm.where(RealmSubmission::class.java).equalTo("id", id).findFirst()
            }

            if (submissions.isEmpty()) return@withRealmAsync null

            val document = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(page_width, page_height, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            var yPosition = margin

            val titlePaint = Paint().apply {
                textSize = 20f
                isFakeBoldText = true
            }
            val subHeaderPaint = Paint().apply {
                textSize = 14f
                isFakeBoldText = true
            }
            val normalPaint = Paint().apply {
                textSize = 12f
            }

            canvas.drawText("$examTitle - All Submissions", margin, yPosition, titlePaint)
            yPosition += line_height * 2

            canvas.drawText("Total Submissions: ${submissions.size}", margin, yPosition, normalPaint)
            yPosition += line_height
            canvas.drawText("Generated: ${getFormattedDateWithTime(Date().time)}", margin, yPosition, normalPaint)
            yPosition += line_height * 3

            val examId = getExamId(submissions.firstOrNull()?.parentId)
            val questions = realm.where(RealmExamQuestion::class.java)
                .equalTo("examId", examId)
                .findAll()

            submissions.forEachIndexed { submissionIndex, submission ->
                if (yPosition > page_height - margin - 100) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(page_width, page_height, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    yPosition = margin
                }

                canvas.drawText("Submission #${submissionIndex + 1}", margin, yPosition, subHeaderPaint)
                yPosition += line_height * 1.5f
                canvas.drawText("Date: ${getFormattedDateWithTime(submission.lastUpdateTime)}", margin + 20, yPosition, normalPaint)
                yPosition += line_height
                canvas.drawText("Status: ${submission.status}", margin + 20, yPosition, normalPaint)
                yPosition += line_height * 2

                questions.forEachIndexed { index, question ->
                    if (yPosition > page_height - margin - 100) {
                        document.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(page_width, page_height, pageNumber).create()
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        yPosition = margin
                    }

                    val questionText = "Q${index + 1}: ${question.body ?: ""}"
                    yPosition = drawMultilineText(canvas, questionText, margin + 20, yPosition, normalPaint, page_width - (2 * margin) - 20)
                    yPosition += line_height / 2

                    val answer = submission.answers?.find { it.questionId == question.id }
                    val answerText = formatAnswer(answer)
                    yPosition = drawMultilineText(canvas, "A: $answerText", margin + 40, yPosition, normalPaint, page_width - (2 * margin) - 40)
                    yPosition += line_height * 1.5f
                }

                yPosition += line_height * 2
            }

            document.finishPage(page)

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

    private fun getFormattedDateWithTime(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            ""
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
                currentY += line_height
                line = word
            } else {
                line = testLine
            }
        }

        if (line.isNotEmpty()) {
            canvas.drawText(line, x, currentY, paint)
            currentY += line_height
        }

        return currentY
    }

    private fun formatAnswer(answer: org.ole.planet.myplanet.model.RealmAnswer?): String {
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