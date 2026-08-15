package xyz.draba.hardcore;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpectateTargetSelectorTest {
    @Test
    void noTargetsRemainUnavailable() {
        assertTrue(SpectateTargetSelector.first(List.of()).isEmpty());
        assertTrue(SpectateTargetSelector.cycle(List.of(), "Alice", 1).isEmpty());
    }

    @Test
    void initialAndUnavailableTargetsSelectTheFirstPlayer() {
        List<String> targets = List.of("Alice", "Bob", "Carol");

        assertEquals("Alice", SpectateTargetSelector.first(targets).orElseThrow());
        assertEquals("Alice", SpectateTargetSelector.cycle(targets, "Departed", 1).orElseThrow());
    }

    @Test
    void cyclingWrapsInBothDirections() {
        List<String> targets = List.of("Alice", "Bob", "Carol");

        assertEquals("Alice", SpectateTargetSelector.cycle(targets, "Carol", 1).orElseThrow());
        assertEquals("Carol", SpectateTargetSelector.cycle(targets, "Alice", -1).orElseThrow());
    }

    @Test
    void aSingleTargetAlwaysRemainsSelected() {
        assertEquals("Alice", SpectateTargetSelector.cycle(List.of("Alice"), "Alice", 1).orElseThrow());
        assertEquals("Alice", SpectateTargetSelector.cycle(List.of("Alice"), "Alice", -1).orElseThrow());
    }
}
