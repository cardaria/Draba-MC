/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.google.inject.Inject
 *  com.velocitypowered.api.command.Command
 *  com.velocitypowered.api.command.CommandManager
 *  com.velocitypowered.api.command.CommandMeta
 *  com.velocitypowered.api.command.CommandSource
 *  com.velocitypowered.api.command.SimpleCommand
 *  com.velocitypowered.api.command.SimpleCommand$Invocation
 *  com.velocitypowered.api.event.Subscribe
 *  com.velocitypowered.api.event.connection.DisconnectEvent
 *  com.velocitypowered.api.event.connection.DisconnectEvent$LoginStatus
 *  com.velocitypowered.api.event.player.PlayerChatEvent
 *  com.velocitypowered.api.event.player.ServerConnectedEvent
 *  com.velocitypowered.api.event.proxy.ProxyInitializeEvent
 *  com.velocitypowered.api.event.proxy.ProxyShutdownEvent
 *  com.velocitypowered.api.plugin.Plugin
 *  com.velocitypowered.api.plugin.annotation.DataDirectory
 *  com.velocitypowered.api.proxy.Player
 *  com.velocitypowered.api.proxy.ProxyServer
 *  com.velocitypowered.api.proxy.ServerConnection
 *  com.velocitypowered.api.proxy.server.RegisteredServer
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.ComponentLike
 *  net.kyori.adventure.text.minimessage.MiniMessage
 *  net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
 *  net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
 *  net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
 *  org.slf4j.Logger
 */
package net.groundplayz.relayline;

import com.google.inject.Inject;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;

@Plugin(id="relayline", name="RelayLine", version="1.4.2-draba.1", description="Relays chat to other Velocity servers while leaving local chat formatting untouched.", authors={"GroundPlayz", "Draba Network"})
public final class RelayLinePlugin {
    private static final String LEGACY_PLUGIN_ID = "localglobalchat";
    private static final String DEFAULT_CONFIG = "# RelayLine\n# Install this jar on Velocity only.\n# Backend servers keep their own chat formatting. This plugin only sends a plain copy to other servers.\n\n# If true, new players relay chat globally until they run /local.\n# If false, new players stay local until they run /global.\ndefault-global=true\n\n# MiniMessage is supported here.\n# Available placeholders: %server_name%, %server%, %player%, %message%\n# Player names and messages are inserted as plain text, so players cannot inject MiniMessage tags.\nformat=<gray>[</gray>%server_name%<gray>]</gray> <aqua>%player%</aqua> <gray>></gray> <white>%message%</white>\n\n# Command names registered on the proxy.\nglobal-command=global\nlocal-command=local\n\n# Optional aliases, comma-separated.\nglobal-aliases=gchat\nlocal-aliases=lchat\n\n# If true, players need the permission below to use /global and /local.\nrequire-permission=false\npermission=relayline.toggle\n\n# If true, every eligible relay is also written to the Velocity console.\nlog-relays-to-console=true\n\n# Permission for /relayline reload.\nreload-permission=relayline.reload\n\n# Server switch notices are sent when a player moves between backend servers.\nserver-switch-notices-enabled=true\ndefault-see-server-switch-notices=true\nlog-server-switches-to-console=true\n\n# MiniMessage is supported here.\n# Available placeholders: %player%, %server_left%, %server_joined%\n\n# Available aliases: %old_server% = %server_left%, %new_server% = %server_joined%\nserver-switch-format=<dark_gray>(<gold>*</gold><dark_gray>) <gray>%player% <dark_gray>(<gold>%old_server%->%new_server%<dark_gray>)\n\n# Join/leave notices use the same visibility toggle as server switch notices.\n# Set a server to lobby/survival/prison, or use * to allow every server.\njoin-leave-notices-enabled=true\njoin-notice-server=lobby\nleave-notice-server=*\nlog-join-leave-notices-to-console=true\n\n# Available placeholders: %player%, %server%, %server_name%\njoin-notice-format=<dark_gray>(<green>+</green><dark_gray>) <gray>%player%</gray>\nleave-notice-format=<dark_gray>(<red>-</red><dark_gray>) <gray>%player%</gray>\n\n# Command for players to toggle whether they see server switch notices.\nserver-switch-command=servernotices\nserver-switch-aliases=switchalerts,serveralerts\n\n# MiniMessage is also supported in command feedback messages.\nglobal-enabled-message=<green>Global chat enabled.</green> <gray>Your chat will be relayed to other servers.</gray>\nlocal-enabled-message=<yellow>Local chat enabled.</yellow> <gray>Your chat will stay on your current server.</gray>\nserver-switch-notices-enabled-message=<green>Server switch notices enabled.</green>\nserver-switch-notices-disabled-message=<yellow>Server switch notices disabled.</yellow>\nplayer-only-message=<red>Only players can use this command.</red>\nno-permission-message=<red>You do not have permission to use this command.</red>\nreload-success-message=<green>RelayLine reloaded.</green>\nreload-failed-message=<red>RelayLine reload failed.</red> <gray>Check the Velocity console for details.</gray>\nusage-message=<gray>Usage:</gray> <white>/relayline reload</white>\n\n# Velocity server-name aliases. Keys must match the server names in velocity.toml.\n# MiniMessage is supported in alias values.\nserver-alias.lobby=<aqua>Lobby</aqua>\nserver-alias.survival=<green>Survival</green>\nserver-alias.prison=<red>Prison</red>\n\n# Comma-separated message prefixes that remain on their origin server.\nlocal-only-message-prefixes=xaero-waypoint:\n\n# Comma-separated Velocity server names to ignore completely.\nexcluded-servers=\n";
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();
    private final Map<UUID, Boolean> playerModes = new HashMap<UUID, Boolean>();
    private final Map<UUID, Boolean> serverSwitchNoticeModes = new HashMap<UUID, Boolean>();
    private final Map<UUID, String> lastKnownServers = new HashMap<UUID, String>();
    private Config config;
    private CommandMeta globalCommandMeta;
    private CommandMeta localCommandMeta;
    private CommandMeta serverSwitchCommandMeta;
    private CommandMeta rootCommandMeta;

    @Inject
    public RelayLinePlugin(ProxyServer proxyServer, Logger logger, @DataDirectory Path path) {
        this.proxy = proxyServer;
        this.logger = logger;
        this.dataDirectory = path;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent proxyInitializeEvent) {
        try {
            this.migrateLegacyDataFiles();
            Files.createDirectories(this.dataDirectory, new FileAttribute[0]);
            this.config = this.loadConfig();
            this.loadPlayerModes();
            this.loadServerSwitchNoticeModes();
            this.registerCommands();
            this.logger.info("RelayLine enabled. Local chat formatting will be left untouched.");
        }
        catch (IOException iOException) {
            this.logger.error("Failed to enable RelayLine", (Throwable)iOException);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent proxyShutdownEvent) {
        try {
            this.savePlayerModes();
            this.saveServerSwitchNoticeModes();
        }
        catch (IOException iOException) {
            this.logger.warn("Failed to save RelayLine player modes", (Throwable)iOException);
        }
    }

    private void migrateLegacyDataFiles() throws IOException {
        Path path = this.dataDirectory.getParent();
        if (path == null) {
            return;
        }
        Path path2 = path.resolve(LEGACY_PLUGIN_ID);
        if (!Files.isDirectory(path2, new LinkOption[0])) {
            return;
        }
        Files.createDirectories(this.dataDirectory, new FileAttribute[0]);
        boolean bl = this.copyLegacyFileIfMissing(path2, "config.properties");
        boolean bl2 = this.copyLegacyFileIfMissing(path2, "player-modes.properties");
        boolean bl3 = this.copyLegacyFileIfMissing(path2, "server-switch-notices.properties");
        if (bl || bl2 || bl3) {
            this.logger.info("Copied legacy LocalGlobalChat data files into the RelayLine data folder.");
        }
    }

    private boolean copyLegacyFileIfMissing(Path path, String string) throws IOException {
        Path path2 = path.resolve(string);
        Path path3 = this.dataDirectory.resolve(string);
        if (Files.isRegularFile(path2, new LinkOption[0]) && Files.notExists(path3, new LinkOption[0])) {
            Files.copy(path2, path3, new CopyOption[0]);
            return true;
        }
        return false;
    }

    @Subscribe(priority=-32768)
    public void onPlayerChat(PlayerChatEvent playerChatEvent) {
        if (this.config == null || !playerChatEvent.getResult().isAllowed()) {
            return;
        }
        Player player = playerChatEvent.getPlayer();
        if (!this.isGlobal(player.getUniqueId())) {
            return;
        }
        Optional optional = player.getCurrentServer();
        if (optional.isEmpty()) {
            return;
        }
        String string = ((ServerConnection)optional.get()).getServerInfo().getName();
        if (this.config.excludedServers.contains(RelayLinePlugin.normalize(string))) {
            return;
        }
        String string2 = playerChatEvent.getResult().getMessage().orElse(playerChatEvent.getMessage());
        if (RelayLineMessagePolicy.isLocalOnly(string2, this.config.localOnlyMessagePrefixes)) {
            if (this.config.logRelaysToConsole) {
                this.logger.info("Kept local-only chat on {}: {}", (Object)string, (Object)string2);
            }
            return;
        }
        Component component = this.formatRelay(string, player.getUsername(), string2);
        int n = 0;
        for (Player player2 : this.proxy.getAllPlayers()) {
            String string3;
            Optional optional2;
            if (player2.getUniqueId().equals(player.getUniqueId()) || (optional2 = player2.getCurrentServer()).isEmpty() || string.equalsIgnoreCase(string3 = ((ServerConnection)optional2.get()).getServerInfo().getName()) || this.config.excludedServers.contains(RelayLinePlugin.normalize(string3))) continue;
            player2.sendMessage(component);
            ++n;
        }
        if (this.config.logRelaysToConsole) {
            this.logger.info("Relayed chat from {} to {} player(s): {}", new Object[]{string, n, this.plainText.serialize(component)});
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent serverConnectedEvent) {
        if (this.config == null) {
            return;
        }
        String string = serverConnectedEvent.getServer().getServerInfo().getName();
        this.lastKnownServers.put(serverConnectedEvent.getPlayer().getUniqueId(), string);
        Optional optional = serverConnectedEvent.getPreviousServer();
        if (optional.isEmpty()) {
            this.sendJoinNotice(serverConnectedEvent.getPlayer(), string);
            return;
        }
        if (!this.config.serverSwitchNoticesEnabled) {
            return;
        }
        String string2 = ((RegisteredServer)optional.get()).getServerInfo().getName();
        if (string2.equalsIgnoreCase(string)) {
            return;
        }
        if (this.config.excludedServers.contains(RelayLinePlugin.normalize(string2)) || this.config.excludedServers.contains(RelayLinePlugin.normalize(string))) {
            return;
        }
        Component component = this.formatServerSwitchNotice(serverConnectedEvent.getPlayer().getUsername(), string2, string);
        int n = 0;
        for (Player player : this.proxy.getAllPlayers()) {
            Optional optional2;
            if (!this.canSeeServerSwitchNotices(player.getUniqueId()) || (optional2 = player.getCurrentServer()).isPresent() && this.config.excludedServers.contains(RelayLinePlugin.normalize(((ServerConnection)optional2.get()).getServerInfo().getName()))) continue;
            player.sendMessage(component);
            ++n;
        }
        if (this.config.logServerSwitchesToConsole) {
            this.logger.info("Server switch notice for {} sent to {} player(s): {}", new Object[]{serverConnectedEvent.getPlayer().getUsername(), n, this.plainText.serialize(component)});
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        if (this.config == null || !this.config.joinLeaveNoticesEnabled || disconnectEvent.getLoginStatus() != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) {
            return;
        }
        Optional<String> optional = disconnectEvent.getPlayer().getCurrentServer().map(serverConnection -> serverConnection.getServerInfo().getName());
        String string = optional.orElseGet(() -> this.lastKnownServers.get(disconnectEvent.getPlayer().getUniqueId()));
        this.lastKnownServers.remove(disconnectEvent.getPlayer().getUniqueId());
        if (string == null || string.isBlank()) {
            return;
        }
        if (!this.matchesNoticeServer(this.config.leaveNoticeServer, string) || this.config.excludedServers.contains(RelayLinePlugin.normalize(string))) {
            return;
        }
        Component component = this.formatJoinLeaveNotice(this.config.leaveNoticeFormat, disconnectEvent.getPlayer().getUsername(), string);
        int n = this.sendNotice(component, disconnectEvent.getPlayer().getUniqueId());
        if (this.config.logJoinLeaveNoticesToConsole) {
            this.logger.info("Leave notice for {} sent to {} player(s): {}", new Object[]{disconnectEvent.getPlayer().getUsername(), n, this.plainText.serialize(component)});
        }
    }

    private void sendJoinNotice(Player player, String string) {
        if (!this.config.joinLeaveNoticesEnabled || !this.matchesNoticeServer(this.config.joinNoticeServer, string) || this.config.excludedServers.contains(RelayLinePlugin.normalize(string))) {
            return;
        }
        Component component = this.formatJoinLeaveNotice(this.config.joinNoticeFormat, player.getUsername(), string);
        int n = this.sendNotice(component);
        if (this.config.logJoinLeaveNoticesToConsole) {
            this.logger.info("Join notice for {} sent to {} player(s): {}", new Object[]{player.getUsername(), n, this.plainText.serialize(component)});
        }
    }

    private void registerCommands() {
        CommandManager commandManager = this.proxy.getCommandManager();
        this.unregisterCommands(commandManager);
        this.globalCommandMeta = commandManager.metaBuilder(this.config.globalCommand).aliases((String[])this.config.globalAliases.toArray(String[]::new)).plugin((Object)this).build();
        commandManager.register(this.globalCommandMeta, (Command)new ModeCommand(true));
        this.localCommandMeta = commandManager.metaBuilder(this.config.localCommand).aliases((String[])this.config.localAliases.toArray(String[]::new)).plugin((Object)this).build();
        commandManager.register(this.localCommandMeta, (Command)new ModeCommand(false));
        this.serverSwitchCommandMeta = commandManager.metaBuilder(this.config.serverSwitchCommand).aliases((String[])this.config.serverSwitchAliases.toArray(String[]::new)).plugin((Object)this).build();
        commandManager.register(this.serverSwitchCommandMeta, (Command)new ServerSwitchNoticeCommand());
        this.rootCommandMeta = commandManager.metaBuilder("relayline").aliases(new String[]{"rl", LEGACY_PLUGIN_ID, "lgc"}).plugin((Object)this).build();
        commandManager.register(this.rootCommandMeta, (Command)new RootCommand());
    }

    private void unregisterCommands(CommandManager commandManager) {
        if (this.globalCommandMeta != null) {
            commandManager.unregister(this.globalCommandMeta);
        }
        if (this.localCommandMeta != null) {
            commandManager.unregister(this.localCommandMeta);
        }
        if (this.serverSwitchCommandMeta != null) {
            commandManager.unregister(this.serverSwitchCommandMeta);
        }
        if (this.rootCommandMeta != null) {
            commandManager.unregister(this.rootCommandMeta);
        }
    }

    private Config loadConfig() throws IOException {
        Path path = this.dataDirectory.resolve("config.properties");
        if (Files.notExists(path, new LinkOption[0])) {
            Files.writeString(path, (CharSequence)DEFAULT_CONFIG, StandardCharsets.UTF_8, new OpenOption[0]);
        }
        Properties properties = new Properties();
        try (BufferedReader bufferedReader = Files.newBufferedReader(path, StandardCharsets.UTF_8);){
            properties.load(bufferedReader);
        }
        boolean bl = Boolean.parseBoolean(properties.getProperty("default-global", "true"));
        String string = properties.getProperty("format", "[%server_name%] %player% > %message%");
        String string2 = properties.getProperty("global-command", "global").trim();
        String string3 = properties.getProperty("local-command", "local").trim();
        boolean bl2 = Boolean.parseBoolean(properties.getProperty("require-permission", "false"));
        String string4 = properties.getProperty("permission", "relayline.toggle").trim();
        boolean bl3 = Boolean.parseBoolean(properties.getProperty("log-relays-to-console", "true"));
        String string5 = properties.getProperty("reload-permission", "relayline.reload").trim();
        boolean bl4 = Boolean.parseBoolean(properties.getProperty("server-switch-notices-enabled", "true"));
        boolean bl5 = Boolean.parseBoolean(properties.getProperty("default-see-server-switch-notices", "true"));
        boolean bl6 = Boolean.parseBoolean(properties.getProperty("log-server-switches-to-console", "true"));
        String string6 = properties.getProperty("server-switch-format", "<dark_gray>(<gold>*</gold><dark_gray>) <gray>%player% <dark_gray>(<gold>%old_server%->%new_server%<dark_gray>)");
        if (string6.contains("\u00c3\u00a2")) {
            string6 = "<dark_gray>(<gold>*</gold><dark_gray>) <gray>%player% <dark_gray>(<gold>%old_server%->%new_server%<dark_gray>)";
        }
        boolean bl7 = Boolean.parseBoolean(properties.getProperty("join-leave-notices-enabled", "true"));
        String string7 = properties.getProperty("join-notice-server", "lobby").trim();
        String string8 = properties.getProperty("leave-notice-server", "*").trim();
        boolean bl8 = Boolean.parseBoolean(properties.getProperty("log-join-leave-notices-to-console", "true"));
        String string9 = properties.getProperty("join-notice-format", "<dark_gray>(<green>+</green><dark_gray>) <gray>%player%</gray>");
        String string10 = properties.getProperty("leave-notice-format", "<dark_gray>(<red>-</red><dark_gray>) <gray>%player%</gray>");
        String string11 = properties.getProperty("server-switch-command", "servernotices").trim();
        HashMap<String, String> hashMap = new HashMap<String, String>();
        for (String string12 : properties.stringPropertyNames()) {
            if (!string12.startsWith("server-alias.")) continue;
            String string13 = string12.substring("server-alias.".length());
            hashMap.put(RelayLinePlugin.normalize(string13), properties.getProperty(string12).trim());
        }
        return new Config(bl, string, string2.isEmpty() ? "global" : string2, string3.isEmpty() ? "local" : string3, RelayLinePlugin.parseList(properties.getProperty("global-aliases", "gchat")), RelayLinePlugin.parseList(properties.getProperty("local-aliases", "lchat")), bl2, string4.isEmpty() ? "relayline.toggle" : string4, bl3, string5.isEmpty() ? "relayline.reload" : string5, bl4, bl5, bl6, string6, bl7, string7.isEmpty() ? "lobby" : string7, string8.isEmpty() ? "*" : string8, bl8, string9, string10, string11.isEmpty() ? "servernotices" : string11, RelayLinePlugin.parseList(properties.getProperty("server-switch-aliases", "switchalerts,serveralerts")), properties.getProperty("global-enabled-message", "Global chat enabled. Your chat will be relayed to other servers."), properties.getProperty("local-enabled-message", "Local chat enabled. Your chat will stay on your current server."), properties.getProperty("server-switch-notices-enabled-message", "Server switch notices enabled."), properties.getProperty("server-switch-notices-disabled-message", "Server switch notices disabled."), properties.getProperty("player-only-message", "Only players can use this command."), properties.getProperty("no-permission-message", "You do not have permission to use this command."), properties.getProperty("reload-success-message", "RelayLine reloaded."), properties.getProperty("reload-failed-message", "RelayLine reload failed. Check the Velocity console for details."), properties.getProperty("usage-message", "Usage: /relayline reload"), hashMap, RelayLinePlugin.parseNormalizedSet(properties.getProperty("excluded-servers", "")), RelayLinePlugin.parseNormalizedSet(properties.getProperty("local-only-message-prefixes", "xaero-waypoint:")));
    }

    private void loadPlayerModes() throws IOException {
        this.playerModes.clear();
        Path path = this.dataDirectory.resolve("player-modes.properties");
        if (Files.notExists(path, new LinkOption[0])) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path, new OpenOption[0]);){
            properties.load(inputStream);
        }
        for (String string : properties.stringPropertyNames()) {
            try {
                this.playerModes.put(UUID.fromString(string), Boolean.parseBoolean(properties.getProperty(string)));
            }
            catch (IllegalArgumentException illegalArgumentException) {
                this.logger.warn("Ignoring invalid UUID in player-modes.properties: {}", (Object)string);
            }
        }
    }

    private void loadServerSwitchNoticeModes() throws IOException {
        this.serverSwitchNoticeModes.clear();
        Path path = this.dataDirectory.resolve("server-switch-notices.properties");
        if (Files.notExists(path, new LinkOption[0])) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path, new OpenOption[0]);){
            properties.load(inputStream);
        }
        for (String string : properties.stringPropertyNames()) {
            try {
                this.serverSwitchNoticeModes.put(UUID.fromString(string), Boolean.parseBoolean(properties.getProperty(string)));
            }
            catch (IllegalArgumentException illegalArgumentException) {
                this.logger.warn("Ignoring invalid UUID in server-switch-notices.properties: {}", (Object)string);
            }
        }
    }

    private void savePlayerModes() throws IOException {
        if (this.config == null) {
            return;
        }
        Properties properties = new Properties();
        for (Map.Entry<UUID, Boolean> entry : this.playerModes.entrySet()) {
            properties.setProperty(entry.getKey().toString(), entry.getValue().toString());
        }
        try (OutputStream outputStream = Files.newOutputStream(this.dataDirectory.resolve("player-modes.properties"), new OpenOption[0]);){
            properties.store(outputStream, "RelayLine player chat modes");
        }
    }

    private void saveServerSwitchNoticeModes() throws IOException {
        if (this.config == null) {
            return;
        }
        Properties properties = new Properties();
        for (Map.Entry<UUID, Boolean> entry : this.serverSwitchNoticeModes.entrySet()) {
            properties.setProperty(entry.getKey().toString(), entry.getValue().toString());
        }
        try (OutputStream outputStream = Files.newOutputStream(this.dataDirectory.resolve("server-switch-notices.properties"), new OpenOption[0]);){
            properties.store(outputStream, "RelayLine server switch notice modes");
        }
    }

    private boolean isGlobal(UUID uUID) {
        return this.playerModes.getOrDefault(uUID, this.config.defaultGlobal);
    }

    private void setGlobal(UUID uUID, boolean bl) {
        if (bl == this.config.defaultGlobal) {
            this.playerModes.remove(uUID);
        } else {
            this.playerModes.put(uUID, bl);
        }
        try {
            this.savePlayerModes();
        }
        catch (IOException iOException) {
            this.logger.warn("Failed to save chat mode for {}", (Object)uUID, (Object)iOException);
        }
    }

    private boolean canSeeServerSwitchNotices(UUID uUID) {
        return this.serverSwitchNoticeModes.getOrDefault(uUID, this.config.defaultSeeServerSwitchNotices);
    }

    private void setServerSwitchNotices(UUID uUID, boolean bl) {
        if (bl == this.config.defaultSeeServerSwitchNotices) {
            this.serverSwitchNoticeModes.remove(uUID);
        } else {
            this.serverSwitchNoticeModes.put(uUID, bl);
        }
        try {
            this.saveServerSwitchNoticeModes();
        }
        catch (IOException iOException) {
            this.logger.warn("Failed to save server switch notice mode for {}", (Object)uUID, (Object)iOException);
        }
    }

    private Component formatRelay(String string, String string2, String string3) {
        String string4 = this.config.serverAliases.getOrDefault(RelayLinePlugin.normalize(string), string);
        String string5 = RelayLinePlugin.normalizeMiniMessageColorAliases(this.config.format).replace("%server_name%", "<lgc_server>").replace("%server%", "<lgc_server>").replace("%player%", "<lgc_player>").replace("%message%", "<lgc_message>");
        try {
            return this.miniMessage.deserialize(string5, new TagResolver[]{Placeholder.component((String)"lgc_server", (ComponentLike)this.deserializeConfigComponent(string4)), Placeholder.component((String)"lgc_player", (ComponentLike)Component.text((String)string2)), Placeholder.component((String)"lgc_message", (ComponentLike)Component.text((String)string3))});
        }
        catch (RuntimeException runtimeException) {
            this.logger.warn("Invalid MiniMessage relay format in RelayLine config. Falling back to plain text.", (Throwable)runtimeException);
            return Component.text((String)this.config.format.replace("%server_name%", string).replace("%server%", string).replace("%player%", string2).replace("%message%", string3));
        }
    }

    private Component formatServerSwitchNotice(String string, String string2, String string3) {
        String string4 = this.config.serverAliases.getOrDefault(RelayLinePlugin.normalize(string2), string2);
        String string5 = this.config.serverAliases.getOrDefault(RelayLinePlugin.normalize(string3), string3);
        String string6 = RelayLinePlugin.normalizeMiniMessageColorAliases(this.config.serverSwitchFormat).replace("%player%", "<lgc_player>").replace("%server_left%", "<lgc_server_left>").replace("%server_joined%", "<lgc_server_joined>").replace("%old_server%", "<lgc_server_left>").replace("%new_server%", "<lgc_server_joined>");
        try {
            return this.miniMessage.deserialize(string6, new TagResolver[]{Placeholder.component((String)"lgc_player", (ComponentLike)Component.text((String)string)), Placeholder.component((String)"lgc_server_left", (ComponentLike)this.deserializeConfigComponent(string4)), Placeholder.component((String)"lgc_server_joined", (ComponentLike)this.deserializeConfigComponent(string5))});
        }
        catch (RuntimeException runtimeException) {
            this.logger.warn("Invalid MiniMessage server switch format in RelayLine config. Falling back to plain text.", (Throwable)runtimeException);
            return Component.text((String)this.config.serverSwitchFormat.replace("%player%", string).replace("%server_left%", string2).replace("%server_joined%", string3).replace("%old_server%", string2).replace("%new_server%", string3));
        }
    }

    private Component formatJoinLeaveNotice(String string, String string2, String string3) {
        String string4 = this.config.serverAliases.getOrDefault(RelayLinePlugin.normalize(string3), string3);
        String string5 = RelayLinePlugin.normalizeMiniMessageColorAliases(string).replace("%player%", "<lgc_player>").replace("%server_name%", "<lgc_server>").replace("%server%", "<lgc_server>");
        try {
            return this.miniMessage.deserialize(string5, new TagResolver[]{Placeholder.component((String)"lgc_player", (ComponentLike)Component.text((String)string2)), Placeholder.component((String)"lgc_server", (ComponentLike)this.deserializeConfigComponent(string4))});
        }
        catch (RuntimeException runtimeException) {
            this.logger.warn("Invalid MiniMessage join/leave format in RelayLine config. Falling back to plain text.", (Throwable)runtimeException);
            return Component.text((String)string.replace("%player%", string2).replace("%server_name%", string3).replace("%server%", string3));
        }
    }

    private int sendNotice(Component component) {
        return this.sendNotice(component, null);
    }

    private int sendNotice(Component component, UUID uUID) {
        int n = 0;
        for (Player player : this.proxy.getAllPlayers()) {
            Optional optional;
            if (uUID != null && player.getUniqueId().equals(uUID) || !this.canSeeServerSwitchNotices(player.getUniqueId()) || (optional = player.getCurrentServer()).isPresent() && this.config.excludedServers.contains(RelayLinePlugin.normalize(((ServerConnection)optional.get()).getServerInfo().getName()))) continue;
            player.sendMessage(component);
            ++n;
        }
        return n;
    }

    private boolean matchesNoticeServer(String string, String string2) {
        if (string == null || string.isBlank()) {
            return false;
        }
        String string3 = RelayLinePlugin.normalize(string.trim());
        return string3.equals("*") || string3.equals("any") || string3.equals(RelayLinePlugin.normalize(string2));
    }

    private Component deserializeConfigComponent(String string) {
        try {
            return this.miniMessage.deserialize(RelayLinePlugin.normalizeMiniMessageColorAliases(string));
        }
        catch (RuntimeException runtimeException) {
            this.logger.warn("Invalid MiniMessage value in RelayLine config: {}", (Object)string, (Object)runtimeException);
            return Component.text((String)string);
        }
    }

    private static String normalizeMiniMessageColorAliases(String string) {
        return string.replace("<dark_grey", "<dark_gray").replace("</dark_grey>", "</dark_gray>").replace("<grey", "<gray").replace("</grey>", "</gray>");
    }

    private void sendConfigMessage(CommandSource commandSource, String string) {
        commandSource.sendMessage(this.deserializeConfigComponent(string));
    }

    private void reload(CommandSource commandSource) {
        try {
            Config config;
            this.config = config = this.loadConfig();
            this.loadPlayerModes();
            this.loadServerSwitchNoticeModes();
            this.registerCommands();
            this.sendConfigMessage(commandSource, this.config.reloadSuccessMessage);
            this.logger.info("RelayLine reloaded.");
        }
        catch (IOException | RuntimeException exception) {
            this.logger.error("Failed to reload RelayLine", (Throwable)exception);
            this.sendConfigMessage(commandSource, this.config.reloadFailedMessage);
        }
    }

    private static Set<String> parseNormalizedSet(String string) {
        HashSet<String> hashSet = new HashSet<String>();
        for (String string2 : RelayLinePlugin.parseList(string)) {
            hashSet.add(RelayLinePlugin.normalize(string2));
        }
        return hashSet;
    }

    private static Set<String> parseList(String string) {
        HashSet<String> hashSet = new HashSet<String>();
        if (string == null || string.isBlank()) {
            return hashSet;
        }
        for (String string2 : string.split(",")) {
            String string3 = string2.trim();
            if (string3.isEmpty()) continue;
            hashSet.add(string3);
        }
        return hashSet;
    }

    private static String normalize(String string) {
        return string.toLowerCase(Locale.ROOT);
    }

    private record Config(boolean defaultGlobal, String format, String globalCommand, String localCommand, Set<String> globalAliases, Set<String> localAliases, boolean requirePermission, String permission, boolean logRelaysToConsole, String reloadPermission, boolean serverSwitchNoticesEnabled, boolean defaultSeeServerSwitchNotices, boolean logServerSwitchesToConsole, String serverSwitchFormat, boolean joinLeaveNoticesEnabled, String joinNoticeServer, String leaveNoticeServer, boolean logJoinLeaveNoticesToConsole, String joinNoticeFormat, String leaveNoticeFormat, String serverSwitchCommand, Set<String> serverSwitchAliases, String globalEnabledMessage, String localEnabledMessage, String serverSwitchNoticesEnabledMessage, String serverSwitchNoticesDisabledMessage, String playerOnlyMessage, String noPermissionMessage, String reloadSuccessMessage, String reloadFailedMessage, String usageMessage, Map<String, String> serverAliases, Set<String> excludedServers, Set<String> localOnlyMessagePrefixes) {
    }

    private final class ModeCommand
    implements SimpleCommand {
        private final boolean global;

        private ModeCommand(boolean bl) {
            this.global = bl;
        }

        public void execute(SimpleCommand.Invocation invocation) {
            CommandSource commandSource = invocation.source();
            if (!(commandSource instanceof Player)) {
                RelayLinePlugin.this.sendConfigMessage(commandSource, RelayLinePlugin.this.config.playerOnlyMessage);
                return;
            }
            Player player = (Player)commandSource;
            if (RelayLinePlugin.this.config.requirePermission && !commandSource.hasPermission(RelayLinePlugin.this.config.permission)) {
                RelayLinePlugin.this.sendConfigMessage(commandSource, RelayLinePlugin.this.config.noPermissionMessage);
                return;
            }
            RelayLinePlugin.this.setGlobal(player.getUniqueId(), this.global);
            RelayLinePlugin.this.sendConfigMessage((CommandSource)player, this.global ? RelayLinePlugin.this.config.globalEnabledMessage : RelayLinePlugin.this.config.localEnabledMessage);
        }

        public boolean hasPermission(SimpleCommand.Invocation invocation) {
            return true;
        }
    }

    private final class ServerSwitchNoticeCommand
    implements SimpleCommand {
        private ServerSwitchNoticeCommand() {
        }

        public void execute(SimpleCommand.Invocation invocation) {
            CommandSource commandSource = invocation.source();
            if (!(commandSource instanceof Player)) {
                RelayLinePlugin.this.sendConfigMessage(commandSource, RelayLinePlugin.this.config.playerOnlyMessage);
                return;
            }
            Player player = (Player)commandSource;
            boolean bl = !RelayLinePlugin.this.canSeeServerSwitchNotices(player.getUniqueId());
            RelayLinePlugin.this.setServerSwitchNotices(player.getUniqueId(), bl);
            RelayLinePlugin.this.sendConfigMessage((CommandSource)player, bl ? RelayLinePlugin.this.config.serverSwitchNoticesEnabledMessage : RelayLinePlugin.this.config.serverSwitchNoticesDisabledMessage);
        }
    }

    private final class RootCommand
    implements SimpleCommand {
        private RootCommand() {
        }

        public void execute(SimpleCommand.Invocation invocation) {
            CommandSource commandSource = invocation.source();
            String[] stringArray = (String[])invocation.arguments();
            if (stringArray.length == 1 && stringArray[0].equalsIgnoreCase("reload")) {
                if (!commandSource.hasPermission(RelayLinePlugin.this.config.reloadPermission)) {
                    RelayLinePlugin.this.sendConfigMessage(commandSource, RelayLinePlugin.this.config.noPermissionMessage);
                    return;
                }
                RelayLinePlugin.this.reload(commandSource);
                return;
            }
            RelayLinePlugin.this.sendConfigMessage(commandSource, RelayLinePlugin.this.config.usageMessage);
        }

        public List<String> suggest(SimpleCommand.Invocation invocation) {
            if (((String[])invocation.arguments()).length <= 1) {
                return List.of("reload");
            }
            return List.of();
        }
    }
}
