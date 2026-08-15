package xyz.draba.spectate.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import xyz.draba.spectate.network.SpectateActionPayload;

final class SpectateOverlay {
    private static final int PANEL_COLOR = 0xD8111821;
    private static final int OUTLINE_COLOR = 0xB05B6675;
    private static final int PRIMARY_TEXT_COLOR = 0xFFF2C66D;
    private static final int SECONDARY_TEXT_COLOR = 0xFF9AA4B2;

    private SpectateOverlay() {
    }

    static Buttons createButtons(int screenWidth, int screenHeight) {
        SpectateOverlayLayout.Controls layout =
                SpectateOverlayLayout.controls(screenWidth, screenHeight);
        Button previous = Button.builder(Component.literal("‹"), button ->
                        DrabaSpectateClient.requestAction(SpectateActionPayload.PREVIOUS))
                .bounds(layout.previous().x(), layout.previous().y(),
                        layout.previous().width(), layout.previous().height())
                .build();
        Button next = Button.builder(Component.literal("›"), button ->
                        DrabaSpectateClient.requestAction(SpectateActionPayload.NEXT))
                .bounds(layout.next().x(), layout.next().y(),
                        layout.next().width(), layout.next().height())
                .build();
        return new Buttons(previous, next);
    }

    static void updateButtons(Buttons buttons, int targetCount) {
        if (buttons == null) {
            return;
        }
        boolean canCycle = targetCount > 1;
        buttons.previous().active = canCycle;
        buttons.next().active = canCycle;
    }

    static void renderControls(
            GuiGraphicsExtractor graphics,
            Font font,
            String targetName,
            int targetIndex,
            int targetCount) {
        SpectateOverlayLayout.Controls layout = SpectateOverlayLayout.controls(
                graphics.guiWidth(), graphics.guiHeight());
        fillPanel(graphics, layout.panel());
        graphics.centeredText(font, Component.literal(targetName),
                graphics.guiWidth() / 2,
                layout.panel().y() + SpectateOverlayLayout.TEXT_TOP_OFFSET,
                PRIMARY_TEXT_COLOR);

        if (targetCount > 1) {
            graphics.centeredText(font,
                    Component.literal((targetIndex + 1) + " / " + targetCount),
                    graphics.guiWidth() / 2, layout.positionY(), SECONDARY_TEXT_COLOR);
        }
        graphics.centeredText(font, Component.literal("Esc to return"),
                graphics.guiWidth() / 2, layout.hintY(), SECONDARY_TEXT_COLOR);
    }

    static void renderCountdown(
            GuiGraphicsExtractor graphics, Font font, int secondsRemaining) {
        SpectateOverlayLayout.Rect panel = SpectateOverlayLayout.countdown(
                graphics.guiWidth(), graphics.guiHeight());
        fillPanel(graphics, panel);
        graphics.centeredText(font,
                Component.literal("Spectating in " + secondsRemaining + "…  Stay still."),
                graphics.guiWidth() / 2,
                panel.y() + SpectateOverlayLayout.TEXT_TOP_OFFSET,
                PRIMARY_TEXT_COLOR);
    }

    private static void fillPanel(
            GuiGraphicsExtractor graphics, SpectateOverlayLayout.Rect panel) {
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), PANEL_COLOR);
        graphics.outline(panel.x(), panel.y(), panel.width(), panel.height(), OUTLINE_COLOR);
    }

    record Buttons(Button previous, Button next) {
    }
}
