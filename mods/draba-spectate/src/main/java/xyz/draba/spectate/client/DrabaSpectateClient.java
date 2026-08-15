package xyz.draba.spectate.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import xyz.draba.spectate.network.ClientModPolicyChallengePayload;
import xyz.draba.spectate.network.ClientModPolicyResponsePayload;
import xyz.draba.spectate.network.SpectateActionPayload;
import xyz.draba.spectate.network.SpectateStatePayload;
import xyz.draba.spectate.VoxyWorldLease;

import java.util.TreeSet;

public final class DrabaSpectateClient implements ClientModInitializer {
    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(
            "draba_spectate", "spectate_hud");
    private static final DeferredChatOpen DEFERRED_CHAT = new DeferredChatOpen();
    private static boolean supported;
    private static boolean watching;
    private static boolean arming;
    private static int armingTicksRemaining;
    private static boolean startAllowed;
    private static boolean startPending;
    private static boolean stopPending;
    private static String startReason = "Checking availability…";
    private static String targetName = "";
    private static int targetEntityId = -1;
    private static int targetIndex = -1;
    private static int targetCount;
    private static int stopRetryTicks;
    private static Button pauseButton;
    private static SpectateOverlay.Buttons chatButtons;
    private static CameraType savedCameraType;
    private static boolean cameraTypeSaved;
    private static VoxyWorldLease originWorldLease;

    @Override
    public void onInitializeClient() {
        ClientTooltipComponentCallback.EVENT.register(component ->
                component instanceof NetworkStatusTooltip statusTooltip
                        ? new NetworkStatusTooltip.ClientComponent(statusTooltip)
                        : null);
        ClientPlayNetworking.registerGlobalReceiver(SpectateStatePayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> apply(payload, context.client())));
        ClientPlayNetworking.registerGlobalReceiver(ClientModPolicyChallengePayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> reportClientMods(payload)));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            reset(client);
            NetworkStatusMonitor.onDisconnect();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            restoreCamera(client);
            releaseOriginWorld();
            DEFERRED_CHAT.clear();
            NetworkStatusMonitor.close();
        });
        ClientTickEvents.END_CLIENT_TICK.register(DrabaSpectateClient::onClientTick);
        ScreenEvents.AFTER_INIT.register(DrabaSpectateClient::afterScreenInit);

        HudElementRegistry.addLast(HUD_ID, DrabaSpectateClient::extractFallbackHud);
        HudElementRegistry.replaceElement(VanillaHudElements.SPECTATOR_MENU, original ->
                (graphics, deltaTracker) -> {
                    if (!watching) {
                        original.extractRenderState(graphics, deltaTracker);
                    }
                });
        HudElementRegistry.replaceElement(VanillaHudElements.SPECTATOR_TOOLTIP, original ->
                (graphics, deltaTracker) -> {
                    if (!watching) {
                        original.extractRenderState(graphics, deltaTracker);
                    }
                });
    }

    private static void afterScreenInit(
            Minecraft minecraft, Screen screen, int scaledWidth, int scaledHeight) {
        chatButtons = null;
        if (watching && screen instanceof ChatScreen) {
            chatButtons = SpectateOverlay.createButtons(scaledWidth, scaledHeight);
            Screens.getWidgets(screen).add(chatButtons.previous());
            Screens.getWidgets(screen).add(chatButtons.next());
            SpectateOverlay.updateButtons(chatButtons, targetCount);
            return;
        }
        if (!(screen instanceof PauseScreen) || !featureChannelAvailable()) {
            return;
        }
        supported = true;
        PauseOverlayLayout.Rect bounds = PauseOverlayLayout.spectateButton(scaledWidth);
        pauseButton = Button.builder(Component.literal("Spectate"), button -> onSpectateButton())
                .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height())
                .build();
        Screens.getWidgets(screen).add(pauseButton);
        NetworkStatusMonitor.addButtons(minecraft, screen);
        updatePauseButton();

        NetworkStatusMonitor.refreshIfStale(minecraft);
    }

    private static void onClientTick(Minecraft minecraft) {
        NetworkStatusMonitor.tick(minecraft);
        supported = featureChannelAvailable();
        if (minecraft.screen instanceof PauseScreen) {
            updatePauseButton();
        }

        if (stopPending && ++stopRetryTicks >= 20) {
            stopRetryTicks = 0;
            requestAction(SpectateActionPayload.STOP);
        }
        if (watching) {
            enforceCamera(minecraft);
        }
        if (watching && !stopPending && minecraft.player != null && minecraft.level != null
                && minecraft.screen == null) {
            minecraft.setScreen(new SpectatingScreen());
        }
        ChatComponent.ChatMethod chatMethod = DEFERRED_CHAT.consume();
        if (chatMethod != null && watching && minecraft.screen instanceof SpectatingScreen) {
            minecraft.openChatScreen(chatMethod);
        }
    }

    private static void apply(SpectateStatePayload payload, Minecraft minecraft) {
        boolean wasArming = arming;
        boolean wasWatching = watching;
        if (payload.arming() && !wasArming && !wasWatching) {
            retainOriginWorld(minecraft);
        }
        supported = true;
        arming = payload.arming();
        armingTicksRemaining = payload.armingTicksRemaining();
        startAllowed = payload.startAllowed();
        startReason = payload.startReason();
        targetName = payload.targetName();
        targetEntityId = payload.targetEntityId();
        targetIndex = payload.targetIndex();
        targetCount = payload.targetCount();
        SpectateOverlay.updateButtons(chatButtons, targetCount);
        startPending = false;

        if (stopPending && payload.watching()) {
            requestAction(SpectateActionPayload.STOP);
            return;
        }
        if (!payload.watching()) {
            stopPending = false;
            stopRetryTicks = 0;
        }
        watching = payload.watching();

        if (watching) {
            if (!wasWatching) {
                saveCameraType(minecraft);
            }
            enforceCamera(minecraft);
            if (minecraft.screen == null || minecraft.screen instanceof PauseScreen) {
                minecraft.setScreen(new SpectatingScreen());
            } else if (minecraft.screen instanceof SpectatingScreen spectatingScreen) {
                spectatingScreen.refreshButtons();
            }
        } else if (minecraft.screen instanceof SpectatingScreen) {
            restoreCamera(minecraft);
            minecraft.setScreen(null);
        } else if (wasWatching || cameraTypeSaved) {
            restoreCamera(minecraft);
        }
        if (!arming && !watching) {
            releaseOriginWorld();
        }
        updatePauseButton();
    }

    private static void onSpectateButton() {
        if (arming) {
            requestAction(SpectateActionPayload.STOP);
            return;
        }
        requestStart();
    }

    private static void requestStart() {
        if (!supported || startPending || watching || !startAllowed || targetCount <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen returnScreen = minecraft.screen;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                minecraft.setScreen(null);
                beginStartRequest();
            } else {
                minecraft.setScreen(returnScreen);
            }
        },
                Component.literal("Begin spectating?"),
                Component.literal(
                        "You remain vulnerable for 5 seconds. Stay still and safe."),
                Component.literal("Begin countdown"),
                Component.literal("Cancel")));
    }

    private static void beginStartRequest() {
        if (!supported || startPending || watching || arming
                || !startAllowed || targetCount <= 0) {
            return;
        }
        startPending = true;
        updatePauseButton();
        requestAction(SpectateActionPayload.START);
    }

    static void requestAction(int action) {
        if (featureChannelAvailable()) {
            ClientPlayNetworking.send(new SpectateActionPayload(action));
        }
    }

    static void openChatNextTick(ChatComponent.ChatMethod method) {
        DEFERRED_CHAT.request(method);
    }

    static void stopWatchingLocally() {
        if (!watching && !stopPending) {
            return;
        }
        watching = false;
        stopPending = true;
        stopRetryTicks = 0;
        targetName = "";
        targetEntityId = -1;
        targetIndex = -1;
        restoreCamera(Minecraft.getInstance());
        requestAction(SpectateActionPayload.STOP);
    }

    private static boolean featureChannelAvailable() {
        return ClientPlayNetworking.canSend(SpectateActionPayload.TYPE);
    }

    private static void reportClientMods(ClientModPolicyChallengePayload challenge) {
        if (!ClientPlayNetworking.canSend(ClientModPolicyResponsePayload.TYPE)) {
            return;
        }
        TreeSet<String> modIds = new TreeSet<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            if (container.getContainingMod().isEmpty()) {
                modIds.add(container.getMetadata().getId());
            }
        }
        ClientPlayNetworking.send(new ClientModPolicyResponsePayload(
                challenge.nonce(), modIds.stream().toList()));
    }

    private static void updatePauseButton() {
        if (pauseButton == null) {
            return;
        }
        pauseButton.active = arming || supported && !startPending && !watching
                && startAllowed && targetCount > 0;
        int seconds = armingSeconds();
        String label = arming
                ? "Cancel (" + seconds + "s)"
                : startPending ? "Starting…" : "Spectate";
        pauseButton.setMessage(Component.literal(label));
        String tooltip = arming
                ? "Stay still and remain safe. Click to cancel."
                : startPending
                ? "Starting spectate…"
                : targetCount <= 0
                ? "No eligible players are online."
                : startAllowed
                ? "Watch an online player."
                : startReason;
        pauseButton.setTooltip(Tooltip.create(Component.literal(tooltip)));
    }

    private static int armingSeconds() {
        return SpectateCountdown.displaySeconds(armingTicksRemaining);
    }

    private static void extractFallbackHud(
            GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker ignored) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }
        if (arming) {
            SpectateOverlay.renderCountdown(graphics, minecraft.font, armingSeconds());
        }
        if (!watching || minecraft.screen instanceof SpectatingScreen) {
            return;
        }
        SpectateOverlay.renderControls(
                graphics, minecraft.font, targetName, targetIndex, targetCount);
    }

    private static void saveCameraType(Minecraft minecraft) {
        if (!cameraTypeSaved) {
            savedCameraType = minecraft.options.getCameraType();
            cameraTypeSaved = true;
        }
    }

    private static void enforceCamera(Minecraft minecraft) {
        saveCameraType(minecraft);
        if (minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
        if (minecraft.level == null || targetEntityId < 0) {
            return;
        }
        Entity target = minecraft.level.getEntity(targetEntityId);
        if (target != null && minecraft.getCameraEntity() != target) {
            minecraft.setCameraEntity(target);
        }
    }

    private static void restoreCamera(Minecraft minecraft) {
        if (minecraft.player != null && minecraft.getCameraEntity() != minecraft.player) {
            minecraft.setCameraEntity(minecraft.player);
        }
        if (cameraTypeSaved && savedCameraType != null) {
            minecraft.options.setCameraType(savedCameraType);
        }
        savedCameraType = null;
        cameraTypeSaved = false;
    }

    private static void retainOriginWorld(Minecraft minecraft) {
        releaseOriginWorld();
        originWorldLease = VoxyWorldLease.acquire(minecraft.level);
    }

    private static void releaseOriginWorld() {
        if (originWorldLease != null) {
            originWorldLease.close();
            originWorldLease = null;
        }
    }

    private static void reset(Minecraft minecraft) {
        restoreCamera(minecraft);
        releaseOriginWorld();
        DEFERRED_CHAT.clear();
        supported = false;
        watching = false;
        arming = false;
        armingTicksRemaining = 0;
        startAllowed = false;
        startPending = false;
        stopPending = false;
        startReason = "Checking availability…";
        targetName = "";
        targetEntityId = -1;
        targetIndex = -1;
        targetCount = 0;
        stopRetryTicks = 0;
        pauseButton = null;
        chatButtons = null;
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
