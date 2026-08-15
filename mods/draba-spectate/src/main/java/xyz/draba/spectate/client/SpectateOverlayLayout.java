package xyz.draba.spectate.client;

final class SpectateOverlayLayout {
    static final int PANEL_WIDTH = 180;
    static final int PANEL_HEIGHT = 20;
    static final int BUTTON_WIDTH = 24;
    static final int BUTTON_GAP = 2;
    static final int BOTTOM_OFFSET = 51;
    static final int TEXT_TOP_OFFSET = 6;
    static final int POSITION_TOP_OFFSET = -12;
    static final int HINT_TOP_OFFSET = 29;
    static final int COUNTDOWN_WIDTH = 204;
    static final int COUNTDOWN_BOTTOM_OFFSET = 68;

    private SpectateOverlayLayout() {
    }

    static Controls controls(int screenWidth, int screenHeight) {
        int centerX = screenWidth / 2;
        int y = screenHeight - BOTTOM_OFFSET;
        Rect panel = new Rect(centerX - PANEL_WIDTH / 2, y, PANEL_WIDTH, PANEL_HEIGHT);
        Rect previous = new Rect(
                panel.x() - BUTTON_GAP - BUTTON_WIDTH, y, BUTTON_WIDTH, PANEL_HEIGHT);
        Rect next = new Rect(
                panel.right() + BUTTON_GAP, y, BUTTON_WIDTH, PANEL_HEIGHT);
        return new Controls(previous, panel, next,
                y + POSITION_TOP_OFFSET, y + HINT_TOP_OFFSET);
    }

    static Rect countdown(int screenWidth, int screenHeight) {
        int width = Math.min(COUNTDOWN_WIDTH, Math.max(1, screenWidth - 16));
        return new Rect((screenWidth - width) / 2,
                screenHeight - COUNTDOWN_BOTTOM_OFFSET, width, PANEL_HEIGHT);
    }

    record Controls(Rect previous, Rect panel, Rect next, int positionY, int hintY) {
    }

    record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }
}
