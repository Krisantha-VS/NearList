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

    @Test
    public void distance_keepsOneDecimal_justBelowTenKilometres() {
        assertEquals("9.9 km", Formatters.distance(9_940));
    }

    @Test
    public void distance_switchesToWholeKilometres_whenTenthsRoundUpToTen() {
        assertEquals("10 km", Formatters.distance(9_960));
    }

    @Test
    public void distance_usesWholeKilometres_fromTenKilometres() {
        assertEquals("10 km", Formatters.distance(10_000));
        assertEquals("15 km", Formatters.distance(14_612));
        assertEquals("128 km", Formatters.distance(127_600));
    }

    @Test
    public void distance_showsCapValue_atLimit() {
        assertEquals("500 km", Formatters.distance(500_400));
    }

    @Test
    public void distance_isCapped_beyondLimit() {
        assertEquals("500+ km", Formatters.distance(500_600));
        // Emulator at its default location (Mountain View) with places in Sri Lanka.
        assertEquals("500+ km", Formatters.distance(14_612_000));
    }
}
