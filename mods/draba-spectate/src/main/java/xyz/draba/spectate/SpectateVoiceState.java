package xyz.draba.spectate;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class SpectateVoiceState {
    private static volatile Snapshot current = Snapshot.empty();

    private SpectateVoiceState() {
    }

    static Snapshot snapshot() {
        return current;
    }

    static void publish(
            Map<UUID, Session> sessions,
            Map<UUID, SpectateVoiceRouting.VoicePosition> players) {
        current = new Snapshot(sessions, players);
    }

    static void clear() {
        current = Snapshot.empty();
    }

    record Session(SpectateVoiceRouting.VoicePosition origin, UUID targetId) {
        Session {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(targetId, "targetId");
        }
    }

    record Snapshot(
            Map<UUID, Session> sessions,
            Map<UUID, SpectateVoiceRouting.VoicePosition> players) {
        Snapshot {
            sessions = Map.copyOf(sessions);
            players = Map.copyOf(players);
        }

        static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of());
        }

        boolean isWatching(UUID playerId) {
            return playerId != null && sessions.containsKey(playerId);
        }

        SpectateVoiceRouting.VoicePosition effectivePosition(UUID playerId) {
            Session session = sessions.get(playerId);
            return session == null ? players.get(playerId) : session.origin();
        }

        SpectateVoiceRouting.VoicePosition cameraPosition(UUID playerId) {
            Session session = sessions.get(playerId);
            return session == null ? players.get(playerId) : players.get(session.targetId());
        }
    }
}
