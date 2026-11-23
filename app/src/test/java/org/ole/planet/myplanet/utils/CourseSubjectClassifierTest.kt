package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseSubjectClassifierTest {

    @Test
    fun classify_null_defaultsToLiteracy() {
        assertEquals(CourseSubject.LITERACY, CourseSubjectClassifier.classify(null))
    }

    @Test
    fun classify_blank_defaultsToLiteracy() {
        assertEquals(CourseSubject.LITERACY, CourseSubjectClassifier.classify(""))
    }

    @Test
    fun classify_math_returnsMathematics() {
        assertEquals(CourseSubject.MATHEMATICS, CourseSubjectClassifier.classify("Mathematics"))
        assertEquals(CourseSubject.MATHEMATICS, CourseSubjectClassifier.classify("Basic Math Level 1"))
    }

    @Test
    fun classify_health_returnsHealth() {
        assertEquals(CourseSubject.HEALTH, CourseSubjectClassifier.classify("Health & Wellness"))
    }

    @Test
    fun classify_social_returnsSocialStudies() {
        assertEquals(CourseSubject.SOCIAL_STUDIES, CourseSubjectClassifier.classify("Social Studies"))
    }

    @Test
    fun classify_civic_returnsSocialStudies() {
        assertEquals(CourseSubject.SOCIAL_STUDIES, CourseSubjectClassifier.classify("Civic Education"))
    }

    @Test
    fun classify_computer_returnsTechnology() {
        assertEquals(CourseSubject.TECHNOLOGY, CourseSubjectClassifier.classify("Computer Science"))
    }

    @Test
    fun classify_technology_returnsTechnology() {
        assertEquals(CourseSubject.TECHNOLOGY, CourseSubjectClassifier.classify("Technology"))
    }

    @Test
    fun classify_ict_returnsTechnology() {
        assertEquals(CourseSubject.TECHNOLOGY, CourseSubjectClassifier.classify("ICT Basics"))
    }

    @Test
    fun classify_caseInsensitive() {
        assertEquals(CourseSubject.MATHEMATICS, CourseSubjectClassifier.classify("MATH"))
    }

    @Test
    fun classify_unrecognizedSubject_defaultsToLiteracy() {
        assertEquals(CourseSubject.LITERACY, CourseSubjectClassifier.classify("Literacy and Reading"))
        assertEquals(CourseSubject.LITERACY, CourseSubjectClassifier.classify("Random Subject"))
    }
}
