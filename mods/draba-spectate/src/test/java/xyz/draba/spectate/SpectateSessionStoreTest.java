package xyz.draba.spectate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.draba.spectate.SpectateSessionStore.StoredSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectateSessionStoreTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsReloadsAndRemovesRecoveryOrigins() throws IOException {
        Path file = temporaryDirectory.resolve("sessions.json");
        StoredSession origin = origin();
        SpectateSessionStore store = new SpectateSessionStore(file);

        store.put(PLAYER, origin);
        assertTrue(Files.exists(file));
        assertEquals(origin, new SpectateSessionStore(file).get(PLAYER).orElseThrow());

        store.remove(PLAYER);
        SpectateSessionStore reloaded = new SpectateSessionStore(file);
        assertEquals(0, reloaded.size());
        assertFalse(reloaded.get(PLAYER).isPresent());
    }

    @Test
    void rejectsCorruptRecoveryDataInsteadOfDiscardingIt() throws IOException {
        Path file = temporaryDirectory.resolve("sessions.json");
        Files.writeString(file, "{not valid json");
        assertThrows(IOException.class, () -> new SpectateSessionStore(file));
    }

    @Test
    void rejectsNonFiniteOrigins() {
        assertThrows(IllegalArgumentException.class, () -> new StoredSession(
                "Player", "minecraft:overworld", Double.NaN, 64, 0, 0, 0, "survival"));
    }

    private static StoredSession origin() {
        return new StoredSession(
                "ExamplePlayer", "minecraft:overworld",
                12.5D, 64.0D, -7.25D, 90.0F, 12.0F, "survival");
    }
}
