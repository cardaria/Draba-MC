package xyz.draba.hardcore;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ItemOwnershipTest {
    private static Holder<Item> testItem;
    private static final OwnershipLedger.OwnerLife ALICE = new OwnershipLedger.OwnerLife(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), 7, "Alice");
    private static final OwnershipLedger.OwnerLife BOB = new OwnershipLedger.OwnerLife(
            UUID.fromString("00000000-0000-0000-0000-000000000002"), 3, "Bob");

    @BeforeAll
    static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Item item = net.minecraft.world.item.Items.STONE;
        item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64)
                .build());
        testItem = item.builtInRegistryHolder();
    }

    @Test
    void roundTripPreservesMixedLedgerAndUnrelatedCustomData() {
        ItemStack stack = stack(64);
        CompoundTag unrelated = new CompoundTag();
        unrelated.putString("another_mod", "keep me");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated));
        OwnershipLedger ledger = OwnershipLedger.permanent(32)
                .append(OwnershipLedger.owned(ALICE, 16))
                .append(OwnershipLedger.owned(BOB, 16));

        ItemOwnership.write(stack, ledger);

        assertEquals(ledger.segments(), ItemOwnership.read(stack).segments());
        assertEquals("keep me", stack.get(DataComponents.CUSTOM_DATA).copyTag()
                .getString("another_mod").orElseThrow());
    }

    @Test
    void removingTheLastOwnedShareRemovesOnlyPrivateMetadata() {
        ItemStack stack = stack(8);
        CompoundTag unrelated = new CompoundTag();
        unrelated.putInt("value", 42);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated));
        ItemOwnership.markOwned(stack, ALICE);

        int removed = ItemOwnership.purge(stack,
                (playerId, life) -> playerId.equals(ALICE.playerId()) && life == ALICE.life());

        assertEquals(8, removed);
        assertTrue(stack.isEmpty());
    }

    @Test
    void ownershipCanBeClearedWithoutTouchingOtherCustomData() {
        ItemStack stack = stack(4);
        CompoundTag unrelated = new CompoundTag();
        unrelated.putBoolean("foreign", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated));
        ItemOwnership.markOwned(stack, ALICE);

        ItemOwnership.clear(stack);

        assertFalse(ItemOwnership.hasMetadata(stack));
        assertTrue(stack.get(DataComponents.CUSTOM_DATA).copyTag().getBoolean("foreign").orElseThrow());
        assertEquals(4, ItemOwnership.read(stack).permanentCount());
    }

    @Test
    void malformedMetadataFailsPermanent() {
        ItemStack stack = stack(5);
        CompoundTag malformed = new CompoundTag();
        malformed.put(ItemOwnership.ROOT_KEY, new CompoundTag());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(malformed));

        OwnershipLedger ledger = ItemOwnership.read(stack);

        assertEquals(5, ledger.permanentCount());
        assertFalse(ledger.hasOwnedUnits());
    }

    @Test
    void stackComparisonIgnoresOnlyThePrivateLedger() {
        ItemStack first = stack(4);
        ItemStack second = stack(4);
        ItemOwnership.markOwned(first, ALICE);
        ItemOwnership.markOwned(second, BOB);

        assertTrue(ItemOwnership.sameItemAndComponentsIgnoringOwnership(first, second));

        CompoundTag foreign = second.get(DataComponents.CUSTOM_DATA).copyTag();
        foreign.putInt("foreign", 1);
        second.set(DataComponents.CUSTOM_DATA, CustomData.of(foreign));
        assertFalse(ItemOwnership.sameItemAndComponentsIgnoringOwnership(first, second));
    }

    @Test
    void splitMovesTopContributionsInExactQuantities() {
        ItemStack source = stack(64);
        ItemOwnership.write(source, OwnershipLedger.permanent(32)
                .append(OwnershipLedger.owned(ALICE, 16))
                .append(OwnershipLedger.owned(BOB, 16)));

        ItemOwnership.beforeSplit(source);
        ItemStack taken = source.copyWithCount(20);
        source.shrink(20);
        ItemOwnership.afterSplit(source, taken);

        assertEquals(List.of(
                new OwnershipLedger.Segment(null, 32),
                new OwnershipLedger.Segment(ALICE, 12)), ItemOwnership.read(source).segments());
        assertEquals(List.of(
                new OwnershipLedger.Segment(ALICE, 4),
                new OwnershipLedger.Segment(BOB, 16)), ItemOwnership.read(taken).segments());
    }

    @Test
    void hopperMergeRetainsBothDestinationAndMovingShares() {
        net.minecraft.world.SimpleContainer destination = new net.minecraft.world.SimpleContainer(1);
        ItemStack existing = stack(32);
        ItemOwnership.markOwned(existing, ALICE);
        destination.setItem(0, existing);
        ItemStack moving = stack(32);
        ItemOwnership.markOwned(moving, BOB);

        TransferOwnership.beforeHopperInsert(destination, moving, 0);
        destination.getItem(0).grow(32);
        moving.shrink(32);
        TransferOwnership.afterHopperInsert(ItemStack.EMPTY);

        assertEquals(List.of(
                new OwnershipLedger.Segment(ALICE, 32),
                new OwnershipLedger.Segment(BOB, 32)),
                ItemOwnership.read(destination.getItem(0)).segments());
    }

    @Test
    void nestedShulkerContentsFollowOuterPlayerHandling() {
        ItemStack inner = stack(5);
        ItemStack outer = stack(1);
        outer.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(inner)));

        ItemOwnership.markOwned(outer, ALICE);
        ItemStack ownedInner = outer.get(DataComponents.CONTAINER).allItemsCopyStream().findFirst().orElseThrow();
        assertEquals(5, ItemOwnership.read(ownedInner).ownedCount());

        ItemOwnership.clear(outer);
        ItemStack clearedInner = outer.get(DataComponents.CONTAINER).allItemsCopyStream().findFirst().orElseThrow();
        assertFalse(ItemOwnership.hasMetadata(outer));
        assertFalse(ItemOwnership.hasMetadata(clearedInner));
    }

    @Test
    void filledContainerDepositTagsNestedItemsForDispenserPlacement() {
        ItemStack inner = stack(5);
        ItemStack outer = stack(1);
        outer.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(inner)));

        ItemOwnership.write(outer, OwnershipLedger.owned(ALICE, 1));
        ItemOwnership.markNestedOwned(outer, ALICE);

        ItemStack ownedInner = outer.get(DataComponents.CONTAINER)
                .allItemsCopyStream().findFirst().orElseThrow();
        assertEquals(5, ItemOwnership.read(ownedInner).ownedCount());
        assertEquals(ALICE, ItemOwnership.read(ownedInner).segments().getFirst().owner());
    }

    @Test
    void nestedBundleContentsAreOwnedAndPurgedExactly() {
        ItemStack aliceItems = stack(5);
        ItemStack permanentItems = stack(3);
        ItemOwnership.markOwned(aliceItems, ALICE);
        ItemStack bundle = stack(1);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(
                ItemStackTemplate.fromNonEmptyStack(aliceItems),
                ItemStackTemplate.fromNonEmptyStack(permanentItems))));

        int removed = ItemOwnership.purge(bundle,
                (playerId, life) -> playerId.equals(ALICE.playerId()) && life == ALICE.life());

        assertEquals(5, removed);
        List<ItemStack> remaining = bundle.get(DataComponents.BUNDLE_CONTENTS).itemCopyStream().toList();
        assertEquals(1, remaining.size());
        assertEquals(3, remaining.getFirst().getCount());
        assertFalse(ItemOwnership.hasMetadata(remaining.getFirst()));
    }

    @Test
    void inventorySorterMergePreservesEveryContribution() {
        net.minecraft.world.SimpleContainer left = new net.minecraft.world.SimpleContainer(1);
        net.minecraft.world.SimpleContainer right = new net.minecraft.world.SimpleContainer(1);
        net.minecraft.world.CompoundContainer storage = new net.minecraft.world.CompoundContainer(left, right);
        ItemStack alice = stack(32);
        ItemStack bob = stack(32);
        ItemOwnership.markOwned(alice, ALICE);
        ItemOwnership.markOwned(bob, BOB);
        storage.setItem(0, alice);
        storage.setItem(1, bob);

        ContainerSortOwnership.beforeSet(storage, 0, List.of(stack(64), ItemStack.EMPTY));
        ItemStack merged = alice.copyWithCount(64);
        storage.setItem(0, merged);
        storage.setItem(1, ItemStack.EMPTY);
        ContainerSortOwnership.afterSet();

        assertEquals(List.of(
                new OwnershipLedger.Segment(ALICE, 32),
                new OwnershipLedger.Segment(BOB, 32)), ItemOwnership.read(storage.getItem(0)).segments());
    }

    @Test
    void groundItemMergePreservesBothLedgers() {
        ItemStack target = stack(20);
        ItemStack source = stack(15);
        ItemOwnership.markOwned(target, ALICE);
        ItemOwnership.markOwned(source, BOB);

        TransferOwnership.beforeItemMerge(target, source);
        ItemStack merged = target.copyWithCount(35);
        source.shrink(15);
        TransferOwnership.afterItemMerge(merged);

        assertEquals(List.of(
                new OwnershipLedger.Segment(ALICE, 20),
                new OwnershipLedger.Segment(BOB, 15)), ItemOwnership.read(merged).segments());
    }

    private static ItemStack stack(int count) {
        return new ItemStack(testItem, count, DataComponentPatch.EMPTY);
    }
}
