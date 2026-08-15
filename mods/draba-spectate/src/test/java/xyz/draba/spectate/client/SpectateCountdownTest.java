package xyz.draba.spectate.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectateCountdownTest {
    @Test
    void countdownRoundsUpForDisplay() {
        assertEquals(5, SpectateCountdown.displaySeconds(100));
        assertEquals(5, SpectateCountdown.displaySeconds(81));
        assertEquals(4, SpectateCountdown.displaySeconds(80));
        assertEquals(1, SpectateCountdown.displaySeconds(1));
        assertEquals(0, SpectateCountdown.displaySeconds(0));
        assertEquals(0, SpectateCountdown.displaySeconds(-1));
    }
}
