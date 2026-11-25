package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseSubjectClassifierTest {

    private fun assertSubject(expected: CourseSubject, title: String?) =
        assertEquals(title, expected, CourseSubjectClassifier.classify(title))

    @Test
    fun `missing title is general, not literacy`() {
        assertSubject(CourseSubject.GENERAL, null)
        assertSubject(CourseSubject.GENERAL, "")
        assertSubject(CourseSubject.GENERAL, "   ")
    }

    @Test
    fun `difficulty levels are not subjects`() {
        listOf("Beginner", "Intermediate", "Advanced", "Expert").forEach {
            assertSubject(CourseSubject.GENERAL, it)
        }
    }

    @Test
    fun `titles without a clear subject are general`() {
        assertSubject(CourseSubject.GENERAL, "Challenge course")
        assertSubject(CourseSubject.GENERAL, "How to send survey")
        assertSubject(CourseSubject.GENERAL, "Unit 2: The Moon")
    }

    @Test
    fun `english titles`() {
        assertSubject(CourseSubject.MATHEMATICS, "Basic Math Level 1")
        assertSubject(CourseSubject.HEALTH, "Health & Wellness")
        assertSubject(CourseSubject.SOCIAL_STUDIES, "Unit 3: Rural, Suburban, Urban")
        assertSubject(CourseSubject.SOCIAL_STUDIES, "Financial Literacy 101")
        assertSubject(CourseSubject.TECHNOLOGY, "Introduction to Prompt Engineering")
        assertSubject(CourseSubject.TECHNOLOGY, " GitHub Issues and Pull Requests")
        assertSubject(CourseSubject.LITERACY, "Unit 1: Fairy Tales Retold")
    }

    @Test
    fun `spanish and french titles, with or without accents`() {
        assertSubject(CourseSubject.SOCIAL_STUDIES, "Microeconomía")
        assertSubject(CourseSubject.SOCIAL_STUDIES, "Comercialización y Ventas ")
        assertSubject(CourseSubject.TECHNOLOGY, "Programación de computadoras")
        assertSubject(CourseSubject.TECHNOLOGY, "Introducción a JS: dibujo y animación")
        assertSubject(CourseSubject.MATHEMATICS, "Matemáticas básicas")
        assertSubject(CourseSubject.HEALTH, "Santé et hygiène")
    }

    @Test
    fun `short abbreviations match whole words only`() {
        assertSubject(CourseSubject.TECHNOLOGY, "TICs")
        assertSubject(CourseSubject.TECHNOLOGY, "ICT Basics")
        assertSubject(CourseSubject.MATHEMATICS, "Statistics for beginners")
        assertSubject(CourseSubject.GENERAL, "Practice and ethics")
        assertSubject(CourseSubject.GENERAL, "Rain and rivers")
    }
}
