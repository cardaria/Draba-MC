package xyz.draba.hardcore;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/** Defines ownership inheritance for automated item transformations. */
public final class ProcessingOwnership {
    private static final ThreadLocal<Deque<FurnaceContext>> FURNACES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<FurnaceFuelContext>> FURNACE_TICKS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<BrewingContext>> BREWING = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<CrafterContext>> CRAFTERS = ThreadLocal.withInitial(ArrayDeque::new);

    private ProcessingOwnership() {
    }

    public static void beforeFurnace(NonNullList<ItemStack> items) {
        ItemStack input = items.get(0);
        ItemStack output = items.get(2);
        FURNACES.get().push(new FurnaceContext(input.getCount(), ItemOwnership.read(input),
                output.getCount(), ItemOwnership.read(output)));
    }

    public static void afterFurnace(NonNullList<ItemStack> items) {
        Deque<FurnaceContext> contexts = FURNACES.get();
        FurnaceContext context = contexts.pop();
        ItemStack input = items.get(0);
        ItemStack output = items.get(2);
        int consumed = Math.max(0, context.inputCount() - input.getCount());
        int produced = Math.max(0, output.getCount() - context.outputCount());
        if (consumed > 0 && produced > 0) {
            OwnershipLedger source = context.inputLedger().takeLast(consumed).taken();
            ItemOwnership.write(output, context.outputLedger().append(inherit(source, produced)));
        }
        if (contexts.isEmpty()) {
            FURNACES.remove();
        }
    }

    public static void beforeFurnaceTick(AbstractFurnaceBlockEntity furnace) {
        ItemStack fuel = furnace.getItem(1);
        FURNACE_TICKS.get().push(new FurnaceFuelContext(fuel.copy(), ItemOwnership.read(fuel)));
    }

    public static void afterFurnaceTick(AbstractFurnaceBlockEntity furnace) {
        Deque<FurnaceFuelContext> contexts = FURNACE_TICKS.get();
        FurnaceFuelContext context = contexts.pop();
        ItemStack fuel = furnace.getItem(1);
        if (!fuel.isEmpty() && !context.sample().isEmpty()
                && !ItemOwnership.sameItemAndComponentsIgnoringOwnership(context.sample(), fuel)) {
            ItemOwnership.write(fuel, inherit(context.ledger().takeLast(1).taken(), fuel.getCount()));
        }
        if (contexts.isEmpty()) {
            FURNACE_TICKS.remove();
        }
    }

    public static void beforeBrewing(NonNullList<ItemStack> items) {
        List<OwnershipLedger> bottles = new ArrayList<>(3);
        for (int slot = 0; slot < 3; slot++) {
            bottles.add(ItemOwnership.read(items.get(slot)));
        }
        ItemStack ingredient = items.get(3);
        BREWING.get().push(new BrewingContext(bottles, ingredient.copy(), ItemOwnership.read(ingredient)));
    }

    public static void afterBrewing(NonNullList<ItemStack> items) {
        Deque<BrewingContext> contexts = BREWING.get();
        BrewingContext context = contexts.pop();
        for (int slot = 0; slot < 3; slot++) {
            if (!items.get(slot).isEmpty()) {
                ItemOwnership.write(items.get(slot), context.bottles().get(slot));
            }
        }
        ItemStack ingredient = items.get(3);
        if (!ingredient.isEmpty() && !context.ingredientSample().isEmpty()
                && !ItemOwnership.sameItemAndComponentsIgnoringOwnership(context.ingredientSample(), ingredient)) {
            ItemOwnership.write(ingredient,
                    inherit(context.ingredientLedger().takeLast(1).taken(), ingredient.getCount()));
        }
        if (contexts.isEmpty()) {
            BREWING.remove();
        }
    }

    public static void ownBrewingRemainder(ItemStack remainder) {
        Deque<BrewingContext> contexts = BREWING.get();
        if (!contexts.isEmpty() && !remainder.isEmpty()) {
            OwnershipLedger source = contexts.peek().ingredientLedger().takeLast(1).taken();
            ItemOwnership.write(remainder, inherit(source, remainder.getCount()));
        }
    }

    /** Marks a crafter result only if every occupied input unit belongs to one life. */
    public static void ownCrafterOutput(CrafterBlockEntity crafter, ItemStack output) {
        OwnershipLedger.OwnerLife owner = null;
        for (ItemStack input : crafter.getItems()) {
            if (input.isEmpty()) {
                continue;
            }
            OwnershipLedger ledger = ItemOwnership.read(input);
            OwnershipLedger.OwnerLife inputOwner = fullSingleOwner(ledger);
            if (inputOwner == null || (owner != null && !OwnershipLedger.sameOwner(owner, inputOwner))) {
                ItemOwnership.clear(output);
                return;
            }
            owner = inputOwner;
        }
        if (owner == null) {
            ItemOwnership.clear(output);
        } else {
            ItemOwnership.markOwned(output, owner);
        }
    }

    public static void beforeCrafter(CrafterBlockEntity crafter) {
        List<ItemStack> samples = new ArrayList<>(crafter.getContainerSize());
        List<OwnershipLedger> ledgers = new ArrayList<>(crafter.getContainerSize());
        for (int slot = 0; slot < crafter.getContainerSize(); slot++) {
            samples.add(crafter.getItem(slot).copy());
            ledgers.add(ItemOwnership.read(crafter.getItem(slot)));
        }
        CRAFTERS.get().push(new CrafterContext(crafter, samples, ledgers));
    }

    /** Preserves ownership into recipe remainders such as empty buckets. */
    public static void afterCrafter() {
        Deque<CrafterContext> contexts = CRAFTERS.get();
        if (contexts.isEmpty()) {
            return;
        }
        CrafterContext context = contexts.pop();
        for (int slot = 0; slot < context.crafter().getContainerSize(); slot++) {
            ItemStack before = context.samples().get(slot);
            ItemStack after = context.crafter().getItem(slot);
            if (after.isEmpty() || before.isEmpty()
                    || ItemOwnership.sameItemAndComponentsIgnoringOwnership(before, after)) {
                continue;
            }
            OwnershipLedger consumedUnit = context.ledgers().get(slot).takeLast(1).taken();
            ItemOwnership.write(after, inherit(consumedUnit, after.getCount()));
        }
        if (contexts.isEmpty()) {
            CRAFTERS.remove();
        }
    }

    private static OwnershipLedger inherit(OwnershipLedger source, int outputCount) {
        OwnershipLedger.OwnerLife owner = fullSingleOwner(source);
        return owner == null ? OwnershipLedger.permanent(outputCount) : OwnershipLedger.owned(owner, outputCount);
    }

    private static OwnershipLedger.OwnerLife fullSingleOwner(OwnershipLedger ledger) {
        if (ledger.totalCount() == 0 || ledger.permanentCount() != 0) {
            return null;
        }
        Map<OwnershipLedger.OwnerLife, Integer> summary = ledger.ownedSummary();
        return summary.size() == 1 ? summary.keySet().iterator().next() : null;
    }

    private record FurnaceContext(int inputCount, OwnershipLedger inputLedger,
                                  int outputCount, OwnershipLedger outputLedger) {
    }

    private record FurnaceFuelContext(ItemStack sample, OwnershipLedger ledger) {
    }

    private record BrewingContext(List<OwnershipLedger> bottles, ItemStack ingredientSample,
                                  OwnershipLedger ingredientLedger) {
    }

    private record CrafterContext(CrafterBlockEntity crafter, List<ItemStack> samples,
                                  List<OwnershipLedger> ledgers) {
    }
}
