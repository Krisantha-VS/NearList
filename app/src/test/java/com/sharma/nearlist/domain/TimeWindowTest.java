package com.sharma.nearlist.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TimeWindowTest {

    private static int at(int hour, int minute) {
        return hour * 60 + minute;
    }

    // U06
    @Test
    public void contains_sameDayWindow_startInclusiveEndExclusive() {
        int start = at(18, 0);
        int end = at(21, 0);

        assertTrue(TimeWindow.contains(start, end, at(18, 0)));
        assertTrue(TimeWindow.contains(start, end, at(20, 59)));
        assertFalse(TimeWindow.contains(start, end, at(21, 0)));
    }

    // U07
    @Test
    public void contains_windowCrossingMidnight() {
        int start = at(22, 0);
        int end = at(2, 0);

        assertTrue(TimeWindow.contains(start, end, at(23, 30)));
        assertTrue(TimeWindow.contains(start, end, at(1, 0)));
        assertFalse(TimeWindow.contains(start, end, at(12, 0)));
    }

    @Test
    public void contains_isAlwaysTrue_whenStartEqualsEnd() {
        assertTrue(TimeWindow.contains(at(9, 0), at(9, 0), at(3, 0)));
        assertTrue(TimeWindow.contains(at(9, 0), at(9, 0), at(9, 0)));
    }

    @Test
    public void format_writesSharedMarkerOnce() {
        assertEquals("6–9 PM", TimeWindow.format(at(18, 0), at(21, 0)));
    }

    @Test
    public void format_writesBothMarkers_whenTheyDiffer() {
        assertEquals("8 AM–8 PM", TimeWindow.format(at(8, 0), at(20, 0)));
        assertEquals("10:30 PM–2 AM", TimeWindow.format(at(22, 30), at(2, 0)));
    }

    @Test
    public void format_handlesNoonMidnightAndMinutes() {
        assertEquals("12 AM–12:05 PM", TimeWindow.format(at(0, 0), at(12, 5)));
    }
}
