package xyz.draba.spectate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class SpectateSessionStore {
    private static final int FILE_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<UUID, StoredSession> sessions = new HashMap<>();

    SpectateSessionStore(Path file) throws IOException {
        this.file = file;
        load();
    }

    synchronized Optional<StoredSession> get(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    synchronized void put(UUID playerId, StoredSession session) throws IOException {
        StoredSession previous = sessions.put(playerId, session);
        try {
            save();
        } catch (IOException exception) {
            if (previous == null) {
                sessions.remove(playerId);
            } else {
                sessions.put(playerId, previous);
            }
            throw exception;
        }
    }

    synchronized void remove(UUID playerId) throws IOException {
        StoredSession removed = sessions.remove(playerId);
        if (removed == null) {
            return;
        }
        try {
            save();
        } catch (IOException exception) {
            sessions.put(playerId, removed);
            throw exception;
        }
    }

    synchronized int size() {
        return sessions.size();
    }

    private void load() throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            FileData data = GSON.fromJson(reader, FileData.class);
            if (data == null || data.version() != FILE_VERSION || data.sessions() == null) {
                throw new IOException("Unsupported or incomplete spectate session state");
            }
            for (Entry entry : data.sessions()) {
                UUID playerId = UUID.fromString(entry.playerId());
                validate(entry.session());
                if (sessions.put(playerId, entry.session()) != null) {
                    throw new IOException("Duplicate spectate recovery entry for " + playerId);
                }
            }
        } catch (JsonParseException | IllegalArgumentException exception) {
            throw new IOException("Invalid spectate session state in " + file, exception);
        }
    }

    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        List<Entry> entries = sessions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .map(entry -> new Entry(entry.getKey().toString(), entry.getValue()))
                .toList();
        FileData data = new FileData(FILE_VERSION, new ArrayList<>(entries));
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(temporary, file,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void validate(StoredSession session) {
        if (session == null
                || session.playerName() == null || session.playerName().isBlank()
                || session.dimension() == null || session.dimension().isBlank()
                || session.gameMode() == null || session.gameMode().isBlank()
                || !Double.isFinite(session.x())
                || !Double.isFinite(session.y())
                || !Double.isFinite(session.z())
                || !Float.isFinite(session.yaw())
                || !Float.isFinite(session.pitch())) {
            throw new IllegalArgumentException("Invalid stored spectate origin");
        }
    }

    record StoredSession(
            String playerName,
            String dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String gameMode) {
        StoredSession {
            validate(new RawSession(playerName, dimension, x, y, z, yaw, pitch, gameMode));
        }

        private static void validate(RawSession session) {
            if (session.playerName() == null || session.playerName().isBlank()
                    || session.dimension() == null || session.dimension().isBlank()
                    || session.gameMode() == null || session.gameMode().isBlank()
                    || !Double.isFinite(session.x())
                    || !Double.isFinite(session.y())
                    || !Double.isFinite(session.z())
                    || !Float.isFinite(session.yaw())
                    || !Float.isFinite(session.pitch())) {
                throw new IllegalArgumentException("Invalid stored spectate origin");
            }
        }
    }

    private record RawSession(
            String playerName,
            String dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String gameMode) {
    }

    private record Entry(String playerId, StoredSession session) {
    }

    private record FileData(int version, List<Entry> sessions) {
    }
}
