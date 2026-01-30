package org.ole.planet.myplanet.model

data class CourseStepData(
    val step: RealmCourseStep,
    val resources: List<RealmMyLibrary>,
    val stepExams: List<RealmStepExam>,
    val stepSurvey: List<RealmStepExam>
)
