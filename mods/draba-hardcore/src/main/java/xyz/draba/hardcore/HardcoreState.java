package xyz.draba.hardcore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Small, atomically persisted Hardcore state. Item ownership itself travels with
 * ItemStacks; only each player's last invalidated life belongs here.
 */
final class HardcoreState {
    static final Duration DEFAULT_COOLDOWN = Duration.ofDays(7);

    private static final int SCHEMA_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private final Map<UUID, Cooldown> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastDeadLives = new HashMap<>();

    HardcoreState(Path path) throws IOException {
        this.path = path;
        if (Files.exists(path)) {
            boolean migrated = load();
            if (migrated) {
                save();
            }
        } else {
            save();
        }
    }

    /**
     * Invalidates the life the player was just living and records the cooldown
     * in the same atomic file replacement. A successful return means a purge
     * may safely begin; a crash can never leave the life valid.
     */
    synchronized DeathRecord beginDeath(UUID playerId, String playerName, Instant diedAt) throws IOException {
        long deadLife = currentLife(playerId);
        Cooldown cooldown = new Cooldown(playerName, diedAt, diedAt.plus(DEFAULT_COOLDOWN));
        Cooldown previousCooldown = cooldowns.put(playerId, cooldown);
        Long previousDeadLife = lastDeadLives.put(playerId, deadLife);
        try {
            save();
        } catch (IOException exception) {
            restore(cooldowns, playerId, previousCooldown);
            restore(lastDeadLives, playerId, previousDeadLife);
            throw exception;
        }
        return new DeathRecord(cooldown, deadLife);
    }

    synchronized long currentLife(UUID playerId) {
        return Math.addExact(lastDeadLives.getOrDefault(playerId, 0L), 1L);
    }

    synchronized boolean isInvalidLife(UUID playerId, long life) {
        return life > 0 && life <= lastDeadLives.getOrDefault(playerId, 0L);
    }

    synchronized OwnershipLedger.OwnerLife ownerLife(UUID playerId, String playerName) {
        return new OwnershipLedger.OwnerLife(playerId, currentLife(playerId), playerName);
    }

    synchronized Optional<Cooldown> cooldown(UUID playerId) {
        return Optional.ofNullable(cooldowns.get(playerId));
    }

    synchronized boolean isActive(UUID playerId, Instant now) {
        Cooldown cooldown = cooldowns.get(playerId);
        return cooldown != null && now.isBefore(cooldown.eligibleAt());
    }

    synchronized Optional<Cooldown> clearCooldown(UUID playerId) throws IOException {
        Cooldown removed = cooldowns.remove(playerId);
        if (removed == null) {
            return Optional.empty();
        }
        try {
            save();
        } catch (IOException exception) {
            cooldowns.put(playerId, removed);
            throw exception;
        }
        return Optional.of(removed);
    }

    synchronized Cooldown setCooldown(UUID playerId, String playerName, Instant now, Duration duration)
            throws IOException {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Cooldown duration must be positive");
        }
        Cooldown updated = new Cooldown(playerName, now, now.plus(duration));
        Cooldown previous = cooldowns.put(playerId, updated);
        try {
            save();
        } catch (IOException exception) {
            restore(cooldowns, playerId, previous);
            throw exception;
        }
        return updated;
    }

    /**
     * @return true when a schema-1 file was migrated. Its block/entity owner
     * records are intentionally discarded: all pre-upgrade contents are
     * grandfathered permanent because historical contributors are unknowable.
     */
    private boolean load() throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int schemaVersion = required(root, "schemaVersion").getAsInt();
            if (schemaVersion != 1 && schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported Hardcore state schema: " + schemaVersion);
            }

            JsonObject serializedCooldowns = root.has("cooldowns")
                    ? root.getAsJsonObject("cooldowns") : new JsonObject();
            for (Map.Entry<String, JsonElement> entry : serializedCooldowns.entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();
                cooldowns.put(UUID.fromString(entry.getKey()), new Cooldown(
                        required(value, "playerName").getAsString(),
                        Instant.parse(required(value, "diedAt").getAsString()),
                        Instant.parse(required(value, "eligibleAt").getAsString())));
            }

            if (schemaVersion == SCHEMA_VERSION) {
                JsonObject serializedLives = root.has("lastDeadLives")
                        ? root.getAsJsonObject("lastDeadLives") : new JsonObject();
                for (Map.Entry<String, JsonElement> entry : serializedLives.entrySet()) {
                    long life = entry.getValue().getAsLong();
                    if (life <= 0) {
                        throw new IllegalArgumentException("Invalid last dead life for " + entry.getKey());
                    }
                    lastDeadLives.put(UUID.fromString(entry.getKey()), life);
                }
            }
            return schemaVersion == 1;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid Hardcore state in " + path, exception);
        }
    }

    private void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);

        JsonObject serializedCooldowns = new JsonObject();
        cooldowns.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            JsonObject value = new JsonObject();
            value.addProperty("playerName", entry.getValue().playerName());
            value.addProperty("diedAt", entry.getValue().diedAt().toString());
            value.addProperty("eligibleAt", entry.getValue().eligibleAt().toString());
            serializedCooldowns.add(entry.getKey().toString(), value);
        });
        root.add("cooldowns", serializedCooldowns);

        JsonObject serializedLives = new JsonObject();
        lastDeadLives.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> serializedLives.addProperty(entry.getKey().toString(), entry.getValue()));
        root.add("lastDeadLives", serializedLives);

        Files.createDirectories(path.getParent());
        Path temporaryPath = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporaryPath, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static JsonElement required(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return value;
    }

    private static <K, V> void restore(Map<K, V> map, K key, V previous) {
        if (previous == null) {
            map.remove(key);
        } else {
            map.put(key, previous);
        }
    }

    record Cooldown(String playerName, Instant diedAt, Instant eligibleAt) {
        Duration remainingAt(Instant now) {
            return now.isBefore(eligibleAt) ? Duration.between(now, eligibleAt) : Duration.ZERO;
        }
    }

    record DeathRecord(Cooldown cooldown, long deadLife) {
    }
}
