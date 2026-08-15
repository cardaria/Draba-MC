package xyz.draba.spectate.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PauseOverlayLayoutTest {
    @Test
    void standardPauseLayoutKeepsStatusAndButtonSeparated() {
        for (int width : new int[]{320, 640, 1920}) {
            PauseOverlayLayout.Rect button = PauseOverlayLayout.spectateButton(width);
            assertTrue(button.x() >= PauseOverlayLayout.MARGIN);
            assertTrue(button.right() <= width - PauseOverlayLayout.MARGIN);
            assertFalse(overlaps(button, PauseOverlayLayout.mainBadge()));
            assertFalse(overlaps(button, PauseOverlayLayout.hardcoreBadge()));
            assertTrue(button.x() > PauseOverlayLayout.hardcoreBadge().right());
            assertTrue(button.y() == PauseOverlayLayout.hardcoreBadge().y());
        }
    }

    @Test
    void narrowLayoutMovesButtonBelowStatusBadges() {
        PauseOverlayLayout.Rect button = PauseOverlayLayout.spectateButton(220);
        assertTrue(button.y() > PauseOverlayLayout.mainBadge().bottom());
        assertTrue(button.x() == PauseOverlayLayout.MARGIN);
        assertFalse(overlaps(button, PauseOverlayLayout.hardcoreBadge()));
    }

    @Test
    void nativeButtonContainsACompleteMiniatureIcon() {
        assertTrue(PauseOverlayLayout.ICON_SIZE >= 16);
        assertTrue(PauseOverlayLayout.BADGE_HEIGHT
                >= PauseOverlayLayout.ICON_SIZE + PauseOverlayLayout.ICON_PADDING * 2);
    }

    private static boolean overlaps(PauseOverlayLayout.Rect first, PauseOverlayLayout.Rect second) {
        return first.x() < second.right()
                && first.right() > second.x()
                && first.y() < second.bottom()
                && first.bottom() > second.y();
    }
}
