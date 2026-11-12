package org.ole.planet.myplanet.utils

enum class CourseSubject {
    MATHEMATICS,
    LITERACY,
    HEALTH,
    SOCIAL_STUDIES,
    TECHNOLOGY
}

object CourseSubjectClassifier {
    fun classify(subjectLevel: String?): CourseSubject {
        val subject = subjectLevel?.lowercase().orEmpty()
        return when {
            subject.contains("math") -> CourseSubject.MATHEMATICS
            subject.contains("health") -> CourseSubject.HEALTH
            subject.contains("social") || subject.contains("civic") -> CourseSubject.SOCIAL_STUDIES
            subject.contains("computer") || subject.contains("technology") || subject.contains("ict") -> CourseSubject.TECHNOLOGY
            else -> CourseSubject.LITERACY
        }
    }
}
