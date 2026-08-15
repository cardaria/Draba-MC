package xyz.draba.spectate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class SpectateTargetSelector {
    private SpectateTargetSelector() {
    }

    static Optional<UUID> first(List<UUID> targets) {
        return targets.isEmpty() ? Optional.empty() : Optional.of(targets.getFirst());
    }

    static Optional<UUID> cycle(List<UUID> targets, UUID current, int direction) {
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        int currentIndex = targets.indexOf(current);
        if (currentIndex < 0) {
            return Optional.of(targets.getFirst());
        }
        return Optional.of(targets.get(Math.floorMod(currentIndex + Integer.signum(direction), targets.size())));
    }
}
