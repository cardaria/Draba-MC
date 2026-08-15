package xyz.draba.spectate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectateTargetSelectorTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void emptyTargetsRemainEmpty() {
        assertTrue(SpectateTargetSelector.first(List.of()).isEmpty());
        assertTrue(SpectateTargetSelector.cycle(List.of(), A, 1).isEmpty());
    }

    @Test
    void cyclesBothDirectionsAndWraps() {
        List<UUID> targets = List.of(A, B, C);
        assertEquals(B, SpectateTargetSelector.cycle(targets, A, 1).orElseThrow());
        assertEquals(C, SpectateTargetSelector.cycle(targets, A, -1).orElseThrow());
        assertEquals(A, SpectateTargetSelector.cycle(targets, C, 1).orElseThrow());
    }

    @Test
    void missingTargetFallsBackDeterministically() {
        assertEquals(A, SpectateTargetSelector.cycle(List.of(A, B), C, 1).orElseThrow());
    }
}
