package xyz.draba.hardcore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Run-length encoded ownership for the indistinguishable units in one item stack.
 * A {@code null} owner is a permanent (grandfathered or naturally automated) unit.
 * Segments are ordered bottom-to-top; splits remove the newest/top units first.
 */
final class OwnershipLedger {
    static final OwnershipLedger EMPTY = new OwnershipLedger(List.of());

    private final List<Segment> segments;
    private final int totalCount;

    private OwnershipLedger(List<Segment> input) {
        List<Segment> normalized = new ArrayList<>();
        int total = 0;
        for (Segment segment : input) {
            if (segment.count() <= 0) {
                continue;
            }
            if (total > Integer.MAX_VALUE - segment.count()) {
                throw new IllegalArgumentException("Ownership count overflow");
            }
            if (!normalized.isEmpty()) {
                Segment previous = normalized.getLast();
                if (sameOwner(previous.owner(), segment.owner())) {
                    OwnerLife currentName = segment.owner() != null ? segment.owner() : previous.owner();
                    normalized.set(normalized.size() - 1,
                            new Segment(currentName, previous.count() + segment.count()));
                    total += segment.count();
                    continue;
                }
            }
            normalized.add(segment);
            total += segment.count();
        }
        this.segments = List.copyOf(normalized);
        this.totalCount = total;
    }

    static OwnershipLedger of(List<Segment> segments) {
        return segments.isEmpty() ? EMPTY : new OwnershipLedger(segments);
    }

    static OwnershipLedger permanent(int count) {
        return count <= 0 ? EMPTY : of(List.of(new Segment(null, count)));
    }

    static OwnershipLedger owned(OwnerLife owner, int count) {
        Objects.requireNonNull(owner, "owner");
        return count <= 0 ? EMPTY : of(List.of(new Segment(owner, count)));
    }

    List<Segment> segments() {
        return segments;
    }

    int totalCount() {
        return totalCount;
    }

    int permanentCount() {
        return segments.stream().filter(segment -> segment.owner() == null)
                .mapToInt(Segment::count).sum();
    }

    int ownedCount() {
        return totalCount - permanentCount();
    }

    boolean hasOwnedUnits() {
        return ownedCount() > 0;
    }

    OwnershipLedger append(OwnershipLedger other) {
        if (other.totalCount == 0) {
            return this;
        }
        if (totalCount == 0) {
            return other;
        }
        List<Segment> combined = new ArrayList<>(segments.size() + other.segments.size());
        combined.addAll(segments);
        combined.addAll(other.segments);
        return of(combined);
    }

    OwnershipLedger alignToCount(int count) {
        if (count <= 0) {
            return EMPTY;
        }
        if (totalCount == count) {
            return this;
        }
        if (totalCount < count) {
            return append(permanent(count - totalCount));
        }
        return takeLast(totalCount - count).remaining();
    }

    Split takeLast(int requested) {
        int amount = Math.max(0, Math.min(requested, totalCount));
        if (amount == 0) {
            return new Split(this, EMPTY);
        }
        if (amount == totalCount) {
            return new Split(EMPTY, this);
        }

        List<Segment> remaining = new ArrayList<>(segments);
        List<Segment> takenReversed = new ArrayList<>();
        int left = amount;
        while (left > 0) {
            Segment last = remaining.removeLast();
            int moved = Math.min(left, last.count());
            takenReversed.add(new Segment(last.owner(), moved));
            int kept = last.count() - moved;
            if (kept > 0) {
                remaining.add(new Segment(last.owner(), kept));
            }
            left -= moved;
        }
        List<Segment> taken = new ArrayList<>(takenReversed.size());
        for (int index = takenReversed.size() - 1; index >= 0; index--) {
            taken.add(takenReversed.get(index));
        }
        return new Split(of(remaining), of(taken));
    }

    PurgeResult purge(BiPredicate<UUID, Long> invalidLife) {
        List<Segment> kept = new ArrayList<>(segments.size());
        int removed = 0;
        for (Segment segment : segments) {
            OwnerLife owner = segment.owner();
            if (owner != null && invalidLife.test(owner.playerId(), owner.life())) {
                removed += segment.count();
            } else {
                kept.add(segment);
            }
        }
        return new PurgeResult(of(kept), removed);
    }

    Map<OwnerLife, Integer> ownedSummary() {
        Map<OwnerLife, Integer> summary = new LinkedHashMap<>();
        for (Segment segment : segments) {
            if (segment.owner() == null) {
                continue;
            }
            OwnerLife existingKey = summary.keySet().stream()
                    .filter(key -> sameOwner(key, segment.owner()))
                    .findFirst().orElse(segment.owner());
            summary.merge(existingKey, segment.count(), Integer::sum);
        }
        return summary;
    }

    static boolean sameOwner(OwnerLife first, OwnerLife second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.playerId().equals(second.playerId()) && first.life() == second.life();
    }

    record OwnerLife(UUID playerId, long life, String playerName) {
        OwnerLife {
            Objects.requireNonNull(playerId, "playerId");
            playerName = playerName == null || playerName.isBlank() ? playerId.toString() : playerName;
        }
    }

    record Segment(OwnerLife owner, int count) {
        Segment {
            if (count <= 0) {
                throw new IllegalArgumentException("Ownership segment count must be positive");
            }
        }
    }

    record Split(OwnershipLedger remaining, OwnershipLedger taken) {
    }

    record PurgeResult(OwnershipLedger remaining, int removedCount) {
    }
}
