package xyz.draba.resources.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Defines the managed packs in Minecraft's low-to-high resource priority order.
 */
public final class ManagedPackPolicy {
    public static final String FACADE_ID = "draba_resources:managed";
    public static final String CONTINUITY_DEFAULT_ID = "continuity:default";
    public static final String CONTINUITY_GLASS_ID = "continuity:glass_pane_culling_fix";
    public static final String FRESH_ANIMATIONS_ID = "file/Fresh-Animations-1.10.5.zip";
    public static final String FRESH_OBJECTS_ID = "file/Fresh-Animations-Objects-2.1.2.zip";

    /** Later entries override earlier entries in Minecraft's resource manager. */
    public static final List<String> LOAD_ORDER = List.of(
            CONTINUITY_DEFAULT_ID,
            CONTINUITY_GLASS_ID,
            FRESH_ANIMATIONS_ID,
            FRESH_OBJECTS_ID,
            FACADE_ID
    );

    private static final Set<String> MANAGED_IDS = Set.copyOf(LOAD_ORDER);
    private static final Set<String> TECHNICAL_IDS = Set.of(
            CONTINUITY_DEFAULT_ID,
            CONTINUITY_GLASS_ID,
            FRESH_ANIMATIONS_ID,
            FRESH_OBJECTS_ID
    );

    private ManagedPackPolicy() {
    }

    public static boolean isManaged(String id) {
        return MANAGED_IDS.contains(id);
    }

    public static boolean isTechnical(String id) {
        return TECHNICAL_IDS.contains(id);
    }

    public static List<String> enforceOrder(
            Collection<String> selectedIds, Collection<String> availableIds) {
        Set<String> available = new LinkedHashSet<>(availableIds);
        List<String> result = new ArrayList<>();
        Set<String> added = new LinkedHashSet<>();

        for (String id : selectedIds) {
            if (!isManaged(id) && available.contains(id) && added.add(id)) {
                result.add(id);
            }
        }
        for (String id : LOAD_ORDER) {
            if (available.contains(id) && added.add(id)) {
                result.add(id);
            }
        }
        return List.copyOf(result);
    }

    /** Returns the resource-pack screen's high-to-low display order. */
    public static List<String> enforceDisplayOrder(Collection<String> selectedIds) {
        Set<String> selected = new LinkedHashSet<>(selectedIds);
        List<String> result = new ArrayList<>();

        for (int index = LOAD_ORDER.size() - 1; index >= 0; index--) {
            String id = LOAD_ORDER.get(index);
            if (selected.contains(id)) {
                result.add(id);
            }
        }
        for (String id : selectedIds) {
            if (!isManaged(id) && !result.contains(id)) {
                result.add(id);
            }
        }
        return List.copyOf(result);
    }

    public static List<String> missingTechnicalPacks(Collection<String> availableIds) {
        Set<String> available = Set.copyOf(availableIds);
        return TECHNICAL_IDS.stream()
                .filter(id -> !available.contains(id))
                .sorted()
                .toList();
    }
}
