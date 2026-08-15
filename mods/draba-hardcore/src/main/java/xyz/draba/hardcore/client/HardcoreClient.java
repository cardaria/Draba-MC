package xyz.draba.hardcore.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import xyz.draba.hardcore.network.HardcoreUiStatePayload;
import xyz.draba.hardcore.network.SpectateActionPayload;
import xyz.draba.hardcore.ItemOwnership;

public final class HardcoreClient implements ClientModInitializer {
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("draba_hardcore", "spectate_hud");
    private static boolean active;
    private static long eligibleAtEpochMillis;
    private static boolean watching;
    private static String targetName = "";
    private static int targetIndex = -1;
    private static int targetCount;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(HardcoreUiStatePayload.TYPE,
                (payload, context) -> context.client().execute(() -> apply(payload, context.client())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(HardcoreClient::keepRequiredScreenOpen);
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) ->
                ItemOwnership.appendTooltip(stack, lines));

        HudElementRegistry.addLast(HUD_ID, HardcoreClient::extractFallbackHud);
        HudElementRegistry.replaceElement(VanillaHudElements.SPECTATOR_MENU, original ->
                (graphics, deltaTracker) -> {
                    if (!active) {
                        original.extractRenderState(graphics, deltaTracker);
                    }
                });
        HudElementRegistry.replaceElement(VanillaHudElements.SPECTATOR_TOOLTIP, original ->
                (graphics, deltaTracker) -> {
                    if (!active) {
                        original.extractRenderState(graphics, deltaTracker);
                    }
                });
    }

    private static void apply(HardcoreUiStatePayload payload, Minecraft minecraft) {
        active = payload.active();
        eligibleAtEpochMillis = payload.eligibleAtEpochMillis();
        watching = payload.watching();
        targetName = payload.targetName();
        targetIndex = payload.targetIndex();
        targetCount = payload.targetCount();

        Screen screen = minecraft.screen;
        if (!active) {
            if (screen instanceof CooldownScreen || screen instanceof SpectatingScreen) {
                minecraft.setScreen(null);
            }
            return;
        }
        if (screen instanceof CooldownScreen cooldownScreen) {
            if (watching) {
                minecraft.setScreen(new SpectatingScreen());
            } else {
                cooldownScreen.refreshButtons();
            }
        } else if (screen instanceof SpectatingScreen spectatingScreen) {
            if (!watching) {
                minecraft.setScreen(new CooldownScreen());
            } else {
                spectatingScreen.refreshButtons();
            }
        }
    }

    private static void keepRequiredScreenOpen(Minecraft minecraft) {
        if (!active || minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        minecraft.setScreen(watching ? new SpectatingScreen() : new CooldownScreen());
    }

    private static void extractFallbackHud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker ignored) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || !watching || minecraft.options.hideGui
                || minecraft.screen instanceof SpectatingScreen) {
            return;
        }

        int centerX = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 46;
        graphics.fill(centerX - 92, y - 4, centerX + 92, y + 17, 0xB8141921);
        graphics.outline(centerX - 92, y - 4, 184, 21, 0x804B5563);
        int arrowColor = targetCount > 1 ? 0xFFE6EDF3 : 0xFF606A78;
        graphics.centeredText(minecraft.font, Component.literal("‹"), centerX - 76, y, arrowColor);
        graphics.centeredText(minecraft.font, targetName, centerX, y, 0xFFF2C66D);
        graphics.centeredText(minecraft.font, Component.literal("›"), centerX + 76, y, arrowColor);
    }

    static void requestAction(int action) {
        if (active && ClientPlayNetworking.canSend(SpectateActionPayload.TYPE)) {
            ClientPlayNetworking.send(new SpectateActionPayload(action));
        }
    }

    static void stopWatchingLocally() {
        watching = false;
        targetName = "";
        targetIndex = -1;
        requestAction(SpectateActionPayload.STOP);
    }

    static void reset() {
        active = false;
        eligibleAtEpochMillis = 0L;
        watching = false;
        targetName = "";
        targetIndex = -1;
        targetCount = 0;
    }

    static long eligibleAtEpochMillis() {
        return eligibleAtEpochMillis;
    }

    static String targetName() {
        return targetName;
    }

    static int targetIndex() {
        return targetIndex;
    }

    static int targetCount() {
        return targetCount;
    }
}
