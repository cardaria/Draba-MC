package xyz.draba.hardcore;

import java.util.List;
import java.util.Optional;

final class SpectateTargetSelector {
    private SpectateTargetSelector() {
    }

    static <T> Optional<T> first(List<T> targets) {
        return targets.isEmpty() ? Optional.empty() : Optional.of(targets.getFirst());
    }

    static <T> Optional<T> cycle(List<T> targets, T current, int direction) {
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        int currentIndex = targets.indexOf(current);
        if (currentIndex < 0) {
            return Optional.of(targets.getFirst());
        }
        int step = direction < 0 ? -1 : 1;
        return Optional.of(targets.get(Math.floorMod(currentIndex + step, targets.size())));
    }
}
