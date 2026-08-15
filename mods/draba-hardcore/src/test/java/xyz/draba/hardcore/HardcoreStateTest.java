package xyz.draba.hardcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HardcoreStateTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path temporaryDirectory;

    @Test
    void deathAtomicallyInvalidatesExactlyOneLifeAndSurvivesReload() throws Exception {
        Path path = temporaryDirectory.resolve("state.json");
        HardcoreState state = new HardcoreState(path);
        Instant diedAt = Instant.parse("2026-08-13T12:00:00Z");

        assertEquals(1, state.currentLife(ALICE));
        HardcoreState.DeathRecord death = state.beginDeath(ALICE, "Alice", diedAt);

        assertEquals(1, death.deadLife());
        assertEquals(Instant.parse("2026-08-20T12:00:00Z"), death.cooldown().eligibleAt());
        assertTrue(state.isInvalidLife(ALICE, 1));
        assertFalse(state.isInvalidLife(ALICE, 2));
        assertEquals(2, state.currentLife(ALICE));

        HardcoreState reloaded = new HardcoreState(path);
        assertEquals(2, reloaded.currentLife(ALICE));
        assertTrue(reloaded.isInvalidLife(ALICE, 1));
        assertEquals(death.cooldown(), reloaded.cooldown(ALICE).orElseThrow());
    }

    @Test
    void clearingCooldownNeverRevalidatesTheDeadLife() throws Exception {
        HardcoreState state = new HardcoreState(temporaryDirectory.resolve("state.json"));
        state.beginDeath(ALICE, "Alice", Instant.parse("2026-08-13T12:00:00Z"));

        state.clearCooldown(ALICE);

        assertTrue(state.cooldown(ALICE).isEmpty());
        assertTrue(state.isInvalidLife(ALICE, 1));
        assertEquals(2, state.currentLife(ALICE));
    }

    @Test
    void laterDeathsAdvanceGenerationWithoutAffectingFutureItems() throws Exception {
        Path path = temporaryDirectory.resolve("state.json");
        HardcoreState state = new HardcoreState(path);
        state.beginDeath(ALICE, "Alice", Instant.parse("2026-08-01T00:00:00Z"));
        state.clearCooldown(ALICE);

        HardcoreState.DeathRecord second = state.beginDeath(ALICE, "Alice", Instant.parse("2026-08-10T00:00:00Z"));

        assertEquals(2, second.deadLife());
        assertTrue(state.isInvalidLife(ALICE, 1));
        assertTrue(state.isInvalidLife(ALICE, 2));
        assertFalse(state.isInvalidLife(ALICE, 3));
        assertEquals(3, new HardcoreState(path).currentLife(ALICE));
    }

    @Test
    void schemaOneMigrationKeepsCooldownAndGrandfathersOldStorage() throws Exception {
        Path path = temporaryDirectory.resolve("state.json");
        Files.writeString(path, """
                {
                  "schemaVersion": 1,
                  "cooldowns": {
                    "00000000-0000-0000-0000-000000000001": {
                      "playerName": "Alice",
                      "diedAt": "2026-08-13T12:00:00Z",
                      "eligibleAt": "2026-08-20T12:00:00Z"
                    }
                  },
                  "blockStorage": [{
                    "dimension": "minecraft:overworld",
                    "position": 123,
                    "owner": "00000000-0000-0000-0000-000000000001",
                    "pendingPurge": true
                  }],
                  "entityStorage": {
                    "00000000-0000-0000-0000-000000000099": {
                      "owner": "00000000-0000-0000-0000-000000000001",
                      "pendingPurge": false
                    }
                  }
                }
                """);

        HardcoreState migrated = new HardcoreState(path);

        assertTrue(migrated.cooldown(ALICE).isPresent());
        assertEquals(1, migrated.currentLife(ALICE));
        JsonObject saved = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(2, saved.get("schemaVersion").getAsInt());
        assertTrue(saved.has("lastDeadLives"));
        assertFalse(saved.has("blockStorage"));
        assertFalse(saved.has("entityStorage"));
    }

    @Test
    void administrativeCooldownChangesDoNotChangeLifeGeneration() throws Exception {
        HardcoreState state = new HardcoreState(temporaryDirectory.resolve("state.json"));
        Instant now = Instant.parse("2026-08-15T00:00:00Z");

        state.setCooldown(ALICE, "Alice", now, Duration.ofDays(3));
        assertEquals(1, state.currentLife(ALICE));
        state.clearCooldown(ALICE);
        assertEquals(1, state.currentLife(ALICE));
    }
}
