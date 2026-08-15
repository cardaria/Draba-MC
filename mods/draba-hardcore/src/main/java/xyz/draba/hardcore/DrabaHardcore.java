package xyz.draba.hardcore;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.draba.hardcore.network.HardcoreUiStatePayload;
import xyz.draba.hardcore.network.SpectateActionPayload;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DrabaHardcore implements ModInitializer {
    public static final String MOD_ID = "draba_hardcore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String STATE_FILE_NAME = "draba-hardcore-state.json";
    private static final DateTimeFormatter RETURN_DATE = DateTimeFormatter
            .ofPattern("d MMMM uuuu, HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);
    private static final Map<UUID, UUID> SPECTATE_TARGETS = new HashMap<>();
    private static final Map<UUID, WaitingAnchor> WAITING_ANCHORS = new HashMap<>();
    private static final Set<Container> LOADED_STORAGE = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<ItemEntity> LOADED_ITEMS = Collections.newSetFromMap(new IdentityHashMap<>());

    private static HardcoreState state;
    private static MinecraftServer server;
    private static int tickCounter;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(HardcoreUiStatePayload.TYPE, HardcoreUiStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SpectateActionPayload.TYPE, SpectateActionPayload.CODEC);
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return;
        }

        try {
            state = new HardcoreState(FabricLoader.getInstance().getConfigDir().resolve(STATE_FILE_NAME));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load Hardcore state", exception);
        }

        ServerLifecycleEvents.SERVER_STARTED.register(startedServer -> server = startedServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(stoppedServer -> {
            SPECTATE_TARGETS.clear();
            WAITING_ANCHORS.clear();
            LOADED_STORAGE.clear();
            LOADED_ITEMS.clear();
            server = null;
        });
        ServerPlayerEvents.JOIN.register(DrabaHardcore::onJoin);
        ServerPlayerEvents.LEAVE.register(DrabaHardcore::onLeave);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> enforcePlayerState(newPlayer, true));
        ServerTickEvents.END_SERVER_TICK.register(DrabaHardcore::onServerTick);
        ServerChunkEvents.CHUNK_LOAD.register(DrabaHardcore::loadChunkStorage);
        ServerChunkEvents.CHUNK_UNLOAD.register(DrabaHardcore::unloadChunkStorage);
        ServerEntityEvents.ENTITY_LOAD.register(DrabaHardcore::loadEntityStorage);
        ServerEntityEvents.ENTITY_UNLOAD.register(DrabaHardcore::unloadEntityStorage);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        ServerPlayNetworking.registerGlobalReceiver(SpectateActionPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handleSpectateAction(context.player(), payload)));
        LOGGER.info("Draba Hardcore initialized with a {} day death cooldown", HardcoreState.DEFAULT_COOLDOWN.toDays());
    }

    public static void onActualDeath(ServerPlayer player) {
        if (state == null || server == null || state.isActive(player.getUUID(), Instant.now())) {
            return;
        }

        Instant now = Instant.now();
        try {
            HardcoreState.DeathRecord death = state.beginDeath(
                    player.getUUID(), player.getGameProfile().name(), now);
            clearPlayerPossessions(player);
            purgeLoadedStorage();
            LOGGER.info("Hardcore death recorded for {} ({}); eligible at {}",
                    player.getGameProfile().name(), player.getUUID(), death.cooldown().eligibleAt());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not durably record Hardcore death for " + player.getUUID(), exception);
        }
    }

    public static void claimPlacedStorage(ServerPlayer player, BlockPos pos) {
        if (state == null || !(player.level().getBlockEntity(pos) instanceof Container)) {
            return;
        }
        Container container = (Container) player.level().getBlockEntity(pos);
        LOADED_STORAGE.add(container);
        // A filled shulker placed from a player inventory becomes ordinary shared
        // storage; its initial contents are contributions from this life.
        OwnershipLedger.OwnerLife owner = ownerLife(player);
        for (ItemStack stack : container) {
            if (!stack.isEmpty()) {
                ItemOwnership.markOwned(stack, owner);
            }
        }
        container.setChanged();
    }

    public static void claimPlacedStorageEntity(ServerPlayer player, Entity entity) {
        if (state == null || !(entity instanceof Container)) {
            return;
        }
        LOADED_STORAGE.add((Container) entity);
    }

    static OwnershipLedger.OwnerLife ownerLife(ServerPlayer player) {
        return state.ownerLife(player.getUUID(), player.getGameProfile().name());
    }

    public static void markPlayerDrop(ServerPlayer player, ItemStack stack) {
        ItemOwnership.markOwned(stack, ownerLife(player));
    }

    private static void clearPlayerPossessions(ServerPlayer player) {
        player.closeContainer();
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
        player.setExperiencePoints(0);
        player.setExperienceLevels(0);
        player.totalExperience = 0;
        player.experienceProgress = 0.0F;
        player.inventoryMenu.broadcastChanges();
    }

    private static void onJoin(ServerPlayer player) {
        enforcePlayerState(player, true);
    }

    private static void onLeave(ServerPlayer player) {
        SPECTATE_TARGETS.remove(player.getUUID());
        WAITING_ANCHORS.remove(player.getUUID());
    }

    private static void onServerTick(MinecraftServer minecraftServer) {
        tickCounter++;
        boolean updateDisplay = tickCounter % 20 == 0;
        Instant now = Instant.now();
        for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
            clearPlayerOwnership(player);
            HardcoreState.Cooldown cooldown = state.cooldown(player.getUUID()).orElse(null);
            if (cooldown == null) {
                clearSpectateSession(player, false);
                continue;
            }

            if (!now.isBefore(cooldown.eligibleAt())) {
                revive(player, "Your death cooldown has ended.");
                continue;
            }
            if (player.gameMode() != GameType.SPECTATOR && player.isAlive()) {
                player.setGameMode(GameType.SPECTATOR);
            }
            enforceSpectateSession(player, now);
            if (updateDisplay) {
                sendUiState(player, cooldown, now);
            }
        }
    }

    private static void clearPlayerOwnership(ServerPlayer player) {
        for (ItemStack stack : player.getInventory()) {
            ItemOwnership.clear(stack);
        }
        for (ItemStack stack : player.getEnderChestInventory()) {
            ItemOwnership.clear(stack);
        }
        ItemOwnership.clear(player.containerMenu.getCarried());
    }

    private static void enforcePlayerState(ServerPlayer player, boolean announce) {
        HardcoreState.Cooldown cooldown = state.cooldown(player.getUUID()).orElse(null);
        if (cooldown == null) {
            clearSpectateSession(player, true);
            return;
        }
        Instant now = Instant.now();
        if (!now.isBefore(cooldown.eligibleAt())) {
            revive(player, "Your death cooldown has ended.");
            return;
        }

        if (player.isAlive()) {
            player.setGameMode(GameType.SPECTATOR);
        }
        if (!SPECTATE_TARGETS.containsKey(player.getUUID())) {
            moveToWaitingPosition(player);
        }
        enforceSpectateSession(player, now);
        sendUiState(player, cooldown, now);
        if (announce) {
            player.sendSystemMessage(Component.literal("Hardcore cooldown active until "
                    + RETURN_DATE.format(cooldown.eligibleAt()) + ".")
                    .withStyle(ChatFormatting.GOLD));
            if (!ServerPlayNetworking.canSend(player, HardcoreUiStatePayload.TYPE)) {
                player.sendSystemMessage(Component.literal(
                                "Install or update the Draba Hardcore client mod to use the spectate controls.")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    private static void revive(ServerPlayer player, String message) {
        try {
            state.clearCooldown(player.getUUID());
        } catch (IOException exception) {
            LOGGER.error("Failed to clear expired cooldown for {}", player.getUUID(), exception);
            return;
        }
        clearSpectateSession(player, true);
        teleportToWorldSpawn(player);
        player.setGameMode(GameType.SURVIVAL);
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("A new life begins at world spawn with an empty inventory.")
                .withStyle(ChatFormatting.GRAY));
        LOGGER.info("Hardcore cooldown cleared for {} ({})", player.getGameProfile().name(), player.getUUID());
    }

    private static void teleportToWorldSpawn(ServerPlayer player) {
        LevelData.RespawnData respawn = server.getRespawnData();
        ServerLevel level = server.getLevel(respawn.dimension());
        if (level == null) {
            level = server.overworld();
        }
        BlockPos pos = respawn.pos();
        player.teleportTo(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                Set.of(), respawn.yaw(), respawn.pitch(), false);
    }

    private static String formatRemaining(Duration remaining) {
        long totalSeconds = Math.max(0, remaining.toSeconds());
        long days = totalSeconds / 86_400;
        long hours = totalSeconds % 86_400 / 3_600;
        long minutes = totalSeconds % 3_600 / 60;
        return days + "d " + hours + "h " + minutes + "m remaining";
    }

    private static void handleSpectateAction(ServerPlayer player, SpectateActionPayload payload) {
        if (!payload.isValid()) {
            return;
        }
        Instant now = Instant.now();
        HardcoreState.Cooldown cooldown = state.cooldown(player.getUUID()).orElse(null);
        if (cooldown == null || !now.isBefore(cooldown.eligibleAt())) {
            return;
        }

        if (payload.action() == SpectateActionPayload.STOP) {
            SPECTATE_TARGETS.remove(player.getUUID());
            moveToWaitingPosition(player);
            sendUiState(player, cooldown, now);
            return;
        }

        List<ServerPlayer> targets = eligibleTargets(player, now);
        List<UUID> targetIds = targets.stream().map(ServerPlayer::getUUID).toList();
        UUID selected;
        if (payload.action() == SpectateActionPayload.START) {
            selected = SpectateTargetSelector.first(targetIds).orElse(null);
        } else {
            selected = SpectateTargetSelector.cycle(
                    targetIds,
                    SPECTATE_TARGETS.get(player.getUUID()),
                    payload.action()).orElse(null);
        }

        if (selected == null) {
            SPECTATE_TARGETS.remove(player.getUUID());
            moveToWaitingPosition(player);
        } else {
            SPECTATE_TARGETS.put(player.getUUID(), selected);
            ServerPlayer target = server.getPlayerList().getPlayer(selected);
            if (target != null && player.getCamera() != target) {
                player.setCamera(target);
            }
        }
        sendUiState(player, cooldown, now);
    }

    private static void enforceSpectateSession(ServerPlayer observer, Instant now) {
        List<ServerPlayer> targets = eligibleTargets(observer, now);
        UUID selectedId = SPECTATE_TARGETS.get(observer.getUUID());
        ServerPlayer selected = targets.stream()
                .filter(target -> target.getUUID().equals(selectedId))
                .findFirst()
                .orElse(null);

        if (selectedId != null && selected == null) {
            selected = SpectateTargetSelector.first(targets).orElse(null);
            if (selected == null) {
                SPECTATE_TARGETS.remove(observer.getUUID());
                moveToWaitingPosition(observer);
            } else {
                SPECTATE_TARGETS.put(observer.getUUID(), selected.getUUID());
            }
        }

        if (selected != null) {
            observer.setDeltaMovement(Vec3.ZERO);
            if (observer.getCamera() != selected) {
                observer.setCamera(selected);
            }
            return;
        }

        if (observer.getCamera() != observer) {
            observer.setCamera(observer);
        }
        WaitingAnchor anchor = WAITING_ANCHORS.get(observer.getUUID());
        if (anchor == null) {
            moveToWaitingPosition(observer);
            anchor = WAITING_ANCHORS.get(observer.getUUID());
        }
        observer.setDeltaMovement(Vec3.ZERO);
        if (anchor != null && (observer.level() != anchor.level()
                || observer.position().distanceToSqr(anchor.position()) > 0.0025D)) {
            observer.teleportTo(anchor.level(),
                    anchor.position().x(), anchor.position().y(), anchor.position().z(),
                    Set.of(), anchor.yaw(), anchor.pitch(), false);
        }
    }

    private static List<ServerPlayer> eligibleTargets(ServerPlayer observer, Instant now) {
        return server.getPlayerList().getPlayers().stream()
                .filter(target -> !target.getUUID().equals(observer.getUUID()))
                .filter(ServerPlayer::isAlive)
                .filter(target -> target.gameMode() != GameType.SPECTATOR)
                .filter(target -> !state.isActive(target.getUUID(), now))
                .sorted(Comparator
                        .comparing((ServerPlayer target) -> target.getGameProfile().name(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(target -> target.getUUID().toString()))
                .toList();
    }

    private static void moveToWaitingPosition(ServerPlayer player) {
        if (server == null) {
            return;
        }
        if (player.getCamera() != player) {
            player.setCamera(player);
        }
        teleportToWorldSpawn(player);
        WAITING_ANCHORS.put(player.getUUID(), new WaitingAnchor(
                player.level(), player.position(), player.getYRot(), player.getXRot()));
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void clearSpectateSession(ServerPlayer player, boolean sendInactive) {
        boolean changed = SPECTATE_TARGETS.remove(player.getUUID()) != null;
        changed |= WAITING_ANCHORS.remove(player.getUUID()) != null;
        // Only reset cameras owned by this cooldown system. Alive players may be
        // using Draba Spectate's separate voluntary camera session.
        if (changed && player.getCamera() != player) {
            player.setCamera(player);
            changed = true;
        }
        if ((sendInactive || changed) && ServerPlayNetworking.canSend(player, HardcoreUiStatePayload.TYPE)) {
            ServerPlayNetworking.send(player, HardcoreUiStatePayload.inactive());
        }
    }

    private static void sendUiState(ServerPlayer observer, HardcoreState.Cooldown cooldown, Instant now) {
        if (!ServerPlayNetworking.canSend(observer, HardcoreUiStatePayload.TYPE)) {
            return;
        }
        List<ServerPlayer> targets = eligibleTargets(observer, now);
        UUID selectedId = SPECTATE_TARGETS.get(observer.getUUID());
        int selectedIndex = -1;
        for (int index = 0; index < targets.size(); index++) {
            if (targets.get(index).getUUID().equals(selectedId)) {
                selectedIndex = index;
                break;
            }
        }
        boolean watching = selectedIndex >= 0;
        String targetName = watching ? targets.get(selectedIndex).getGameProfile().name() : "";
        ServerPlayNetworking.send(observer, new HardcoreUiStatePayload(
                true,
                cooldown.eligibleAt().toEpochMilli(),
                watching,
                targetName,
                selectedIndex,
                targets.size()));
    }

    private static void loadChunkStorage(ServerLevel level, LevelChunk chunk, boolean generated) {
        if (state == null) {
            return;
        }
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof Container container) {
                LOADED_STORAGE.add(container);
                purgeContainer(container);
            }
        }
    }

    private static void unloadChunkStorage(ServerLevel level, LevelChunk chunk) {
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof Container container) {
                LOADED_STORAGE.remove(container);
            }
        }
    }

    private static void loadEntityStorage(Entity entity, ServerLevel level) {
        if (entity instanceof Container container) {
            LOADED_STORAGE.add(container);
            purgeContainer(container);
        }
        if (entity instanceof ItemEntity itemEntity) {
            LOADED_ITEMS.add(itemEntity);
            purgeItemEntity(itemEntity);
        }
    }

    private static void unloadEntityStorage(Entity entity, ServerLevel level) {
        if (entity instanceof Container container) {
            LOADED_STORAGE.remove(container);
        }
        if (entity instanceof ItemEntity itemEntity) {
            LOADED_ITEMS.remove(itemEntity);
        }
    }

    private static void purgeLoadedStorage() {
        List.copyOf(LOADED_STORAGE).forEach(DrabaHardcore::purgeContainer);
        List.copyOf(LOADED_ITEMS).forEach(DrabaHardcore::purgeItemEntity);
    }

    private static void purgeContainer(Container container) {
        int removed = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            removed += ItemOwnership.purge(container.getItem(slot), state::isInvalidLife);
        }
        if (removed > 0) {
            container.setChanged();
            LOGGER.debug("Purged {} invalid owned item(s) from loaded storage", removed);
        }
    }

    private static void purgeItemEntity(ItemEntity entity) {
        ItemStack stack = entity.getItem();
        int removed = ItemOwnership.purge(stack, state::isInvalidLife);
        if (removed > 0) {
            if (stack.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(stack);
            }
        }
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hc")
                .then(Commands.literal("status")
                        .executes(context -> showStatus(context.getSource(),
                                context.getSource().getPlayerOrException().nameAndId()))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .executes(context -> showStatus(context.getSource(),
                                        firstProfile(context, "player")))))
                .then(Commands.literal("cooldown")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(context -> clearCooldown(context.getSource(),
                                                firstProfile(context, "player")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("days", IntegerArgumentType.integer(1, 365))
                                                .executes(context -> setCooldown(
                                                        context.getSource(),
                                                        firstProfile(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "days"))))))));
    }

    private static NameAndId firstProfile(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                          String argumentName)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return GameProfileArgument.getGameProfiles(context, argumentName).iterator().next();
    }

    private static int showStatus(CommandSourceStack source, NameAndId profile) {
        HardcoreState.Cooldown cooldown = state.cooldown(profile.id()).orElse(null);
        if (cooldown == null) {
            source.sendSuccess(() -> Component.literal(profile.name() + " has no death cooldown.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        Instant now = Instant.now();
        source.sendSuccess(() -> Component.literal(profile.name() + " may return at "
                + RETURN_DATE.format(cooldown.eligibleAt()) + " (" + formatRemaining(cooldown.remainingAt(now)) + ").")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int clearCooldown(CommandSourceStack source, NameAndId profile) {
        if (state.cooldown(profile.id()).isEmpty()) {
            source.sendFailure(Component.literal(profile.name() + " has no death cooldown."));
            return 0;
        }
        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(profile.id());
        if (onlinePlayer != null) {
            revive(onlinePlayer, "An administrator ended your death cooldown.");
        } else {
            try {
                Instant alreadyExpired = Instant.now().minusSeconds(1);
                state.setCooldown(profile.id(), profile.name(), alreadyExpired, Duration.ofNanos(1));
            } catch (IOException exception) {
                LOGGER.error("Failed to expire cooldown for offline player {}", profile.id(), exception);
                source.sendFailure(Component.literal("The cooldown state could not be saved."));
                return 0;
            }
        }
        source.sendSuccess(() -> Component.literal("Revived " + profile.name()
                + (onlinePlayer == null ? " for their next login." : ".")), true);
        return 1;
    }

    private static int setCooldown(CommandSourceStack source, NameAndId profile, int days) {
        try {
            HardcoreState.Cooldown cooldown = state.setCooldown(
                    profile.id(), profile.name(), Instant.now(), Duration.ofDays(days));
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(profile.id());
            if (onlinePlayer != null) {
                enforcePlayerState(onlinePlayer, true);
            }
            source.sendSuccess(() -> Component.literal("Set " + profile.name()
                    + "'s cooldown through " + RETURN_DATE.format(cooldown.eligibleAt()) + "."), true);
            return 1;
        } catch (IOException exception) {
            LOGGER.error("Failed to set cooldown for {}", profile.id(), exception);
            source.sendFailure(Component.literal("The cooldown state could not be saved."));
            return 0;
        }
    }

    private record WaitingAnchor(ServerLevel level, Vec3 position, float yaw, float pitch) {
    }
}
