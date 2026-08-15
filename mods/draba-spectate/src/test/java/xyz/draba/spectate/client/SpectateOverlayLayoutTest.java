package xyz.draba.spectate.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpectateOverlayLayoutTest {
    @Test
    void controlPanelUsesVanillaWidgetGeometry() {
        SpectateOverlayLayout.Controls controls =
                SpectateOverlayLayout.controls(320, 240);

        assertEquals(20, controls.panel().height());
        assertEquals(2, controls.panel().x() - controls.previous().right());
        assertEquals(2, controls.next().x() - controls.panel().right());
        assertEquals(controls.panel().y() + 29, controls.hintY());
    }

    @Test
    void textIsVerticallyCenteredInsideBothPanels() {
        SpectateOverlayLayout.Controls controls =
                SpectateOverlayLayout.controls(640, 360);
        SpectateOverlayLayout.Rect countdown =
                SpectateOverlayLayout.countdown(640, 360);

        assertEquals(controls.panel().y() + 6,
                controls.panel().y() + SpectateOverlayLayout.TEXT_TOP_OFFSET);
        assertEquals(countdown.y() + 6,
                countdown.y() + SpectateOverlayLayout.TEXT_TOP_OFFSET);
    }

    @Test
    void controlsAndCountdownRemainInsideNormalGuiWidths() {
        for (int width : new int[]{320, 640, 1920}) {
            SpectateOverlayLayout.Controls controls =
                    SpectateOverlayLayout.controls(width, 240);
            SpectateOverlayLayout.Rect countdown =
                    SpectateOverlayLayout.countdown(width, 240);

            assertTrue(controls.previous().x() >= 0);
            assertTrue(controls.next().right() <= width);
            assertTrue(countdown.x() >= 0);
            assertTrue(countdown.right() <= width);
        }
    }
}
