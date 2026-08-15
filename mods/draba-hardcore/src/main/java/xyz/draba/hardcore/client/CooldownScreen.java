package xyz.draba.hardcore.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import xyz.draba.hardcore.network.SpectateActionPayload;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class CooldownScreen extends Screen {
    private static final DateTimeFormatter RETURN_DATE = DateTimeFormatter
            .ofPattern("d MMMM uuuu, HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private Button spectateButton;

    CooldownScreen() {
        super(Component.literal("Hardcore cooldown"));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;
        spectateButton = addRenderableWidget(Button.builder(Component.literal("Spectate"), button ->
                        HardcoreClient.requestAction(SpectateActionPayload.START))
                .bounds(centerX - 102, centerY + 42, 204, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Leave server"), button -> {
                    if (minecraft != null && minecraft.level != null) {
                        minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
                    }
                })
                .bounds(centerX - 102, centerY + 68, 204, 20)
                .build());
        refreshButtons();
    }

    void refreshButtons() {
        if (spectateButton == null) {
            return;
        }
        int count = HardcoreClient.targetCount();
        spectateButton.active = count > 0;
        spectateButton.setMessage(Component.literal(count > 0 ? "Spectate" : "No living players online"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = width / 2;
        int centerY = height / 2;
        graphics.fill(0, 0, width, height, 0xB00A0D12);
        graphics.fill(centerX - 170, centerY - 106, centerX + 170, centerY + 102, 0xED111821);
        graphics.outline(centerX - 170, centerY - 106, 340, 208, 0xB05B6675);

        graphics.centeredText(font, Component.literal("HARDCORE COOLDOWN"), centerX, centerY - 82, 0xFFF2C66D);
        graphics.centeredText(font, Component.literal("Your Hardcore life has ended."),
                centerX, centerY - 51, 0xFFE6EDF3);
        graphics.centeredText(font, Component.literal("You can return on"),
                centerX, centerY - 28, 0xFF9AA4B2);
        graphics.centeredText(font, Component.literal(RETURN_DATE.format(
                        Instant.ofEpochMilli(HardcoreClient.eligibleAtEpochMillis()))),
                centerX, centerY - 14, 0xFFE6EDF3);
        graphics.centeredText(font, Component.literal(formatRemaining()),
                centerX, centerY + 8, 0xFF9AA4B2);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private static String formatRemaining() {
        Duration remaining = Duration.ofMillis(Math.max(0L,
                HardcoreClient.eligibleAtEpochMillis() - System.currentTimeMillis()));
        long totalSeconds = remaining.toSeconds();
        long days = totalSeconds / 86_400;
        long hours = totalSeconds % 86_400 / 3_600;
        long minutes = totalSeconds % 3_600 / 60;
        long seconds = totalSeconds % 60;
        return days + "d  " + hours + "h  " + minutes + "m  " + seconds + "s remaining";
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }
}
