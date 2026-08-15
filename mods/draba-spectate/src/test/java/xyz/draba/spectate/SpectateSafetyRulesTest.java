package xyz.draba.spectate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectateSafetyRulesTest {
    private static final SpectateSafetyRules.Snapshot SAFE = snapshot();

    @Test
    void safePlayerMayBeginArming() {
        assertTrue(SpectateSafetyRules.initialBlocker(SAFE).isEmpty());
    }

    @Test
    void everyUnsafeStateHasTheExpectedBlocker() {
        assertInitial(SpectateSafetyRules.Blocker.DEAD,
                new SpectateSafetyRules.Snapshot(
                        false, false, false, false, true, false,
                        false, false, false, false, false));
        assertInitial(SpectateSafetyRules.Blocker.ALREADY_SPECTATOR,
                new SpectateSafetyRules.Snapshot(
                        true, true, false, false, true, false,
                        false, false, false, false, false));
        assertInitial(SpectateSafetyRules.Blocker.PASSENGER,
                new SpectateSafetyRules.Snapshot(
                        true, false, true, false, true, false,
                        false, false, false, false, false));
        assertInitial(SpectateSafetyRules.Blocker.SLEEPING,
                new SpectateSafetyRules.Snapshot(
                        true, false, false, true, true, false,
                        false, false, false, false, false));
        assertInitial(SpectateSafetyRules.Blocker.AIRBORNE,
                new SpectateSafetyRules.Snapshot(
                        true, false, false, false, false, false,
                        false, false, false, false, false));
        assertInitial(SpectateSafetyRules.Blocker.AIRBORNE,
                new SpectateSafetyRules.Snapshot(
                        true, false, false, false, true, true,
                        false, false, false, false, false));
        assertInitial(SpectateSafetyRules.Blocker.HAZARD,
                new SpectateSafetyRules.Snapshot(
                        true, false, false, false, true, false,
                        true, false, false, false, false));
        assertInitial(SpectateSafetyRules.Blocker.HAZARD,
                new SpectateSafetyRules.Snapshot(
                        true, false, false, false, true, false,
                        false, true, false, false, false));
        assertInitial(SpectateSafetyRules.Blocker.HAZARD,
                new SpectateSafetyRules.Snapshot(
                        true, false, false, false, true, false,
                        false, false, true, false, false));
        assertInitial(SpectateSafetyRules.Blocker.RECENT_DAMAGE,
                new SpectateSafetyRules.Snapshot(
                        true, false, false, false, true, false,
                        false, false, false, true, false));
        assertInitial(SpectateSafetyRules.Blocker.ACTIVE_THREAT,
                new SpectateSafetyRules.Snapshot(
                        true, false, false, false, true, false,
                        false, false, false, false, true));
    }

    @Test
    void movementAndDimensionChangesCancelArming() {
        assertEquals(SpectateSafetyRules.Blocker.MOVED,
                SpectateSafetyRules.armingBlocker(SAFE, true, false).orElseThrow());
        assertEquals(SpectateSafetyRules.Blocker.CHANGED_DIMENSION,
                SpectateSafetyRules.armingBlocker(SAFE, false, true).orElseThrow());
        assertTrue(SpectateSafetyRules.armingBlocker(SAFE, false, false).isEmpty());
    }

    @Test
    void movementToleranceAllowsOnlyTinyPositionJitter() {
        assertFalse(SpectateSafetyRules.movedBeyondAnchor(0.0D));
        assertFalse(SpectateSafetyRules.movedBeyondAnchor(
                SpectateSafetyRules.MAX_ARMING_MOVEMENT_SQUARED));
        assertTrue(SpectateSafetyRules.movedBeyondAnchor(
                Math.nextUp(SpectateSafetyRules.MAX_ARMING_MOVEMENT_SQUARED)));
    }

    @Test
    void cancellationMessagesExplainTheCause() {
        for (SpectateSafetyRules.Blocker blocker : SpectateSafetyRules.Blocker.values()) {
            assertFalse(blocker.initialMessage().isBlank(), blocker.name());
            assertTrue(blocker.cancellationMessage().startsWith("Spectate cancelled"),
                    blocker.name());
        }
    }

    private static void assertInitial(
            SpectateSafetyRules.Blocker expected, SpectateSafetyRules.Snapshot snapshot) {
        assertEquals(expected, SpectateSafetyRules.initialBlocker(snapshot).orElseThrow());
    }

    private static SpectateSafetyRules.Snapshot snapshot() {
        return new SpectateSafetyRules.Snapshot(
                true, false, false, false, true, false,
                false, false, false, false, false);
    }
}
