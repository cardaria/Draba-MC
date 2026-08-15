package xyz.draba.spectate.client;

final class PauseOverlayLayout {
    static final int MARGIN = 8;
    static final int BUTTON_WIDTH = 100;
    static final int BUTTON_HEIGHT = 20;
    static final int BADGE_WIDTH = 72;
    static final int BADGE_HEIGHT = 20;
    static final int BADGE_GAP = 4;
    static final int ICON_SIZE = 16;
    static final int ICON_PADDING = 2;

    private PauseOverlayLayout() {
    }

    static Rect spectateButton(int screenWidth) {
        int preferredX = hardcoreBadge().right() + BADGE_GAP;
        boolean fitsBesideBadges = preferredX + BUTTON_WIDTH <= screenWidth - MARGIN;
        int x = fitsBesideBadges ? preferredX : MARGIN;
        int y = fitsBesideBadges ? MARGIN : MARGIN + BADGE_HEIGHT + BADGE_GAP;
        return new Rect(x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
    }

    static Rect mainBadge() {
        return new Rect(MARGIN, MARGIN, BADGE_WIDTH, BADGE_HEIGHT);
    }

    static Rect hardcoreBadge() {
        return new Rect(MARGIN + BADGE_WIDTH + BADGE_GAP, MARGIN, BADGE_WIDTH, BADGE_HEIGHT);
    }

    record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }
}
