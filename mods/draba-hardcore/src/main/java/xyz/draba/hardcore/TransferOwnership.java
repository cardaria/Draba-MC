package xyz.draba.hardcore;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/** Ownership bookkeeping for vanilla automation paths that merge stack counts directly. */
public final class TransferOwnership {
    private static final ThreadLocal<Deque<HopperContext>> HOPPERS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<MergeContext>> ITEM_MERGES = ThreadLocal.withInitial(ArrayDeque::new);

    private TransferOwnership() {
    }

    public static void beforeHopperInsert(Container destination, ItemStack moving, int slot) {
        ItemStack target = destination.getItem(slot);
        HOPPERS.get().push(new HopperContext(destination, slot, moving.getCount(),
                ItemOwnership.read(moving), target.getCount(), ItemOwnership.read(target)));
    }

    public static void afterHopperInsert(ItemStack remainder) {
        Deque<HopperContext> contexts = HOPPERS.get();
        if (contexts.isEmpty()) {
            return;
        }
        HopperContext context = contexts.pop();
        ItemStack target = context.destination().getItem(context.slot());
        int moved = Math.max(0, context.movingCount() - remainder.getCount());
        if (moved > 0 && !target.isEmpty()) {
            OwnershipLedger.Split split = context.movingLedger().takeLast(moved);
            ItemOwnership.write(target, context.targetLedger().append(split.taken()));
            if (!remainder.isEmpty()) {
                ItemOwnership.write(remainder, split.remaining());
            }
        }
        if (contexts.isEmpty()) {
            HOPPERS.remove();
        }
    }

    public static void beforeItemMerge(ItemStack target, ItemStack source) {
        ITEM_MERGES.get().push(new MergeContext(target.getCount(), ItemOwnership.read(target),
                source, source.getCount(), ItemOwnership.read(source)));
    }

    public static void afterItemMerge(ItemStack mergedTarget) {
        Deque<MergeContext> contexts = ITEM_MERGES.get();
        if (contexts.isEmpty()) {
            return;
        }
        MergeContext context = contexts.pop();
        int moved = Math.max(0, mergedTarget.getCount() - context.targetCount());
        if (moved > 0) {
            OwnershipLedger.Split split = context.sourceLedger().takeLast(moved);
            ItemOwnership.write(mergedTarget, context.targetLedger().append(split.taken()));
            if (!context.source().isEmpty()) {
                ItemOwnership.write(context.source(), split.remaining());
            }
        }
        if (contexts.isEmpty()) {
            ITEM_MERGES.remove();
        }
    }

    private record HopperContext(Container destination, int slot, int movingCount,
                                 OwnershipLedger movingLedger, int targetCount,
                                 OwnershipLedger targetLedger) {
    }

    private record MergeContext(int targetCount, OwnershipLedger targetLedger,
                                ItemStack source, int sourceCount, OwnershipLedger sourceLedger) {
    }
}
