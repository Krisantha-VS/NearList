package com.sharma.nearlist.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormattersTest {

    // U11
    @Test
    public void distance_usesMetresBelowOneKilometre() {
        assertEquals("450 m", Formatters.distance(450));
    }

    // U11
    @Test
    public void distance_usesKilometresWithOneDecimal() {
        assertEquals("1.2 km", Formatters.distance(1234));
    }

    @Test
    public void distance_switchesToKilometres_whenMetresRoundUpToOneThousand() {
        assertEquals("999 m", Formatters.distance(999.4));
        assertEquals("1.0 km", Formatters.distance(999.6));
    }
}
