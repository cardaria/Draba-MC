package xyz.draba.hardcore;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OwnershipLedgerTest {
    private static final OwnershipLedger.OwnerLife ALICE_ONE = owner(1, 1, "Alice");
    private static final OwnershipLedger.OwnerLife ALICE_TWO = owner(1, 2, "Alice");
    private static final OwnershipLedger.OwnerLife BOB_ONE = owner(2, 1, "Bob");

    @Test
    void normalizesAdjacentRunsButKeepsDifferentLivesSeparate() {
        OwnershipLedger ledger = OwnershipLedger.of(List.of(
                new OwnershipLedger.Segment(ALICE_ONE, 3),
                new OwnershipLedger.Segment(new OwnershipLedger.OwnerLife(ALICE_ONE.playerId(), 1, "Renamed"), 4),
                new OwnershipLedger.Segment(ALICE_TWO, 2),
                new OwnershipLedger.Segment(null, 5),
                new OwnershipLedger.Segment(null, 1)));

        assertEquals(List.of(
                new OwnershipLedger.Segment(new OwnershipLedger.OwnerLife(ALICE_ONE.playerId(), 1, "Renamed"), 7),
                new OwnershipLedger.Segment(ALICE_TWO, 2),
                new OwnershipLedger.Segment(null, 6)), ledger.segments());
        assertEquals(15, ledger.totalCount());
        assertEquals(6, ledger.permanentCount());
        assertEquals(9, ledger.ownedCount());
    }

    @Test
    void partialSplitsTakeTheTopRunsAndPreserveOrder() {
        OwnershipLedger ledger = OwnershipLedger.permanent(32)
                .append(OwnershipLedger.owned(ALICE_ONE, 16))
                .append(OwnershipLedger.owned(BOB_ONE, 16));

        OwnershipLedger.Split split = ledger.takeLast(20);

        assertEquals(List.of(
                new OwnershipLedger.Segment(null, 32),
                new OwnershipLedger.Segment(ALICE_ONE, 12)), split.remaining().segments());
        assertEquals(List.of(
                new OwnershipLedger.Segment(ALICE_ONE, 4),
                new OwnershipLedger.Segment(BOB_ONE, 16)), split.taken().segments());
    }

    @Test
    void purgesOnlyTheInvalidContributionFromAMixedStack() {
        OwnershipLedger ledger = OwnershipLedger.permanent(32)
                .append(OwnershipLedger.owned(ALICE_ONE, 16))
                .append(OwnershipLedger.owned(BOB_ONE, 16));

        OwnershipLedger.PurgeResult result = ledger.purge(
                (playerId, life) -> playerId.equals(ALICE_ONE.playerId()) && life <= 1);

        assertEquals(16, result.removedCount());
        assertEquals(48, result.remaining().totalCount());
        assertEquals(List.of(
                new OwnershipLedger.Segment(null, 32),
                new OwnershipLedger.Segment(BOB_ONE, 16)), result.remaining().segments());
    }

    @Test
    void oldLifePurgeNeverTouchesANewerLife() {
        OwnershipLedger ledger = OwnershipLedger.owned(ALICE_ONE, 7)
                .append(OwnershipLedger.owned(ALICE_TWO, 9));

        OwnershipLedger.PurgeResult result = ledger.purge(
                (playerId, life) -> playerId.equals(ALICE_ONE.playerId()) && life <= 1);

        assertEquals(7, result.removedCount());
        assertEquals(OwnershipLedger.owned(ALICE_TWO, 9).segments(), result.remaining().segments());
    }

    @Test
    void malformedCountsAreSafelyAlignedWithLegacyUnits() {
        OwnershipLedger tooShort = OwnershipLedger.owned(ALICE_ONE, 2).alignToCount(5);
        OwnershipLedger tooLong = OwnershipLedger.owned(ALICE_ONE, 5).alignToCount(2);

        assertEquals(List.of(
                new OwnershipLedger.Segment(ALICE_ONE, 2),
                new OwnershipLedger.Segment(null, 3)), tooShort.segments());
        assertEquals(OwnershipLedger.owned(ALICE_ONE, 2).segments(), tooLong.segments());
        assertFalse(OwnershipLedger.EMPTY.hasOwnedUnits());
        assertTrue(tooShort.hasOwnedUnits());
    }

    private static OwnershipLedger.OwnerLife owner(long uuidSuffix, long life, String name) {
        return new OwnershipLedger.OwnerLife(
                UUID.fromString("00000000-0000-0000-0000-%012d".formatted(uuidSuffix)), life, name);
    }
}
