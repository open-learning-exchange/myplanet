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
import org.ole.planet.myplanet.data.room.dao.legacy.AnswerDao
import org.ole.planet.myplanet.data.room.dao.legacy.ExamDao
import org.ole.planet.myplanet.data.room.dao.legacy.QuestionDao
import org.ole.planet.myplanet.data.room.dao.legacy.SubmissionDao
import org.ole.planet.myplanet.data.room.entity.legacy.toRealmModel
import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.utils.TimeProvider
import org.ole.planet.myplanet.utils.TimeUtils

internal class SubmissionsRepositoryExporter @Inject constructor(
    private val submissionDao: SubmissionDao,
    private val answerDao: AnswerDao,
    private val examDao: ExamDao,
    private val questionDao: QuestionDao,
    private val timeProvider: TimeProvider
) {

    companion object {
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 50f
        private const val LINE_HEIGHT = 20f
    }

    suspend fun generateSubmissionPdf(
        context: Context,
        submissionId: String
    ): File? {
        return try {
            val submissionEntity = submissionDao.getByIdOrRemoteId(submissionId) ?: return null
            val answers = answerDao.getBySubmissionId(submissionEntity.id)
            val submission = submissionEntity.toRealmModel(answers)

            val document = PdfDocument()
            try {
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
                val exam = examId?.let { examDao.getById(it)?.toRealmModel() }

                canvas.drawText(exam?.name ?: "Submission Report", MARGIN, yPosition, titlePaint)
                yPosition += LINE_HEIGHT * 2

                canvas.drawText("Status: ${submission.status}", MARGIN, yPosition, normalPaint)
                yPosition += LINE_HEIGHT
                canvas.drawText("Date: ${TimeUtils.getFormattedDateWithTime(submission.lastUpdateTime)}", MARGIN, yPosition, normalPaint)
                yPosition += LINE_HEIGHT * 2

                val questions = examId?.let { questionDao.getByExamId(it).map { question -> question.toRealmModel() } }.orEmpty()

                val answersMap = submission.answers?.associateBy { it.questionId } ?: emptyMap()

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

                    val answer = answersMap[question.id]
                    val answerText = formatAnswer(answer)
                    canvas.drawText("A: $answerText", MARGIN + 20, yPosition, normalPaint)
                    yPosition += LINE_HEIGHT * 2
                }

                document.finishPage(page)

                val fileName = "submission_${submission.id}_${timeProvider.now()}.pdf"
                val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Submissions")
                if (!directory.exists()) {
                    directory.mkdirs()
                }

                val file = File(directory, fileName)
                FileOutputStream(file).use { outputStream ->
                    document.writeTo(outputStream)
                }

                file
            } finally {
                document.close()
            }
        } catch (e: Exception) {
                e.printStackTrace()
                null
            }
    }

    suspend fun generateMultipleSubmissionsPdf(
        context: Context,
        submissionIds: List<String>,
        examTitle: String
    ): File? {
        return try {
            val submissionEntities = if (submissionIds.isEmpty()) emptyList() else submissionDao.getByIds(submissionIds)
            val answersBySubmissionId = if (submissionEntities.isEmpty()) {
                emptyMap()
            } else {
                answerDao.getBySubmissionIds(submissionEntities.map { it.id }).groupBy { it.submissionId }
            }
            val submissions = submissionEntities.map { submission ->
                submission.toRealmModel(answersBySubmissionId[submission.id].orEmpty())
            }

            if (submissions.isEmpty()) return null

            val document = PdfDocument()
            try {
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
                val questions = examId?.let { questionDao.getByExamId(it).map { question -> question.toRealmModel() } }.orEmpty()

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

                    val answersMap = submission.answers?.associateBy { it.questionId } ?: emptyMap()

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

                        val answer = answersMap[question.id]
                        val answerText = formatAnswer(answer)
                        yPosition = drawMultilineText(canvas, "A: $answerText", MARGIN + 40, yPosition, normalPaint, PAGE_WIDTH - (2 * MARGIN) - 40)
                        yPosition += LINE_HEIGHT * 1.5f
                    }

                    yPosition += LINE_HEIGHT * 2
                }

                document.finishPage(page)

                val fileName = "submissions_report_${timeProvider.now()}.pdf"
                val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Submissions")
                if (!directory.exists()) {
                    directory.mkdirs()
                }

                val file = File(directory, fileName)
                FileOutputStream(file).use { outputStream ->
                    document.writeTo(outputStream)
                }

                file
            } finally {
                document.close()
            }
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

    private fun formatAnswer(answer: RealmAnswer?): String {
        if (answer == null) return "No answer provided"
        val value = answer.value
        val choices = answer.valueChoices
        return when {
            !value.isNullOrEmpty() -> value
            !choices.isNullOrEmpty() -> {
                choices.joinToString(", ") { choice ->
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
