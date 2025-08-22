package org.ole.planet.myplanet.utilities;

import org.junit.Test;
import java.time.LocalDate;
import java.time.ZoneId;
import static org.junit.Assert.assertEquals;

public class TimeUtilsTest {

    @Test
    public void testGetFormattedDate() {
        // Test with a specific millisecond value
        long millis = 1672531200000L; // Corresponds to 2023-01-01 00:00:00 UTC
        String expectedDate = "Sunday, Jan 01, 2023";
        assertEquals(expectedDate, TimeUtils.INSTANCE.getFormattedDate(millis));

        // Test with null
        String expectedToday = TimeUtils.INSTANCE.getFormattedDate(LocalDate.now().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli());
        assertEquals(expectedToday, TimeUtils.INSTANCE.getFormattedDate(null));
    }

    @Test
    public void testGetFormattedDateWithTime() {
        long millis = 1672531200000L; // Corresponds to 2023-01-01 00:00:00 UTC
        // The output of this function depends on the system's timezone.
        // For the purpose of this test, we are assuming the system is in UTC.
        // A more robust test would involve setting the timezone explicitly.
        String expectedDateTime = "Sun 01, January 2023 , 12:00 AM";
        assertEquals(expectedDateTime, TimeUtils.INSTANCE.getFormattedDateWithTime(millis));
    }

    @Test
    public void testGetAge() {
        // Test with a date of birth
        String dob = "2003-01-01T12:00:00.000Z";
        int expectedAge = java.time.Period.between(java.time.LocalDate.of(2003, 1, 1), java.time.LocalDate.now()).getYears();
        assertEquals(expectedAge, TimeUtils.INSTANCE.getAge(dob));

        // Test with another date of birth
        String dob2 = "1990-05-15T10:00:00.000Z";
        int expectedAge2 = java.time.Period.between(java.time.LocalDate.of(1990, 5, 15), java.time.LocalDate.now()).getYears();
        assertEquals(expectedAge2, TimeUtils.INSTANCE.getAge(dob2));
    }

    @Test
    public void testFormatDate() {
        long millis = 1672531200000L; // Corresponds to 2023-01-01 00:00:00 UTC
        String expectedDate = "Sun 01, January 2023";
        assertEquals(expectedDate, TimeUtils.INSTANCE.formatDate(millis));
    }

    @Test
    public void testFormatDateWithFormat() {
        long millis = 1672531200000L; // Corresponds to 2023-01-01 00:00:00 UTC
        String format = "yyyy-MM-dd";
        String expectedDate = "2023-01-01";
        assertEquals(expectedDate, TimeUtils.INSTANCE.formatDate(millis, format));
    }
}
