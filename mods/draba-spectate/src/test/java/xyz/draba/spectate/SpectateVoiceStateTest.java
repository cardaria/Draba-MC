package xyz.draba.spectate;

import org.junit.jupiter.api.Test;
import xyz.draba.spectate.SpectateVoiceRouting.VoicePosition;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectateVoiceStateTest {
    private static final UUID OBSERVER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TARGET = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PLAYER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Test
    void usesSavedOriginForRangeAndTargetForCamera() {
        VoicePosition origin = position("minecraft:overworld", 1.0D, 65.0D, 2.0D);
        VoicePosition target = position("minecraft:the_nether", 50.0D, 71.0D, 60.0D);
        VoicePosition player = position("minecraft:overworld", 3.0D, 65.0D, 4.0D);
        SpectateVoiceState.Snapshot snapshot = new SpectateVoiceState.Snapshot(
                Map.of(OBSERVER, new SpectateVoiceState.Session(origin, TARGET)),
                Map.of(OBSERVER, target, TARGET, target, PLAYER, player));

        assertTrue(snapshot.isWatching(OBSERVER));
        assertFalse(snapshot.isWatching(PLAYER));
        assertEquals(origin, snapshot.effectivePosition(OBSERVER));
        assertEquals(target, snapshot.cameraPosition(OBSERVER));
        assertEquals(player, snapshot.effectivePosition(PLAYER));
        assertEquals(player, snapshot.cameraPosition(PLAYER));
    }

    @Test
    void failsClosedWhenAWatchingTargetsCameraIsUnavailable() {
        VoicePosition origin = position("minecraft:overworld", 1.0D, 65.0D, 2.0D);
        SpectateVoiceState.Snapshot snapshot = new SpectateVoiceState.Snapshot(
                Map.of(OBSERVER, new SpectateVoiceState.Session(origin, TARGET)),
                Map.of());

        assertEquals(origin, snapshot.effectivePosition(OBSERVER));
        assertNull(snapshot.cameraPosition(OBSERVER));
    }

    @Test
    void publishedSnapshotsAreImmutableAndAtomicallyReplaceable() {
        Map<UUID, SpectateVoiceState.Session> sessions = new HashMap<>();
        Map<UUID, VoicePosition> players = new HashMap<>();
        VoicePosition origin = position("minecraft:overworld", 0.0D, 64.0D, 0.0D);
        sessions.put(OBSERVER, new SpectateVoiceState.Session(origin, TARGET));
        players.put(TARGET, position("minecraft:the_end", 5.0D, 70.0D, 5.0D));

        SpectateVoiceState.publish(sessions, players);
        sessions.clear();
        players.clear();

        assertTrue(SpectateVoiceState.snapshot().isWatching(OBSERVER));
        SpectateVoiceState.clear();
        assertTrue(SpectateVoiceState.snapshot().sessions().isEmpty());
        assertTrue(SpectateVoiceState.snapshot().players().isEmpty());
    }

    private static VoicePosition position(String dimension, double x, double y, double z) {
        return new VoicePosition(dimension, x, y, z);
    }
}
