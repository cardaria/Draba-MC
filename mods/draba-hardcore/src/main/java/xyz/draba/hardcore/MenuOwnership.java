package xyz.draba.hardcore;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/** Reconciles an entire server-side menu click as one ownership transaction. */
public final class MenuOwnership {
    private MenuOwnership() {
    }

    public static Snapshot capture(AbstractContainerMenu menu, int slotIndex, int button,
                                   ContainerInput input, net.minecraft.server.level.ServerPlayer player) {
        return new Snapshot(menu, slotIndex, button, input, player,
                collect(menu, true), snapshotPlayer(menu));
    }

    public static void reconcile(Snapshot snapshot) {
        List<StackGroup> after = collect(snapshot.menu(), false);
        List<StackGroup> all = new ArrayList<>(snapshot.before());
        for (StackGroup group : after) {
            if (find(all, group.sample()) == null) {
                all.add(new StackGroup(group.sample(), OwnershipLedger.EMPTY, 0, List.of()));
            }
        }

        OwnershipLedger.OwnerLife actor = DrabaHardcore.ownerLife(snapshot.player());
        for (StackGroup prior : all) {
            StackGroup current = find(after, prior.sample());
            int afterCount = current == null ? 0 : current.count();
            int deposited = Math.max(0, afterCount - prior.count());
            OwnershipLedger finalLedger;
            if (afterCount > prior.count()) {
                finalLedger = prior.ledger().append(
                        OwnershipLedger.owned(actor, deposited));
            } else {
                finalLedger = prior.ledger().takeLast(prior.count() - afterCount).remaining();
            }

            // A number-key swap can replace an equal number of identical items.
            // In that one case net totals conceal a withdrawal plus a deposit.
            if (snapshot.input() == ContainerInput.SWAP
                    && isPersistentSlot(snapshot.menu(), snapshot.slotIndex())
                    && beforePlayerCount(snapshot, prior.sample()) != afterPlayerCount(snapshot, prior.sample())
                    && afterCount == prior.count()) {
                deposited = Math.max(0,
                        beforePlayerCount(snapshot, prior.sample()) - afterPlayerCount(snapshot, prior.sample()));
                if (deposited > 0) {
                    finalLedger = prior.ledger().takeLast(deposited).remaining()
                            .append(OwnershipLedger.owned(actor, deposited));
                }
            }
            if (current != null) {
                distribute(current.stacks(), finalLedger, actor, deposited);
            }
        }
        clearPlayerSide(snapshot.menu());
    }

    private static List<StackGroup> collect(AbstractContainerMenu menu, boolean readLedger) {
        List<StackGroup> groups = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (!isPersistent(slot.container)) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            StackGroup group = find(groups, stack);
            if (group == null) {
                group = new StackGroup(stack.copyWithCount(1), OwnershipLedger.EMPTY, 0, new ArrayList<>());
                groups.add(group);
            }
            group.stacks().add(stack);
            group.add(stack.getCount(), readLedger ? ItemOwnership.read(stack) : OwnershipLedger.EMPTY);
        }
        return groups;
    }

    private static List<CountedStack> snapshotPlayer(AbstractContainerMenu menu) {
        List<CountedStack> result = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (isPersistent(slot.container) || slot.getItem().isEmpty()) {
                continue;
            }
            addCount(result, slot.getItem());
        }
        if (!menu.getCarried().isEmpty()) {
            addCount(result, menu.getCarried());
        }
        return result;
    }

    private static void addCount(List<CountedStack> result, ItemStack stack) {
        CountedStack existing = result.stream()
                .filter(value -> ItemOwnership.sameItemAndComponentsIgnoringOwnership(value.sample(), stack))
                .findFirst().orElse(null);
        if (existing == null) {
            result.add(new CountedStack(stack.copyWithCount(1), stack.getCount()));
        } else {
            existing.count += stack.getCount();
        }
    }

    private static int beforePlayerCount(Snapshot snapshot, ItemStack sample) {
        return snapshot.playerBefore().stream()
                .filter(value -> ItemOwnership.sameItemAndComponentsIgnoringOwnership(value.sample(), sample))
                .mapToInt(CountedStack::count).sum();
    }

    private static int afterPlayerCount(Snapshot snapshot, ItemStack sample) {
        return snapshotPlayer(snapshot.menu()).stream()
                .filter(value -> ItemOwnership.sameItemAndComponentsIgnoringOwnership(value.sample(), sample))
                .mapToInt(CountedStack::count).sum();
    }

    private static void distribute(List<ItemStack> stacks, OwnershipLedger ledger,
                                   OwnershipLedger.OwnerLife actor, int deposited) {
        OwnershipLedger remaining = ledger;
        int nestedDeposits = deposited;
        for (int index = stacks.size() - 1; index >= 0; index--) {
            ItemStack stack = stacks.get(index);
            OwnershipLedger.Split split = remaining.takeLast(stack.getCount());
            ItemOwnership.write(stack, split.taken());
            int depositedInStack = Math.min(nestedDeposits, stack.getCount());
            if (depositedInStack == stack.getCount()) {
                ItemOwnership.markNestedOwned(stack, actor);
            }
            nestedDeposits -= depositedInStack;
            remaining = split.remaining();
        }
    }

    private static void clearPlayerSide(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (!isPersistent(slot.container)) {
                ItemOwnership.clear(slot.getItem());
            }
        }
        ItemOwnership.clear(menu.getCarried());
    }

    private static StackGroup find(List<StackGroup> groups, ItemStack sample) {
        return groups.stream()
                .filter(group -> ItemOwnership.sameItemAndComponentsIgnoringOwnership(group.sample(), sample))
                .findFirst().orElse(null);
    }

    private static boolean isPersistentSlot(AbstractContainerMenu menu, int index) {
        return index >= 0 && index < menu.slots.size() && isPersistent(menu.slots.get(index).container);
    }

    static boolean isPersistent(Container container) {
        if (container instanceof Inventory || container instanceof PlayerEnderChestContainer) {
            return false;
        }
        return container instanceof BlockEntity
                || container instanceof CompoundContainer
                || container instanceof ContainerEntity;
    }

    public record Snapshot(AbstractContainerMenu menu, int slotIndex, int button, ContainerInput input,
                           net.minecraft.server.level.ServerPlayer player, List<StackGroup> before,
                           List<CountedStack> playerBefore) {
    }

    public static final class StackGroup {
        private final ItemStack sample;
        private OwnershipLedger ledger;
        private int count;
        private final List<ItemStack> stacks;

        StackGroup(ItemStack sample, OwnershipLedger ledger, int count, List<ItemStack> stacks) {
            this.sample = sample;
            this.ledger = ledger;
            this.count = count;
            this.stacks = stacks;
        }

        void add(int addedCount, OwnershipLedger addedLedger) {
            count += addedCount;
            ledger = ledger.append(addedLedger);
        }

        ItemStack sample() { return sample; }
        OwnershipLedger ledger() { return ledger; }
        int count() { return count; }
        List<ItemStack> stacks() { return stacks; }
    }

    public static final class CountedStack {
        private final ItemStack sample;
        private int count;

        CountedStack(ItemStack sample, int count) {
            this.sample = sample;
            this.count = count;
        }

        ItemStack sample() { return sample; }
        int count() { return count; }
    }
}
