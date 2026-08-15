package xyz.draba.hardcore.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import xyz.draba.hardcore.network.SpectateActionPayload;

final class SpectatingScreen extends Screen {
    private static final int LEFT_ARROW_KEY = 263;
    private static final int RIGHT_ARROW_KEY = 262;
    private Button previousButton;
    private Button nextButton;

    SpectatingScreen() {
        super(Component.literal("Spectating"));
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The live world is the background while watching; menus must not darken it.
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = height - 51;
        previousButton = addRenderableWidget(Button.builder(Component.literal("‹"), button ->
                        HardcoreClient.requestAction(SpectateActionPayload.PREVIOUS))
                .bounds(centerX - 116, y, 24, 20)
                .build());
        nextButton = addRenderableWidget(Button.builder(Component.literal("›"), button ->
                        HardcoreClient.requestAction(SpectateActionPayload.NEXT))
                .bounds(centerX + 92, y, 24, 20)
                .build());
        refreshButtons();
    }

    void refreshButtons() {
        boolean canCycle = HardcoreClient.targetCount() > 1;
        if (previousButton != null) {
            previousButton.active = canCycle;
        }
        if (nextButton != null) {
            nextButton.active = canCycle;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.options.hideGui) {
            return;
        }
        int centerX = width / 2;
        int y = height - 51;
        graphics.fill(centerX - 90, y, centerX + 90, y + 20, 0xC8141921);
        graphics.outline(centerX - 90, y, 180, 20, 0x804B5563);
        graphics.centeredText(font, Component.literal(HardcoreClient.targetName()), centerX, y + 6, 0xFFF2C66D);
        String position = HardcoreClient.targetCount() > 1
                ? (HardcoreClient.targetIndex() + 1) + " / " + HardcoreClient.targetCount()
                : "";
        if (!position.isEmpty()) {
            graphics.centeredText(font, Component.literal(position), centerX, y - 12, 0xFF9AA4B2);
        }
        graphics.centeredText(font, Component.literal("Click arrows  •  T to chat  •  Esc to return"),
                centerX, y + 25, 0xFF7E8998);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (minecraft.options.keyChat.matches(event)) {
            minecraft.openChatScreen(ChatComponent.ChatMethod.MESSAGE);
            return true;
        }
        if (minecraft.options.keyCommand.matches(event)) {
            minecraft.openChatScreen(ChatComponent.ChatMethod.COMMAND);
            return true;
        }
        if (event.key() == LEFT_ARROW_KEY && HardcoreClient.targetCount() > 1) {
            HardcoreClient.requestAction(SpectateActionPayload.PREVIOUS);
            return true;
        }
        if (event.key() == RIGHT_ARROW_KEY && HardcoreClient.targetCount() > 1) {
            HardcoreClient.requestAction(SpectateActionPayload.NEXT);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        HardcoreClient.stopWatchingLocally();
        minecraft.setScreen(new CooldownScreen());
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
