package xyz.draba.spectate.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkStatusTooltipTest {
    @Test
    void usesOneColumnWhenAllPlayersFitVertically() {
        assertEquals(1, NetworkStatusTooltip.chooseColumns(15, 320, 240, 300));
    }

    @Test
    void usesTwoColumnsForTallListsWhenTheyFitHorizontally() {
        assertEquals(2, NetworkStatusTooltip.chooseColumns(30, 320, 240, 300));
        assertEquals(2, NetworkStatusTooltip.chooseColumns(15, 320, 180, 300));
    }

    @Test
    void refusesASecondColumnThatWouldLeaveTheScreen() {
        assertEquals(1, NetworkStatusTooltip.chooseColumns(30, 280, 240, 260));
        assertEquals(1, NetworkStatusTooltip.chooseColumns(30, 320, 240, 311));
    }

    @Test
    void leavesRoomForTheSeparatedFooterWhenChoosingRows() {
        assertEquals(1, NetworkStatusTooltip.chooseColumns(17, 320, 240, 300));
        assertEquals(2, NetworkStatusTooltip.chooseColumns(18, 320, 240, 300));
    }
}
