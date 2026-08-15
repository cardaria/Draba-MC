package xyz.draba.spectate;

import org.junit.jupiter.api.Test;
import xyz.draba.spectate.SpectateVoiceRouting.VoicePosition;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectateVoiceRoutingTest {
    private static final UUID GROUP_ONE =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GROUP_TWO =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void replacesOnlyAutomaticProximityPathsForAffectedSessions() {
        assertTrue(SpectateVoiceRouting.replacesAutomaticDelivery("proximity", true, false));
        assertTrue(SpectateVoiceRouting.replacesAutomaticDelivery("proximity", false, true));
        assertTrue(SpectateVoiceRouting.replacesAutomaticDelivery("spectator", true, false));
        assertFalse(SpectateVoiceRouting.replacesAutomaticDelivery("group", true, true));
        assertFalse(SpectateVoiceRouting.replacesAutomaticDelivery("plugin", true, true));
        assertFalse(SpectateVoiceRouting.replacesAutomaticDelivery("proximity", false, false));
    }

    @Test
    void checksSavedOriginDimensionAndInclusiveDistance() {
        VoicePosition listener = position("minecraft:overworld", 10.0D, 64.0D, 10.0D);
        assertTrue(SpectateVoiceRouting.isAudible(
                position("minecraft:overworld", 34.0D, 64.0D, 10.0D), listener, 24.0D));
        assertFalse(SpectateVoiceRouting.isAudible(
                position("minecraft:overworld", 34.01D, 64.0D, 10.0D), listener, 24.0D));
        assertFalse(SpectateVoiceRouting.isAudible(
                position("minecraft:the_nether", 10.0D, 64.0D, 10.0D), listener, 48.0D));
        assertFalse(SpectateVoiceRouting.isAudible(listener, listener, 0.0D));
        assertFalse(SpectateVoiceRouting.isAudible(listener, listener, Double.NaN));
    }

    @Test
    void manuallyRelaysOnlyWhenAnEndpointIsWatching() {
        assertTrue(SpectateVoiceRouting.needsManualDelivery(true, false));
        assertTrue(SpectateVoiceRouting.needsManualDelivery(false, true));
        assertTrue(SpectateVoiceRouting.needsManualDelivery(true, true));
        assertFalse(SpectateVoiceRouting.needsManualDelivery(false, false));
    }

    @Test
    void preservesConnectionAndVoiceGroupIsolationRules() {
        assertTrue(SpectateVoiceRouting.canReceiveProximity(
                true, false, true, null, null, false));
        assertTrue(SpectateVoiceRouting.canReceiveProximity(
                true, false, true, GROUP_ONE, GROUP_TWO, false));
        assertFalse(SpectateVoiceRouting.canReceiveProximity(
                false, false, true, null, null, false));
        assertFalse(SpectateVoiceRouting.canReceiveProximity(
                true, true, true, null, null, false));
        assertFalse(SpectateVoiceRouting.canReceiveProximity(
                true, false, false, null, null, false));
        assertFalse(SpectateVoiceRouting.canReceiveProximity(
                true, false, true, GROUP_ONE, GROUP_ONE, false));
        assertFalse(SpectateVoiceRouting.canReceiveProximity(
                true, false, true, GROUP_ONE, GROUP_TWO, true));
    }

    @Test
    void rebasesOriginRelativeSoundAroundRemoteCamera() {
        VoicePosition source = position("minecraft:overworld", 16.0D, 66.0D, 2.0D);
        VoicePosition listener = position("minecraft:overworld", 10.0D, 64.0D, -2.0D);
        VoicePosition camera = position("minecraft:the_nether", 100.0D, 80.0D, 300.0D);

        assertEquals(
                position("minecraft:the_nether", 106.0D, 82.0D, 304.0D),
                SpectateVoiceRouting.rebase(source, listener, camera));
    }

    @Test
    void rejectsInvalidAndCrossDimensionRebases() {
        assertThrows(IllegalArgumentException.class,
                () -> position("", 0.0D, 0.0D, 0.0D));
        assertThrows(IllegalArgumentException.class,
                () -> position("minecraft:overworld", Double.POSITIVE_INFINITY, 0.0D, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> SpectateVoiceRouting.rebase(
                position("minecraft:overworld", 0.0D, 0.0D, 0.0D),
                position("minecraft:the_nether", 0.0D, 0.0D, 0.0D),
                position("minecraft:the_end", 0.0D, 0.0D, 0.0D)));
    }

    private static VoicePosition position(String dimension, double x, double y, double z) {
        return new VoicePosition(dimension, x, y, z);
    }
}
