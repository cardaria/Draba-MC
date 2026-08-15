package xyz.draba.network;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;

import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
        id = "draba-network-notices",
        name = "Draba Network Notices",
        version = "1.3.0",
        description = "Provides Draba network notices, branded disconnects, and live status details."
)
public final class DrabaNetworkNotices {
    private static final String HARDCORE_SERVER = "hardcore";
    private static final String SURVIVAL_SERVER = "main";
    private static final String HARDCORE_HOST = "hardcore.example.com";
    private static final String SURVIVAL_HOST = "survival.example.com";
    static final long JOIN_TIME_MAGIC = 0x44524142414A4F49L; // "DRABAJOI"

    private final ProxyServer proxy;
    private final Logger logger;
    private final Map<UUID, String> lastKnownServers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> serverJoinedAtEpochSeconds = new ConcurrentHashMap<>();

    @Inject
    public DrabaNetworkNotices(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String serverName = event.getServer().getServerInfo().getName();
        lastKnownServers.put(player.getUniqueId(), serverName);
        serverJoinedAtEpochSeconds.put(
                player.getUniqueId(), System.currentTimeMillis() / 1_000L);

        // The backend handles its own local join line. Mirror only a player's
        // first network connection to the other backend to avoid duplicates.
        if (event.getPreviousServer().isEmpty()) {
            int recipients = sendToOtherServers(
                    serverName,
                    player.getUniqueId(),
                    joinLeaveMessage(serverName, player.getUsername(), true));
            logger.info("Mirrored {} join for {} to {} player(s)", serverName, player.getUsername(), recipients);
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        String serverName = lastKnownServers.remove(player.getUniqueId());
        serverJoinedAtEpochSeconds.remove(player.getUniqueId());
        if (serverName == null) {
            serverName = player.getCurrentServer()
                    .map(connection -> connection.getServerInfo().getName())
                    .orElse(null);
        }
        if (serverName == null) {
            return;
        }

        int recipients = sendToOtherServers(
                serverName,
                player.getUniqueId(),
                joinLeaveMessage(serverName, player.getUsername(), false));
        logger.info("Mirrored {} leave for {} to {} player(s)", serverName, player.getUsername(), recipients);
    }

    @Subscribe(order = PostOrder.LAST)
    public void onProxyPing(ProxyPingEvent event) {
        String serverName = serverForVirtualHost(
                event.getConnection().getRawVirtualHost().orElse(""));
        if (serverName == null) {
            return;
        }

        long nowEpochSeconds = System.currentTimeMillis() / 1_000L;
        List<StatusPlayer> currentPlayers = proxy.getAllPlayers().stream()
                .filter(player -> player.getCurrentServer()
                        .map(connection -> connection.getServerInfo().getName()
                                .equalsIgnoreCase(serverName))
                        .orElse(false))
                .map(player -> new StatusPlayer(
                        player.getUsername(),
                        serverJoinedAtEpochSeconds.computeIfAbsent(
                                player.getUniqueId(), ignored -> nowEpochSeconds)))
                .sorted(java.util.Comparator
                        .comparingLong(StatusPlayer::joinedAtEpochSeconds)
                        .thenComparing(StatusPlayer::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<ServerPing.SamplePlayer> sample = currentPlayers.stream()
                .map(player -> new ServerPing.SamplePlayer(
                        player.name(), statusSampleId(player.joinedAtEpochSeconds())))
                .toList();

        event.setPing(withStatusPlayers(event.getPing(), currentPlayers.size(), sample));
    }

    static ServerPing withStatusPlayers(
            ServerPing ping, int onlinePlayers, List<ServerPing.SamplePlayer> sample) {
        return ping.asBuilder()
                .onlinePlayers(onlinePlayers)
                .clearSamplePlayers()
                .samplePlayers(sample)
                .build();
    }

    static UUID statusSampleId(long joinedAtEpochSeconds) {
        return new UUID(JOIN_TIME_MAGIC, joinedAtEpochSeconds);
    }

    static String serverForVirtualHost(String rawHost) {
        if (rawHost == null) {
            return null;
        }
        String host = rawHost.strip().toLowerCase(Locale.ROOT);
        int portSeparator = host.indexOf(':');
        if (portSeparator >= 0) {
            host = host.substring(0, portSeparator);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return switch (host) {
            case SURVIVAL_HOST -> SURVIVAL_SERVER;
            case HARDCORE_HOST -> HARDCORE_SERVER;
            default -> null;
        };
    }

    @Subscribe(priority = Short.MIN_VALUE)
    public void onKickedFromServer(KickedFromServerEvent event) {
        String serverName = event.getServer().getServerInfo().getName();
        Component originalReason = event.getServerKickReason()
                .orElse(Component.text("The server closed your connection.", NamedTextColor.RED));
        Component brandedReason = brandedKickReason(serverName, originalReason);

        KickedFromServerEvent.ServerKickResult current = event.getResult();
        if (current instanceof KickedFromServerEvent.RedirectPlayer redirect) {
            event.setResult(KickedFromServerEvent.RedirectPlayer.create(redirect.getServer(), brandedReason));
        } else if (current instanceof KickedFromServerEvent.Notify) {
            event.setResult(KickedFromServerEvent.Notify.create(brandedReason));
        } else {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(brandedReason));
        }
    }

    private int sendToOtherServers(String originServer, UUID excludedPlayer, Component message) {
        int recipients = 0;
        for (Player recipient : proxy.getAllPlayers()) {
            if (recipient.getUniqueId().equals(excludedPlayer)) {
                continue;
            }
            String recipientServer = recipient.getCurrentServer()
                    .map(connection -> connection.getServerInfo().getName())
                    .orElse(null);
            if (recipientServer == null || recipientServer.equalsIgnoreCase(originServer)) {
                continue;
            }
            recipient.sendMessage(message);
            recipients++;
        }
        return recipients;
    }

    static Component joinLeaveMessage(String serverName, String playerName, boolean joined) {
        Component message = Component.empty();
        if (serverName.equalsIgnoreCase(HARDCORE_SERVER)) {
            message = message.append(Component.text("[HC] ", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        } else if (serverName.equalsIgnoreCase(SURVIVAL_SERVER)) {
            message = message.append(Component.text("[SC] ", NamedTextColor.AQUA, TextDecoration.BOLD));
        }
        return message
                .append(Component.text(playerName, NamedTextColor.YELLOW))
                .append(Component.text(joined ? " joined the game" : " left the game", NamedTextColor.YELLOW));
    }

    static Component brandedKickReason(String serverName, Component originalReason) {
        boolean hardcore = serverName.equalsIgnoreCase(HARDCORE_SERVER);
        String brand = hardcore ? "Draba HC XSMP" : "Draba XSMP";
        NamedTextColor brandColor = hardcore ? NamedTextColor.DARK_RED : NamedTextColor.DARK_GREEN;

        Component message = Component.text(brand, brandColor, TextDecoration.BOLD)
                .append(Component.newline())
                .append(originalReason);

        String plainReason = PlainTextComponentSerializer.plainText()
                .serialize(originalReason)
                .toLowerCase(java.util.Locale.ROOT);
        if (isModpackRestartReason(plainReason)
                && !plainReason.contains("if it has already installed")) {
            message = message
                    .append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("Tip: ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(
                            "If the update has already installed, fully restart Minecraft before reconnecting.",
                            NamedTextColor.GRAY));
        }
        return message;
    }

    static boolean isModpackRestartReason(String reason) {
        return reason.contains("automodpack")
                || reason.contains("client pack")
                || reason.contains("modpack")
                || reason.contains("required mod")
                || reason.contains("restart minecraft");
    }

    private record StatusPlayer(String name, long joinedAtEpochSeconds) {
    }
}
