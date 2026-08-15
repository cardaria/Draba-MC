package xyz.draba.spectate.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkStatusMonitorTest {
    private static final byte[] ICON = new byte[]{1, 2, 3};

    @Test
    void skipsOnlyAnAlreadyUsableMatchingIcon() {
        assertFalse(NetworkStatusMonitor.needsIconUpload(ICON, ICON.clone(), true, true));
    }

    @Test
    void reuploadsMatchingBytesWhenTextureWasLost() {
        assertTrue(NetworkStatusMonitor.needsIconUpload(ICON, ICON.clone(), true, false));
        assertTrue(NetworkStatusMonitor.needsIconUpload(ICON, ICON.clone(), false, true));
    }

    @Test
    void uploadsChangedIconsAndIgnoresMissingIcons() {
        assertTrue(NetworkStatusMonitor.needsIconUpload(ICON, new byte[]{3, 2, 1}, true, true));
        assertFalse(NetworkStatusMonitor.needsIconUpload(null, ICON, false, false));
    }

    @Test
    void joinMarkerAcceptsOnlySaneDrabaStatusUuids() {
        long now = 1_800_000_000L;
        UUID marker = new UUID(NetworkStatusProtocol.JOIN_TIME_MAGIC, now - 3_600L);
        assertEquals(now - 3_600L,
                NetworkStatusProtocol.decodeJoinEpochSeconds(marker, now));
        assertEquals(-1L, NetworkStatusProtocol.decodeJoinEpochSeconds(
                UUID.randomUUID(), now));
        assertEquals(-1L, NetworkStatusProtocol.decodeJoinEpochSeconds(
                new UUID(NetworkStatusProtocol.JOIN_TIME_MAGIC, now + 301L), now));
    }

    @Test
    void elapsedJoinTimesStayCompactAndHumanReadable() {
        long now = 2_000_000L;
        assertEquals("—", NetworkStatusMonitor.formatElapsed(-1L, now));
        assertEquals("now", NetworkStatusMonitor.formatElapsed(now - 59L, now));
        assertEquals("8m", NetworkStatusMonitor.formatElapsed(now - 8L * 60L, now));
        assertEquals("1h 24m", NetworkStatusMonitor.formatElapsed(
                now - (84L * 60L), now));
        assertEquals("3d 2h", NetworkStatusMonitor.formatElapsed(
                now - (74L * 60L * 60L), now));
    }
}
