package xyz.draba.hardcore;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Preserves contribution totals when Inventory Sorter rewrites container slots directly. */
public final class ContainerSortOwnership {
    private static final ThreadLocal<Deque<Snapshot>> SORTS = ThreadLocal.withInitial(ArrayDeque::new);

    private ContainerSortOwnership() {
    }

    public static void beforeSet(Container container, int start, List<ItemStack> replacement) {
        SORTS.get().push(new Snapshot(container, start, replacement.size(), groups(container, start, replacement.size())));
    }

    public static void afterSet() {
        Deque<Snapshot> snapshots = SORTS.get();
        if (snapshots.isEmpty()) {
            return;
        }
        Snapshot snapshot = snapshots.pop();
        if (MenuOwnership.isPersistent(snapshot.container())) {
            List<Group> after = groups(snapshot.container(), snapshot.start(), snapshot.length());
            for (Group current : after) {
                Group prior = find(snapshot.before(), current.sample());
                OwnershipLedger ledger = prior == null
                        ? OwnershipLedger.permanent(current.count())
                        : prior.ledger().alignToCount(current.count());
                distribute(current.stacks(), ledger);
            }
        } else {
            for (int slot = snapshot.start(); slot < snapshot.start() + snapshot.length(); slot++) {
                ItemOwnership.clear(snapshot.container().getItem(slot));
            }
        }
        if (snapshots.isEmpty()) {
            SORTS.remove();
        }
    }

    private static List<Group> groups(Container container, int start, int length) {
        List<Group> groups = new ArrayList<>();
        int end = Math.min(container.getContainerSize(), start + length);
        for (int slot = Math.max(0, start); slot < end; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Group group = find(groups, stack);
            if (group == null) {
                group = new Group(stack.copyWithCount(1));
                groups.add(group);
            }
            group.count += stack.getCount();
            group.ledger = group.ledger.append(ItemOwnership.read(stack));
            group.stacks.add(stack);
        }
        return groups;
    }

    private static Group find(List<Group> groups, ItemStack stack) {
        return groups.stream()
                .filter(group -> ItemOwnership.sameItemAndComponentsIgnoringOwnership(group.sample(), stack))
                .findFirst().orElse(null);
    }

    private static void distribute(List<ItemStack> stacks, OwnershipLedger ledger) {
        OwnershipLedger remaining = ledger;
        for (int index = stacks.size() - 1; index >= 0; index--) {
            ItemStack stack = stacks.get(index);
            OwnershipLedger.Split split = remaining.takeLast(stack.getCount());
            ItemOwnership.write(stack, split.taken());
            remaining = split.remaining();
        }
    }

    private record Snapshot(Container container, int start, int length, List<Group> before) {
    }

    private static final class Group {
        private final ItemStack sample;
        private final List<ItemStack> stacks = new ArrayList<>();
        private OwnershipLedger ledger = OwnershipLedger.EMPTY;
        private int count;

        private Group(ItemStack sample) {
            this.sample = sample;
        }

        private ItemStack sample() {
            return sample;
        }

        private List<ItemStack> stacks() {
            return stacks;
        }

        private OwnershipLedger ledger() {
            return ledger;
        }

        private int count() {
            return count;
        }
    }
}
