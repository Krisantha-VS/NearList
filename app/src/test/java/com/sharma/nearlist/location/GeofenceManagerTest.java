package com.sharma.nearlist.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure parts of GeofenceManager: responsiveness table (spec section 7) and request IDs. */
public class GeofenceManagerTest {

    @Test
    public void responsivenessMs_isZero_whenDemoActive() {
        assertEquals(0L, GeofenceManager.responsivenessMs(true, true, 5, false));
    }

    @Test
    public void responsivenessMs_isFiveMinutes_whenBatteryAwareLowAndNotCharging() {
        assertEquals(300_000L, GeofenceManager.responsivenessMs(false, true, 19, false));
    }

    @Test
    public void responsivenessMs_isOneMinute_whenCharging() {
        assertEquals(60_000L, GeofenceManager.responsivenessMs(false, true, 19, true));
    }

    @Test
    public void responsivenessMs_isOneMinute_whenBatteryAtThreshold() {
        assertEquals(60_000L, GeofenceManager.responsivenessMs(false, true, 20, false));
    }

    @Test
    public void responsivenessMs_isOneMinute_whenBatteryAwareOff() {
        assertEquals(60_000L, GeofenceManager.responsivenessMs(false, false, 5, false));
    }

    @Test
    public void responsivenessMs_isOneMinute_whenBatteryLevelUnknown() {
        assertEquals(60_000L, GeofenceManager.responsivenessMs(false, true, -1, false));
    }

    @Test
    public void placeIdFromRequestId_roundTripsAndRejectsOtherFormats() {
        assertEquals(42L, GeofenceManager.placeIdFromRequestId(GeofenceManager.requestIdFor(42L)));
        assertEquals(-1L, GeofenceManager.placeIdFromRequestId("task-42"));
        assertEquals(-1L, GeofenceManager.placeIdFromRequestId("place-abc"));
        assertEquals(-1L, GeofenceManager.placeIdFromRequestId(null));
    }

    @Test
    public void isRecentSync_trueOnlyForAFreshSyncCoveringAllPlaces() {
        long now = 1_000_000_000L;
        assertTrue(GeofenceManager.isRecentSync(now - 10_000L, 3, 3, now));
        assertFalse(GeofenceManager.isRecentSync(now - GeofenceManager.RECENT_SYNC_MS, 3, 3, now));
        assertFalse(GeofenceManager.isRecentSync(now - 10_000L, 2, 3, now));
        assertFalse(GeofenceManager.isRecentSync(0L, 0, 0, now));
    }
}
