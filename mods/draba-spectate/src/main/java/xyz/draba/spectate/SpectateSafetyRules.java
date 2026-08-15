package xyz.draba.spectate;

import java.util.Optional;

final class SpectateSafetyRules {
    static final int ARMING_TICKS = 100;
    static final int RECENT_DAMAGE_TICKS = 200;
    static final double MAX_ARMING_MOVEMENT_SQUARED = 0.04D;

    private SpectateSafetyRules() {
    }

    static Optional<Blocker> initialBlocker(Snapshot snapshot) {
        if (!snapshot.alive()) {
            return Optional.of(Blocker.DEAD);
        }
        if (snapshot.spectator()) {
            return Optional.of(Blocker.ALREADY_SPECTATOR);
        }
        if (snapshot.passenger()) {
            return Optional.of(Blocker.PASSENGER);
        }
        if (snapshot.sleeping()) {
            return Optional.of(Blocker.SLEEPING);
        }
        if (!snapshot.onGround() || snapshot.fallFlying()) {
            return Optional.of(Blocker.AIRBORNE);
        }
        if (snapshot.onFire() || snapshot.inLava() || snapshot.inWater()) {
            return Optional.of(Blocker.HAZARD);
        }
        if (snapshot.recentlyDamaged()) {
            return Optional.of(Blocker.RECENT_DAMAGE);
        }
        if (snapshot.activeThreat()) {
            return Optional.of(Blocker.ACTIVE_THREAT);
        }
        return Optional.empty();
    }

    static Optional<Blocker> armingBlocker(
            Snapshot snapshot, boolean moved, boolean changedDimension) {
        if (!snapshot.alive()) {
            return Optional.of(Blocker.DEAD);
        }
        if (changedDimension) {
            return Optional.of(Blocker.CHANGED_DIMENSION);
        }
        if (moved) {
            return Optional.of(Blocker.MOVED);
        }
        return initialBlocker(snapshot);
    }

    static boolean movedBeyondAnchor(double distanceSquared) {
        return distanceSquared > MAX_ARMING_MOVEMENT_SQUARED;
    }

    record Snapshot(
            boolean alive,
            boolean spectator,
            boolean passenger,
            boolean sleeping,
            boolean onGround,
            boolean fallFlying,
            boolean onFire,
            boolean inLava,
            boolean inWater,
            boolean recentlyDamaged,
            boolean activeThreat) {
    }

    enum Blocker {
        DEAD(
                "Spectate is unavailable while dead.",
                "Spectate cancelled because you died."),
        ALREADY_SPECTATOR(
                "Spectate is unavailable while already in spectator mode.",
                "Spectate cancelled because you entered spectator mode."),
        PASSENGER(
                "Dismount before spectating.",
                "Spectate cancelled because you mounted something."),
        SLEEPING(
                "Leave the bed before spectating.",
                "Spectate cancelled because you entered a bed."),
        AIRBORNE(
                "Stand safely on the ground before spectating.",
                "Spectate cancelled because you left the ground."),
        HAZARD(
                "Move to a safe, dry position before spectating.",
                "Spectate cancelled because you entered fire, lava, or water."),
        RECENT_DAMAGE(
                "Wait until combat has ended before spectating.",
                "Spectate cancelled because you took damage."),
        ACTIVE_THREAT(
                "A nearby mob is actively targeting you.",
                "Spectate cancelled because a nearby mob targeted you."),
        CHANGED_DIMENSION(
                "Stay in the same dimension while spectate starts.",
                "Spectate cancelled because you changed dimensions."),
        MOVED(
                "Stand still while spectate starts.",
                "Spectate cancelled because you moved.");

        private final String initialMessage;
        private final String cancellationMessage;

        Blocker(String initialMessage, String cancellationMessage) {
            this.initialMessage = initialMessage;
            this.cancellationMessage = cancellationMessage;
        }

        String initialMessage() {
            return initialMessage;
        }

        String cancellationMessage() {
            return cancellationMessage;
        }
    }
}
