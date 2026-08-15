package xyz.draba.spectate.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.server.players.NameAndId;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class NetworkStatusMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger("draba_spectate/status");
    private static final long REFRESH_INTERVAL_MILLIS = 15_000L;
    private static final long PING_TIMEOUT_MILLIS = 7_000L;
    private static final ServerStatusPinger PINGER = new ServerStatusPinger();
    private static final Slot SURVIVAL = new Slot(
            "Survival", "survival.example.com", 30, 0xFF4FA76B);
    private static final Slot HARDCORE = new Slot(
            "Hardcore", "hardcore.example.com", 15, 0xFF9F3030);

    private static long lastRefreshMillis;
    private static int generation;

    private NetworkStatusMonitor() {
    }

    static void tick(Minecraft minecraft) {
        PINGER.tick();
        long now = System.currentTimeMillis();
        SURVIVAL.expireIfTimedOut(now);
        HARDCORE.expireIfTimedOut(now);
        if (minecraft.screen instanceof PauseScreen) {
            refreshIfStale(minecraft);
        }
        SURVIVAL.syncButton(minecraft);
        HARDCORE.syncButton(minecraft);
    }

    static void addButtons(Minecraft minecraft, Screen screen) {
        ServerStatusButton mainButton = new ServerStatusButton(
                SURVIVAL, PauseOverlayLayout.mainBadge());
        ServerStatusButton hardcoreButton = new ServerStatusButton(
                HARDCORE, PauseOverlayLayout.hardcoreBadge());
        SURVIVAL.attachButton(mainButton);
        HARDCORE.attachButton(hardcoreButton);
        Screens.getWidgets(screen).add(mainButton);
        Screens.getWidgets(screen).add(hardcoreButton);
        SURVIVAL.syncButton(minecraft);
        HARDCORE.syncButton(minecraft);
    }

    static void refreshIfStale(Minecraft minecraft) {
        long now = System.currentTimeMillis();
        if (SURVIVAL.loading || HARDCORE.loading || now - lastRefreshMillis < REFRESH_INTERVAL_MILLIS) {
            return;
        }
        lastRefreshMillis = now;
        int refreshGeneration = ++generation;
        PINGER.removeAll();
        startPing(minecraft, SURVIVAL, refreshGeneration, now);
        startPing(minecraft, HARDCORE, refreshGeneration, now);
    }

    private static void startPing(
            Minecraft minecraft, Slot slot, int refreshGeneration, long startedAtMillis) {
        slot.begin(refreshGeneration, startedAtMillis + PING_TIMEOUT_MILLIS);
        ServerData data = new ServerData(slot.label, slot.host, ServerData.Type.OTHER);
        data.setState(ServerData.State.PINGING);
        CompletableFuture.runAsync(() -> {
            try {
                PINGER.pingServer(
                        data,
                        () -> minecraft.execute(() -> uploadIcon(minecraft, slot, data, refreshGeneration)),
                        () -> minecraft.execute(() -> complete(slot, data, refreshGeneration)),
                        EventLoopGroupHolder.remote(minecraft.options.useNativeTransport()));
            } catch (Exception exception) {
                minecraft.execute(() -> fail(slot, refreshGeneration));
                LOGGER.debug("Could not ping {}", slot.host, exception);
            }
        });
    }

    private static void complete(Slot slot, ServerData data, int refreshGeneration) {
        if (slot.generation != refreshGeneration) {
            return;
        }
        if (data.players == null) {
            slot.fail();
            return;
        }
        slot.complete(
                data.players.online(),
                data.players.max(),
                decodePlayers(data.players.sample(), System.currentTimeMillis() / 1_000L));
        uploadIcon(Minecraft.getInstance(), slot, data, refreshGeneration);
    }

    private static void fail(Slot slot, int refreshGeneration) {
        if (slot.generation == refreshGeneration) {
            slot.fail();
        }
    }

    private static void uploadIcon(
            Minecraft minecraft, Slot slot, ServerData data, int refreshGeneration) {
        if (slot.generation != refreshGeneration) {
            return;
        }
        byte[] iconBytes = data.getIconBytes();
        boolean textureAvailable = slot.favicon != null && !slot.favicon.isClosed();
        if (!needsIconUpload(iconBytes, slot.lastIconBytes, slot.iconReady, textureAvailable)) {
            return;
        }
        try {
            NativeImage image = NativeImage.read(iconBytes);
            slot.ensureFavicon(minecraft).upload(image);
            slot.lastIconBytes = iconBytes.clone();
            slot.iconReady = true;
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.warn("Ignored an invalid server icon from {}", slot.host, exception);
        }
    }

    static boolean needsIconUpload(
            byte[] iconBytes, byte[] lastIconBytes, boolean iconReady, boolean textureAvailable) {
        return iconBytes != null
                && (!Arrays.equals(iconBytes, lastIconBytes) || !iconReady || !textureAvailable);
    }

    static List<PlayerPresence> decodePlayers(List<NameAndId> sample, long nowEpochSeconds) {
        List<PlayerPresence> players = new ArrayList<>();
        for (NameAndId profile : sample) {
            if (profile == null || profile.name() == null || profile.name().isBlank()) {
                continue;
            }
            players.add(new PlayerPresence(
                    profile.name(),
                    NetworkStatusProtocol.decodeJoinEpochSeconds(profile.id(), nowEpochSeconds)));
        }
        players.sort(Comparator
                .comparingLong((PlayerPresence player) -> player.joinedAtEpochSeconds() < 0L
                        ? Long.MAX_VALUE
                        : player.joinedAtEpochSeconds())
                .thenComparing(PlayerPresence::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(players);
    }

    static String formatElapsed(long joinedAtEpochSeconds, long nowEpochSeconds) {
        if (joinedAtEpochSeconds < 0L) {
            return "—";
        }
        long elapsed = Math.max(0L, nowEpochSeconds - joinedAtEpochSeconds);
        if (elapsed < 60L) {
            return "now";
        }
        long minutes = elapsed / 60L;
        if (minutes < 60L) {
            return minutes + "m";
        }
        long hours = minutes / 60L;
        long remainingMinutes = minutes % 60L;
        if (hours < 24L) {
            return hours + "h" + (remainingMinutes == 0L ? "" : " " + remainingMinutes + "m");
        }
        long days = hours / 24L;
        long remainingHours = hours % 24L;
        return days + "d" + (remainingHours == 0L ? "" : " " + remainingHours + "h");
    }

    static void onDisconnect() {
        ++generation;
        PINGER.removeAll();
        SURVIVAL.reset();
        HARDCORE.reset();
        lastRefreshMillis = 0L;
    }

    static void close() {
        PINGER.removeAll();
        SURVIVAL.close();
        HARDCORE.close();
    }

    private static void requestRefresh() {
        if (SURVIVAL.loading || HARDCORE.loading) {
            return;
        }
        lastRefreshMillis = System.currentTimeMillis() - REFRESH_INTERVAL_MILLIS - 1L;
        refreshIfStale(Minecraft.getInstance());
    }

    private static final class ServerStatusButton extends Button.Plain {
        private final Slot slot;

        private ServerStatusButton(Slot slot, PauseOverlayLayout.Rect bounds) {
            super(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    Component.empty(), button -> {
                        requestRefresh();
                    }, DEFAULT_NARRATION);
            this.slot = slot;
            setTooltipDelay(Duration.ofMillis(100L));
        }

        @Override
        public boolean shouldTakeFocusAfterInteraction() {
            return false;
        }

        @Override
        protected void extractContents(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractContents(graphics, mouseX, mouseY, partialTick);
            int iconX = getX() + PauseOverlayLayout.ICON_PADDING;
            int iconY = getY() + PauseOverlayLayout.ICON_PADDING;
            if (slot.iconReady && slot.favicon != null) {
                // Destination 16x16, source region 64x64: scale the complete
                // favicon instead of cropping its top-left corner.
                graphics.blit(RenderPipelines.GUI_TEXTURED,
                        slot.favicon.textureLocation(),
                        iconX, iconY, 0.0F, 0.0F,
                        PauseOverlayLayout.ICON_SIZE, PauseOverlayLayout.ICON_SIZE,
                        64, 64, 64, 64);
            } else {
                graphics.fill(iconX, iconY,
                        iconX + PauseOverlayLayout.ICON_SIZE,
                        iconY + PauseOverlayLayout.ICON_SIZE,
                        slot.accentColor);
                graphics.centeredText(Minecraft.getInstance().font,
                        Component.literal(slot == SURVIVAL ? "S" : "HC"),
                        iconX + PauseOverlayLayout.ICON_SIZE / 2,
                        getY() + 6, 0xFFFFFFFF);
            }
        }
    }

    private static final class Slot {
        private final String label;
        private final String host;
        private final int fallbackMax;
        private final int accentColor;
        private int generation;
        private int onlineCount = -1;
        private int maxPlayers;
        private long deadlineMillis;
        private boolean loading;
        private boolean online;
        private boolean iconReady;
        private byte[] lastIconBytes;
        private FaviconTexture favicon;
        private ServerStatusButton button;
        private List<PlayerPresence> players = List.of();
        private String tooltipFingerprint = "";

        private Slot(String label, String host, int fallbackMax, int accentColor) {
            this.label = label;
            this.host = host;
            this.fallbackMax = fallbackMax;
            this.accentColor = accentColor;
            this.maxPlayers = fallbackMax;
        }

        private void attachButton(ServerStatusButton newButton) {
            button = newButton;
            // Pause screens create new widget instances. Force the tooltip
            // onto each one even when the displayed server state is unchanged.
            tooltipFingerprint = "";
        }

        private void begin(int newGeneration, long deadline) {
            generation = newGeneration;
            deadlineMillis = deadline;
            loading = true;
            online = false;
            players = List.of();
        }

        private void complete(int count, int max, List<PlayerPresence> currentPlayers) {
            onlineCount = Math.max(0, count);
            maxPlayers = max > 0 ? max : fallbackMax;
            loading = false;
            online = true;
            players = currentPlayers;
        }

        private void fail() {
            loading = false;
            online = false;
            players = List.of();
        }

        private void expireIfTimedOut(long now) {
            if (loading && now >= deadlineMillis) {
                fail();
            }
        }

        private String countText() {
            if (loading) {
                return "…/" + maxPlayers;
            }
            if (!online) {
                return "—/" + maxPlayers;
            }
            return onlineCount + "/" + maxPlayers;
        }

        private void syncButton(Minecraft minecraft) {
            if (button == null) {
                return;
            }
            // Leading space reserves the left side for the favicon while the
            // label still uses Minecraft's native centered button rendering.
            button.setMessage(Component.literal("   " + countText()));
            long nowEpochSeconds = System.currentTimeMillis() / 1_000L;
            String fingerprint = loading + ":" + online + ":"
                    + onlineCount + ":" + maxPlayers + ":" + players.hashCode() + ":"
                    + nowEpochSeconds / 60L;
            if (!fingerprint.equals(tooltipFingerprint)) {
                tooltipFingerprint = fingerprint;
                button.setTooltip(buildTooltip(minecraft, nowEpochSeconds));
            }
        }

        private Tooltip buildTooltip(Minecraft minecraft, long nowEpochSeconds) {
            String state = loading ? "Checking…" : online ? "Online" : "Unavailable";
            int stateColor = loading ? 0xFFFFAA00 : online ? 0xFF55FF55 : 0xFFFF5555;
            Component header = Component.literal(label)
                    .withStyle(style -> style.withBold(true).withColor(accentColor & 0xFFFFFF))
                    .append(Component.literal("\n" + state)
                            .withStyle(style -> style.withColor(stateColor & 0xFFFFFF)))
                    .append(Component.literal("  •  " + populationText())
                            .withStyle(style -> style.withColor(0x777777)));

            List<NetworkStatusTooltip.PlayerLine> lines = players.stream()
                    .map(player -> new NetworkStatusTooltip.PlayerLine(
                            player.name(),
                            formatElapsed(player.joinedAtEpochSeconds(), nowEpochSeconds)))
                    .toList();
            NetworkStatusTooltip table = new NetworkStatusTooltip(lines, 1);
            if (!lines.isEmpty()) {
                NetworkStatusTooltip twoColumn = new NetworkStatusTooltip(lines, 2);
                int twoColumnWidth = new NetworkStatusTooltip.ClientComponent(twoColumn)
                        .getWidth(minecraft.font);
                int columns = NetworkStatusTooltip.chooseColumns(
                        lines.size(),
                        minecraft.getWindow().getGuiScaledWidth(),
                        minecraft.getWindow().getGuiScaledHeight(),
                        twoColumnWidth);
                if (columns == 2) {
                    table = twoColumn;
                }
            }
            return Tooltip.create(header, Optional.of(table), null);
        }

        private String populationText() {
            String count = loading ? "…" : online ? Integer.toString(onlineCount) : "—";
            return count + " / " + maxPlayers + " players";
        }

        private FaviconTexture ensureFavicon(Minecraft minecraft) {
            if (favicon == null || favicon.isClosed()) {
                favicon = FaviconTexture.forServer(minecraft.getTextureManager(), host);
            }
            return favicon;
        }

        private void reset() {
            generation = 0;
            onlineCount = -1;
            maxPlayers = fallbackMax;
            deadlineMillis = 0L;
            loading = false;
            online = false;
            button = null;
            players = List.of();
            tooltipFingerprint = "";
        }

        private void close() {
            if (favicon != null && !favicon.isClosed()) {
                favicon.close();
            }
            favicon = null;
            iconReady = false;
            lastIconBytes = null;
            button = null;
            players = List.of();
            tooltipFingerprint = "";
        }
    }

    record PlayerPresence(String name, long joinedAtEpochSeconds) {
    }
}
