package org.ole.planet.myplanet.model

data class CourseStepData(
    val step: CourseStep,
    val resources: List<MyLibrary>,
    val stepExams: List<StepExam>,
    val stepSurvey: List<StepExam>,
    val userHasCourse: Boolean
)
