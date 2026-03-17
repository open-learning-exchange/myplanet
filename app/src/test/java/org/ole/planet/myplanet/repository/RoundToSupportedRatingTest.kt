package org.ole.planet.myplanet.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class RoundToSupportedRatingTest {

    @Test
    fun testRoundToSupportedRating_Zero() {
        assertEquals(1, RatingsRepositoryImpl.roundToSupportedRating(0f))
    }

    @Test
    fun testRoundToSupportedRating_PointFour() {
        assertEquals(1, RatingsRepositoryImpl.roundToSupportedRating(0.4f))
    }

    @Test
    fun testRoundToSupportedRating_One() {
        assertEquals(1, RatingsRepositoryImpl.roundToSupportedRating(1f))
    }

    @Test
    fun testRoundToSupportedRating_TwoPointFive() {
        assertEquals(3, RatingsRepositoryImpl.roundToSupportedRating(2.5f))
    }

    @Test
    fun testRoundToSupportedRating_Five() {
        assertEquals(5, RatingsRepositoryImpl.roundToSupportedRating(5f))
    }

    @Test
    fun testRoundToSupportedRating_FivePointSix() {
        assertEquals(5, RatingsRepositoryImpl.roundToSupportedRating(5.6f))
    }

    @Test
    fun testRoundToSupportedRating_Negative() {
        assertEquals(1, RatingsRepositoryImpl.roundToSupportedRating(-1.5f))
    }
}
