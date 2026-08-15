package xyz.draba.hardcore;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Stores the compact contribution ledger inside the stack's existing custom-data component. */
public final class ItemOwnership {
    static final String ROOT_KEY = "draba_hardcore:ownership";
    private static final String SEGMENTS_KEY = "s";
    private static final String COUNT_KEY = "c";
    private static final String UUID_KEY = "u";
    private static final String LIFE_KEY = "l";
    private static final String NAME_KEY = "n";
    private static final ThreadLocal<Deque<SplitContext>> SPLITS = ThreadLocal.withInitial(ArrayDeque::new);

    private ItemOwnership() {
    }

    static OwnershipLedger read(ItemStack stack) {
        if (stack.isEmpty()) {
            return OwnershipLedger.EMPTY;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return OwnershipLedger.permanent(stack.getCount());
        }
        CompoundTag root = customData.copyTag();
        CompoundTag ownership = root.getCompound(ROOT_KEY).orElse(null);
        if (ownership == null) {
            return OwnershipLedger.permanent(stack.getCount());
        }

        try {
            ListTag serialized = ownership.getList(SEGMENTS_KEY).orElseThrow();
            List<OwnershipLedger.Segment> segments = new ArrayList<>(serialized.size());
            for (int index = 0; index < serialized.size(); index++) {
                CompoundTag segment = serialized.getCompound(index).orElseThrow();
                int count = segment.getInt(COUNT_KEY).orElseThrow();
                OwnershipLedger.OwnerLife owner = null;
                if (segment.contains(UUID_KEY)) {
                    int[] uuid = segment.getIntArray(UUID_KEY).orElseThrow();
                    UUID playerId = UUIDUtil.uuidFromIntArray(uuid);
                    long life = segment.getLong(LIFE_KEY).orElseThrow();
                    if (life <= 0) {
                        throw new IllegalArgumentException("Ownership life must be positive");
                    }
                    owner = new OwnershipLedger.OwnerLife(
                            playerId, life, segment.getStringOr(NAME_KEY, playerId.toString()));
                }
                segments.add(new OwnershipLedger.Segment(owner, count));
            }
            OwnershipLedger parsed = OwnershipLedger.of(segments);
            if (!parsed.hasOwnedUnits()) {
                return OwnershipLedger.permanent(stack.getCount());
            }
            return parsed.alignToCount(stack.getCount());
        } catch (RuntimeException malformed) {
            // Corrupt or foreign-looking metadata must fail safe: never delete those items.
            DrabaHardcore.LOGGER.warn("Ignoring malformed ownership data on {}", stack.getItem(), malformed);
            return OwnershipLedger.permanent(stack.getCount());
        }
    }

    static void write(ItemStack stack, OwnershipLedger ledger) {
        if (stack.isEmpty()) {
            return;
        }
        OwnershipLedger aligned = Objects.requireNonNull(ledger, "ledger").alignToCount(stack.getCount());
        CompoundTag customTag = customDataWithoutOwnership(stack);
        if (aligned.hasOwnedUnits()) {
            CompoundTag ownership = new CompoundTag();
            ListTag serialized = new ListTag();
            for (OwnershipLedger.Segment segment : aligned.segments()) {
                CompoundTag value = new CompoundTag();
                value.putInt(COUNT_KEY, segment.count());
                if (segment.owner() != null) {
                    value.putIntArray(UUID_KEY, UUIDUtil.uuidToIntArray(segment.owner().playerId()));
                    value.putLong(LIFE_KEY, segment.owner().life());
                    value.putString(NAME_KEY, segment.owner().playerName());
                }
                serialized.add(value);
            }
            ownership.put(SEGMENTS_KEY, serialized);
            customTag.put(ROOT_KEY, ownership);
        }
        applyCustomData(stack, customTag);
    }

    public static void clear(ItemStack stack) {
        if (!stack.isEmpty() && hasMetadata(stack)) {
            applyCustomData(stack, customDataWithoutOwnership(stack));
        }
        updateNestedItems(stack, ItemOwnership::clear);
    }

    static void markOwned(ItemStack stack, OwnershipLedger.OwnerLife owner) {
        if (!stack.isEmpty()) {
            write(stack, OwnershipLedger.owned(owner, stack.getCount()));
            updateNestedItems(stack, nested -> markOwned(nested, owner));
        }
    }

    /** Tags contents when a filled container item crosses from a player into storage. */
    static void markNestedOwned(ItemStack stack, OwnershipLedger.OwnerLife owner) {
        if (!stack.isEmpty()) {
            updateNestedItems(stack, nested -> markOwned(nested, owner));
        }
    }

    static int purge(ItemStack stack, BiPredicate<UUID, Long> invalidLife) {
        if (stack.isEmpty()) {
            return 0;
        }
        int removed = 0;
        if (hasMetadata(stack)) {
            OwnershipLedger.PurgeResult result = read(stack).purge(invalidLife);
            removed = result.removedCount();
            if (removed > 0) {
                stack.setCount(result.remaining().totalCount());
                if (!stack.isEmpty()) {
                    write(stack, result.remaining());
                }
            }
        }
        if (!stack.isEmpty()) {
            int[] nestedRemoved = {0};
            updateNestedItems(stack, nested -> nestedRemoved[0] += purge(nested, invalidLife));
            removed += nestedRemoved[0];
        }
        return removed;
    }

    public static boolean sameItemAndComponentsIgnoringOwnership(ItemStack first, ItemStack second) {
        if (!first.is(second.getItem())) {
            return false;
        }
        if (first.isEmpty() && second.isEmpty()) {
            return true;
        }
        if (!hasMetadata(first) && !hasMetadata(second)) {
            return first.getComponents().equals(second.getComponents());
        }
        ItemStack cleanFirst = copyWithoutOwnership(first);
        ItemStack cleanSecond = copyWithoutOwnership(second);
        return cleanFirst.getComponents().equals(cleanSecond.getComponents());
    }

    public static int hashItemAndComponentsIgnoringOwnership(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        ItemStack clean = hasMetadata(stack) ? copyWithoutOwnership(stack) : stack;
        return 31 * (31 + clean.getItem().hashCode()) + clean.getComponents().hashCode();
    }

    public static void beforeSplit(ItemStack source) {
        SPLITS.get().push(new SplitContext(source, read(source)));
    }

    public static void afterSplit(ItemStack source, ItemStack result) {
        Deque<SplitContext> contexts = SPLITS.get();
        if (contexts.isEmpty()) {
            return;
        }
        SplitContext context = contexts.pop();
        if (context.source() != source || result.isEmpty()) {
            if (contexts.isEmpty()) {
                SPLITS.remove();
            }
            return;
        }
        OwnershipLedger.Split split = context.ledger().takeLast(result.getCount());
        if (!source.isEmpty()) {
            write(source, split.remaining());
        }
        write(result, split.taken());
        if (contexts.isEmpty()) {
            SPLITS.remove();
        }
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        if (!hasMetadata(stack)) {
            return;
        }
        OwnershipLedger ledger = read(stack);
        tooltip.add(Component.literal("Hardcore storage ownership").withStyle(ChatFormatting.DARK_GRAY));
        if (ledger.permanentCount() > 0) {
            tooltip.add(Component.literal("  Permanent: " + ledger.permanentCount())
                    .withStyle(ChatFormatting.GRAY));
        }
        ledger.ownedSummary().forEach((owner, count) -> tooltip.add(Component.literal(
                        "  " + owner.playerName() + ": " + count)
                .withStyle(ChatFormatting.GOLD)));
    }

    static boolean hasMetadata(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.copyTag().contains(ROOT_KEY);
    }

    private static ItemStack copyWithoutOwnership(ItemStack stack) {
        ItemStack copy = stack.copy();
        applyCustomData(copy, customDataWithoutOwnership(copy));
        return copy;
    }

    private static CompoundTag customDataWithoutOwnership(ItemStack stack) {
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag copy = existing == null ? new CompoundTag() : existing.copyTag();
        copy.remove(ROOT_KEY);
        return copy;
    }

    private static void applyCustomData(ItemStack stack, CompoundTag customTag) {
        if (customTag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
        }
    }

    private static void updateNestedItems(ItemStack stack, java.util.function.Consumer<ItemStack> action) {
        if (stack.isEmpty()) {
            return;
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null && contents != ItemContainerContents.EMPTY) {
            List<ItemStack> items = contents.allItemsCopyStream().toList();
            boolean changed = applyToCopies(items, action);
            if (changed) {
                stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
            }
        }

        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && bundle != BundleContents.EMPTY) {
            List<ItemStack> items = bundle.itemCopyStream().toList();
            boolean changed = applyToCopies(items, action);
            if (changed) {
                List<ItemStackTemplate> templates = items.stream()
                        .filter(item -> !item.isEmpty())
                        .map(ItemStackTemplate::fromNonEmptyStack)
                        .toList();
                // Selection is transient UI state and is not serialized by BundleContents.
                stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(templates));
            }
        }
    }

    private static boolean applyToCopies(List<ItemStack> items,
                                         java.util.function.Consumer<ItemStack> action) {
        boolean changed = false;
        for (ItemStack item : items) {
            ItemStack before = item.copy();
            action.accept(item);
            changed |= !ItemStack.matches(before, item);
        }
        return changed;
    }

    private record SplitContext(ItemStack source, OwnershipLedger ledger) {
    }
}
