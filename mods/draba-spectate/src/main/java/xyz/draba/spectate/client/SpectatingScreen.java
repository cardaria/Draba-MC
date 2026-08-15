package xyz.draba.spectate.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import xyz.draba.spectate.network.SpectateActionPayload;

final class SpectatingScreen extends Screen {
    private static final int LEFT_ARROW_KEY = 263;
    private static final int RIGHT_ARROW_KEY = 262;
    private SpectateOverlay.Buttons buttons;

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
        buttons = SpectateOverlay.createButtons(width, height);
        addRenderableWidget(buttons.previous());
        addRenderableWidget(buttons.next());
        refreshButtons();
    }

    void refreshButtons() {
        SpectateOverlay.updateButtons(buttons, DrabaSpectateClient.targetCount());
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.options.hideGui) {
            return;
        }
        SpectateOverlay.renderControls(
                graphics, font,
                DrabaSpectateClient.targetName(),
                DrabaSpectateClient.targetIndex(),
                DrabaSpectateClient.targetCount());
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (minecraft.options.keyChat.matches(event)) {
            DrabaSpectateClient.openChatNextTick(ChatComponent.ChatMethod.MESSAGE);
            return true;
        }
        if (minecraft.options.keyCommand.matches(event)) {
            DrabaSpectateClient.openChatNextTick(ChatComponent.ChatMethod.COMMAND);
            return true;
        }
        if (event.key() == LEFT_ARROW_KEY && DrabaSpectateClient.targetCount() > 1) {
            DrabaSpectateClient.requestAction(SpectateActionPayload.PREVIOUS);
            return true;
        }
        if (event.key() == RIGHT_ARROW_KEY && DrabaSpectateClient.targetCount() > 1) {
            DrabaSpectateClient.requestAction(SpectateActionPayload.NEXT);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        DrabaSpectateClient.stopWatchingLocally();
        minecraft.setScreen(null);
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
