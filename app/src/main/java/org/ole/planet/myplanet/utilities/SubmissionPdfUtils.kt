package org.ole.planet.myplanet.utilities

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
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission

object SubmissionPdfUtils {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 50f
    private const val LINE_HEIGHT = 20f

    suspend fun generateSubmissionPdf(
        context: Context,
        submissionId: String,
        databaseService: DatabaseService
    ): File? = databaseService.withRealmAsync { realm ->
        try {
            val submission = realm.where(RealmSubmission::class.java).equalTo("id", submissionId).findFirst()
                ?: return@withRealmAsync null

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

                val examId = getExamId(submission.parentId)
                val exam = realm.where(RealmStepExam::class.java)
                    .equalTo("id", examId)
                    .findFirst()

                canvas.drawText(exam?.name ?: "Submission Report", MARGIN, yPosition, titlePaint)
                yPosition += LINE_HEIGHT * 2

                canvas.drawText("Status: ${submission.status}", MARGIN, yPosition, normalPaint)
                yPosition += LINE_HEIGHT
                canvas.drawText("Date: ${TimeUtils.getFormattedDateWithTime(submission.lastUpdateTime)}", MARGIN, yPosition, normalPaint)
                yPosition += LINE_HEIGHT * 2

                val questions = realm.where(RealmExamQuestion::class.java)
                    .equalTo("examId", examId)
                    .findAll()

                questions.forEachIndexed { index, question ->
                    if (yPosition > PAGE_HEIGHT - MARGIN - 100) {
                        document.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        yPosition = MARGIN
                    }

                    val questionText = "Q${index + 1}: ${question.body ?: ""}"
                    yPosition = drawMultilineText(canvas, questionText, MARGIN, yPosition, headerPaint, PAGE_WIDTH - (2 * MARGIN))
                    yPosition += LINE_HEIGHT / 2

                    val answer = submission.answers?.find { it.questionId == question.id }
                    val answerText = formatAnswer(answer)
                    canvas.drawText("A: $answerText", MARGIN + 20, yPosition, normalPaint)
                    yPosition += LINE_HEIGHT * 2
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
        databaseService: DatabaseService
    ): File? = databaseService.withRealmAsync { realm ->
        try {
            val submissions = submissionIds.mapNotNull { id ->
                realm.where(RealmSubmission::class.java).equalTo("id", id).findFirst()
            }

            if (submissions.isEmpty()) return@withRealmAsync null

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

                canvas.drawText("$examTitle - All Submissions", MARGIN, yPosition, titlePaint)
                yPosition += LINE_HEIGHT * 2

                canvas.drawText("Total Submissions: ${submissions.size}", MARGIN, yPosition, normalPaint)
                yPosition += LINE_HEIGHT
                canvas.drawText("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}", MARGIN, yPosition, normalPaint)
                yPosition += LINE_HEIGHT * 3

                val examId = getExamId(submissions.firstOrNull()?.parentId)
                val questions = realm.where(RealmExamQuestion::class.java)
                    .equalTo("examId", examId)
                    .findAll()

                submissions.forEachIndexed { submissionIndex, submission ->
                    if (yPosition > PAGE_HEIGHT - MARGIN - 100) {
                        document.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        yPosition = MARGIN
                    }

                    canvas.drawText("Submission #${submissionIndex + 1}", MARGIN, yPosition, subHeaderPaint)
                    yPosition += LINE_HEIGHT * 1.5f
                    canvas.drawText("Date: ${TimeUtils.getFormattedDateWithTime(submission.lastUpdateTime)}", MARGIN + 20, yPosition, normalPaint)
                    yPosition += LINE_HEIGHT
                    canvas.drawText("Status: ${submission.status}", MARGIN + 20, yPosition, normalPaint)
                    yPosition += LINE_HEIGHT * 2

                    questions.forEachIndexed { index, question ->
                        if (yPosition > PAGE_HEIGHT - MARGIN - 100) {
                            document.finishPage(page)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                            page = document.startPage(pageInfo)
                            canvas = page.canvas
                            yPosition = MARGIN
                        }

                        val questionText = "Q${index + 1}: ${question.body ?: ""}"
                        yPosition = drawMultilineText(canvas, questionText, MARGIN + 20, yPosition, normalPaint, PAGE_WIDTH - (2 * MARGIN) - 20)
                        yPosition += LINE_HEIGHT / 2

                        val answer = submission.answers?.find { it.questionId == question.id }
                        val answerText = formatAnswer(answer)
                        yPosition = drawMultilineText(canvas, "A: $answerText", MARGIN + 40, yPosition, normalPaint, PAGE_WIDTH - (2 * MARGIN) - 40)
                        yPosition += LINE_HEIGHT * 1.5f
                    }

                    yPosition += LINE_HEIGHT * 2
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
