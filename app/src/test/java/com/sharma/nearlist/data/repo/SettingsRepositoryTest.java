package com.sharma.nearlist.data.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Tests the pure derived-value rules; no SharedPreferences or Context needed. */
public class SettingsRepositoryTest {

    private static final long NOW = 1_760_000_000_000L;
    private static final long MINUTE = 60_000L;

    // U12
    @Test
    public void isDemoActive_isTrue_whenEnabled59MinutesAgo() {
        assertTrue(SettingsRepository.isDemoActive(NOW - 59 * MINUTE, NOW));
    }

    // U12
    @Test
    public void isDemoActive_isFalse_whenEnabled61MinutesAgo() {
        assertFalse(SettingsRepository.isDemoActive(NOW - 61 * MINUTE, NOW));
    }

    @Test
    public void isDemoActive_isFalse_whenNeverEnabled() {
        assertFalse(SettingsRepository.isDemoActive(0L, NOW));
    }

    @Test
    public void cooldownMs_isTenSeconds_whenDemoActive() {
        assertEquals(10_000L, SettingsRepository.cooldownMs(true, 24));
    }

    @Test
    public void cooldownMs_usesHours_whenDemoInactive() {
        assertEquals(4 * 3_600_000L, SettingsRepository.cooldownMs(false, 4));
    }

    @Test
    public void demoQuietCap_isTenSecondsFromNow() {
        assertEquals(NOW + 10_000L, SettingsRepository.demoQuietCap(NOW));
    }

    @Test
    public void clampQuietUntil_cutsRunningCooldown_toDemoCap() {
        long cap = SettingsRepository.demoQuietCap(NOW);
        long fourHourCooldown = NOW + 4 * 60 * MINUTE;
        assertEquals(cap, SettingsRepository.clampQuietUntil(fourHourCooldown, cap));
    }

    @Test
    public void clampQuietUntil_keepsShorterOrExpiredValue() {
        long cap = SettingsRepository.demoQuietCap(NOW);
        assertEquals(NOW + 5_000L, SettingsRepository.clampQuietUntil(NOW + 5_000L, cap));
        assertEquals(0L, SettingsRepository.clampQuietUntil(0L, cap));
    }
}
