package xyz.draba.resources.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ManagedPackPolicyTest {
    @Test
    void managedPacksAreAlwaysAppendedInLowToHighPriorityOrder() {
        List<String> available = List.of(
                "vanilla",
                "file/Personal.zip",
                ManagedPackPolicy.FRESH_OBJECTS_ID,
                ManagedPackPolicy.FACADE_ID,
                ManagedPackPolicy.CONTINUITY_GLASS_ID,
                ManagedPackPolicy.FRESH_ANIMATIONS_ID,
                ManagedPackPolicy.CONTINUITY_DEFAULT_ID);

        assertEquals(List.of(
                        "vanilla",
                        "file/Personal.zip",
                        ManagedPackPolicy.CONTINUITY_DEFAULT_ID,
                        ManagedPackPolicy.CONTINUITY_GLASS_ID,
                        ManagedPackPolicy.FRESH_ANIMATIONS_ID,
                        ManagedPackPolicy.FRESH_OBJECTS_ID,
                        ManagedPackPolicy.FACADE_ID),
                ManagedPackPolicy.enforceOrder(
                        List.of(ManagedPackPolicy.FRESH_OBJECTS_ID,
                                "vanilla",
                                "file/Personal.zip",
                                ManagedPackPolicy.FACADE_ID),
                        available));
    }

    @Test
    void missingPacksAreSkippedWithoutRemovingPersonalPacks() {
        assertEquals(List.of("vanilla", ManagedPackPolicy.FACADE_ID),
                ManagedPackPolicy.enforceOrder(
                        List.of("vanilla"),
                        List.of("vanilla", ManagedPackPolicy.FACADE_ID)));
        assertEquals(4, ManagedPackPolicy.missingTechnicalPacks(
                List.of(ManagedPackPolicy.FACADE_ID)).size());
    }

    @Test
    void onlyTechnicalEntriesAreHidden() {
        assertTrue(ManagedPackPolicy.isTechnical(ManagedPackPolicy.FRESH_ANIMATIONS_ID));
        assertFalse(ManagedPackPolicy.isTechnical(ManagedPackPolicy.FACADE_ID));
        assertFalse(ManagedPackPolicy.isTechnical("file/Personal.zip"));
    }

    @Test
    void screenOrderIsCorrectedImmediatelyAfterSelectingAPersonalPack() {
        assertEquals(List.of(
                        ManagedPackPolicy.FACADE_ID,
                        ManagedPackPolicy.FRESH_OBJECTS_ID,
                        ManagedPackPolicy.FRESH_ANIMATIONS_ID,
                        ManagedPackPolicy.CONTINUITY_GLASS_ID,
                        ManagedPackPolicy.CONTINUITY_DEFAULT_ID,
                        "file/Personal.zip",
                        "vanilla"),
                ManagedPackPolicy.enforceDisplayOrder(List.of(
                        "file/Personal.zip",
                        ManagedPackPolicy.FACADE_ID,
                        ManagedPackPolicy.FRESH_OBJECTS_ID,
                        ManagedPackPolicy.FRESH_ANIMATIONS_ID,
                        ManagedPackPolicy.CONTINUITY_GLASS_ID,
                        ManagedPackPolicy.CONTINUITY_DEFAULT_ID,
                        "vanilla")));
    }
}
