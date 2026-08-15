package xyz.draba.spectate;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.draba.spectate.SpectateSessionStore.StoredSession;
import xyz.draba.spectate.network.ClientModPolicyChallengePayload;
import xyz.draba.spectate.network.ClientModPolicyResponsePayload;
import xyz.draba.spectate.network.SpectateActionPayload;
import xyz.draba.spectate.network.SpectateStatePayload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DrabaSpectate implements ModInitializer {
    public static final String MOD_ID = "draba_spectate";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String STATE_FILE = "draba-spectate-sessions.json";
    private static final double ACTIVE_THREAT_RADIUS = 16.0D;
    private static final Map<UUID, ActiveSession> ACTIVE_SESSIONS = new HashMap<>();
    private static final Map<UUID, ArmingSession> ARMING_SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_DAMAGE_TICKS = new HashMap<>();

    private static SpectateSessionStore store;
    private static MinecraftServer server;
    private static long tickCounter;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(SpectateStatePayload.TYPE, SpectateStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SpectateActionPayload.TYPE, SpectateActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                ClientModPolicyChallengePayload.TYPE, ClientModPolicyChallengePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                ClientModPolicyResponsePayload.TYPE, ClientModPolicyResponsePayload.CODEC);
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return;
        }

        try {
            store = new SpectateSessionStore(
                    FabricLoader.getInstance().getConfigDir().resolve(STATE_FILE));
            ClientModPolicy.initialize(FabricLoader.getInstance().getConfigDir()
                    .resolve(ClientModPolicy.CONFIG_FILE));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load voluntary spectate recovery state", exception);
        }

        ServerLifecycleEvents.SERVER_STARTED.register(startedServer -> {
            server = startedServer;
            if (store.size() > 0) {
                LOGGER.warn("Loaded {} interrupted spectate session(s) for safe recovery", store.size());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(DrabaSpectate::onServerStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(stoppedServer -> {
            ACTIVE_SESSIONS.clear();
            ARMING_SESSIONS.clear();
            LAST_DAMAGE_TICKS.clear();
            ClientModPolicy.onServerStopped();
            server = null;
            tickCounter = 0;
        });
        ServerPlayerEvents.JOIN.register(DrabaSpectate::onJoin);
        ServerPlayerEvents.LEAVE.register(DrabaSpectate::onLeave);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(DrabaSpectate::onAfterDamage);
        ServerTickEvents.END_SERVER_TICK.register(DrabaSpectate::onServerTick);
        ServerPlayNetworking.registerGlobalReceiver(SpectateActionPayload.TYPE,
                (payload, context) -> context.server().execute(
                        () -> handleAction(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ClientModPolicyResponsePayload.TYPE,
                (payload, context) -> context.server().execute(
                        () -> ClientModPolicy.handleResponse(context.player(), payload)));
        LOGGER.info("Draba Spectate initialized with guarded starts, locked cameras, and crash-safe restoration");
        LOGGER.info("Voxy origin-world retention available={}",
                VoxyWorldLease.integrationAvailable());
    }

    private static void onJoin(ServerPlayer player) {
        ClientModPolicy.onJoin(player);
        store.get(player.getUUID()).ifPresent(origin -> {
            if (restoreOrigin(player, origin)) {
                try {
                    store.remove(player.getUUID());
                    player.sendSystemMessage(Component.literal(
                                    "Your interrupted spectate session was safely restored.")
                            .withStyle(ChatFormatting.GREEN));
                    LOGGER.info("Recovered interrupted spectate session for {} ({})",
                            player.getGameProfile().name(), player.getUUID());
                } catch (IOException exception) {
                    LOGGER.error("Restored {} but could not clear its recovery record",
                            player.getUUID(), exception);
                }
            } else {
                LOGGER.error("Could not restore interrupted spectate session for {}", player.getUUID());
            }
        });
        sendState(player);
    }

    private static void onLeave(ServerPlayer player) {
        ClientModPolicy.onLeave(player);
        cancelArming(player, null, false);
        stopSession(player, false, null, true);
    }

    private static void onServerStopping(MinecraftServer minecraftServer) {
        ARMING_SESSIONS.clear();
        for (UUID playerId : new ArrayList<>(ACTIVE_SESSIONS.keySet())) {
            ServerPlayer player = minecraftServer.getPlayerList().getPlayer(playerId);
            if (player != null) {
                stopSession(player, false, null, true);
            }
        }
    }

    private static void onAfterDamage(
            LivingEntity entity,
            DamageSource source,
            float baseDamageTaken,
            float damageTaken,
            boolean blocked) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        LAST_DAMAGE_TICKS.put(player.getUUID(), tickCounter);
        cancelArming(
                player,
                SpectateSafetyRules.Blocker.RECENT_DAMAGE.cancellationMessage(),
                true);
    }

    private static void onServerTick(MinecraftServer minecraftServer) {
        ClientModPolicy.onServerTick(minecraftServer);
        tickCounter++;
        for (UUID playerId : new ArrayList<>(ARMING_SESSIONS.keySet())) {
            ServerPlayer player = minecraftServer.getPlayerList().getPlayer(playerId);
            if (player != null) {
                enforceArming(player);
            }
        }
        for (UUID observerId : new ArrayList<>(ACTIVE_SESSIONS.keySet())) {
            ServerPlayer observer = minecraftServer.getPlayerList().getPlayer(observerId);
            if (observer == null) {
                continue;
            }
            enforceSession(observer);
        }

        if (tickCounter % 20 == 0) {
            LAST_DAMAGE_TICKS.entrySet().removeIf(entry ->
                    tickCounter - entry.getValue() >= SpectateSafetyRules.RECENT_DAMAGE_TICKS);
            for (ServerPlayer player : minecraftServer.getPlayerList().getPlayers()) {
                sendState(player);
            }
        }
    }

    private static void handleAction(ServerPlayer player, SpectateActionPayload payload) {
        if (!payload.isValid() || server == null) {
            return;
        }
        if (payload.action() == SpectateActionPayload.STOP) {
            if (cancelArming(player, "Spectate start cancelled.", true)) {
                return;
            }
            stopSession(player, true, "Returned to your previous position.", false);
            return;
        }

        ActiveSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) {
            if (payload.action() != SpectateActionPayload.START) {
                sendState(player);
                return;
            }
            beginArming(player, null);
            return;
        }

        List<ServerPlayer> targets = eligibleTargets(player);
        List<UUID> targetIds = targets.stream().map(ServerPlayer::getUUID).toList();
        UUID selected = SpectateTargetSelector.cycle(
                targetIds, session.targetId, payload.action()).orElse(null);
        if (selected == null) {
            stopSession(player, true, "No eligible players remain online.", false);
            return;
        }
        session.targetId = selected;
        attachToSelectedTarget(player, session, targets);
        sendState(player);
    }

    /**
     * Starts the same locked spectate session used by the client button, with an
     * optional explicit target for permission-controlled server commands.
     */
    public static boolean startWatching(ServerPlayer player, ServerPlayer requestedTarget) {
        if (server == null || player == null || requestedTarget == null
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || server.getPlayerList().getPlayer(requestedTarget.getUUID()) != requestedTarget) {
            return false;
        }
        return beginArming(player, requestedTarget);
    }

    /** Cancels a pending start or stops a voluntary session and restores its durable origin. */
    public static boolean stopWatching(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (cancelArming(player, "Spectate start cancelled.", true)) {
            return true;
        }
        if (!ACTIVE_SESSIONS.containsKey(player.getUUID())) {
            return false;
        }
        stopSession(player, true, "Returned to your previous position.", false);
        return true;
    }

    public static boolean isWatching(UUID playerId) {
        return ACTIVE_SESSIONS.containsKey(playerId) || ARMING_SESSIONS.containsKey(playerId);
    }

    /** Returns anonymous target totals without exposing the observer identities. */
    public static Map<UUID, Integer> activeTargetCounts() {
        Map<UUID, Integer> counts = new HashMap<>();
        for (ActiveSession session : ACTIVE_SESSIONS.values()) {
            counts.merge(session.targetId, 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    private static boolean beginArming(ServerPlayer player, ServerPlayer requestedTarget) {
        List<ServerPlayer> targets = eligibleTargets(player);
        if (targets.isEmpty()) {
            rejectStart(player, "No eligible players are online to spectate.");
            return false;
        }
        if (requestedTarget != null && targets.stream().noneMatch(
                target -> target.getUUID().equals(requestedTarget.getUUID()))) {
            rejectStart(player, "That player is not available to spectate.");
            return false;
        }

        ActiveSession existing = ACTIVE_SESSIONS.get(player.getUUID());
        if (existing != null) {
            ServerPlayer selected = requestedTarget != null ? requestedTarget : targets.getFirst();
            existing.targetId = selected.getUUID();
            attachToSelectedTarget(player, existing, targets);
            sendState(player);
            return true;
        }
        if (ARMING_SESSIONS.containsKey(player.getUUID())) {
            sendState(player);
            return true;
        }
        SafetyResult safety = evaluateSafety(player);
        if (!safety.allowed()) {
            rejectStart(player, safety.reason());
            return false;
        }

        UUID requestedTargetId = requestedTarget == null ? null : requestedTarget.getUUID();
        ARMING_SESSIONS.put(player.getUUID(), new ArmingSession(
                player.level().dimension(),
                player.position(),
                requestedTargetId,
                SpectateSafetyRules.ARMING_TICKS));
        player.sendSystemMessage(Component.literal(
                        "Stand still: spectate will begin in 5 seconds. Moving or taking damage cancels.")
                .withStyle(ChatFormatting.YELLOW));
        sendState(player);
        LOGGER.info("{} began the voluntary spectate safety countdown",
                player.getGameProfile().name());
        return true;
    }

    private static void enforceArming(ServerPlayer player) {
        ArmingSession arming = ARMING_SESSIONS.get(player.getUUID());
        if (arming == null) {
            return;
        }

        boolean changedDimension = !player.level().dimension().equals(arming.dimension);
        boolean moved = SpectateSafetyRules.movedBeyondAnchor(
                player.position().distanceToSqr(arming.anchor));
        Optional<SpectateSafetyRules.Blocker> blocker = SpectateSafetyRules.armingBlocker(
                safetySnapshot(player), moved, changedDimension);
        if (blocker.isPresent()) {
            cancelArming(player, blocker.get().cancellationMessage(), true);
            return;
        }

        List<ServerPlayer> targets = eligibleTargets(player);
        if (targets.isEmpty()) {
            cancelArming(player, "Spectate cancelled because no eligible players remain online.", true);
            return;
        }
        if (arming.requestedTargetId != null && targets.stream().noneMatch(
                target -> target.getUUID().equals(arming.requestedTargetId))) {
            cancelArming(player, "Spectate cancelled because that player is no longer available.", true);
            return;
        }

        arming.ticksRemaining--;
        if (arming.ticksRemaining <= 0) {
            completeArming(player, arming, targets);
        }
    }

    private static void completeArming(
            ServerPlayer player, ArmingSession arming, List<ServerPlayer> targets) {
        if (ARMING_SESSIONS.get(player.getUUID()) != arming) {
            return;
        }
        SafetyResult safety = evaluateSafety(player);
        if (!safety.allowed()) {
            cancelArming(player, "Spectate cancelled: " + safety.reason(), true);
            return;
        }
        ARMING_SESSIONS.remove(player.getUUID());

        StoredSession origin = new StoredSession(
                player.getGameProfile().name(),
                player.level().dimension().identifier().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                player.gameMode().getName());
        try {
            store.put(player.getUUID(), origin);
        } catch (IOException exception) {
            LOGGER.error("Could not durably save spectate origin for {}", player.getUUID(), exception);
            rejectStart(player, "Spectate could not start because your return point was not saved.");
            return;
        }

        ServerPlayer selected = arming.requestedTargetId == null
                ? targets.getFirst()
                : targets.stream()
                        .filter(target -> target.getUUID().equals(arming.requestedTargetId))
                        .findFirst()
                        .orElse(null);
        if (selected == null) {
            try {
                store.remove(player.getUUID());
            } catch (IOException exception) {
                LOGGER.error("Could not clear unused spectate origin for {}", player.getUUID(), exception);
            }
            rejectStart(player, "Spectate cancelled because that player is no longer available.");
            return;
        }
        VoxyWorldLease originWorldLease = VoxyWorldLease.acquire(player.level());
        ActiveSession session = new ActiveSession(origin, selected.getUUID(), originWorldLease);
        ACTIVE_SESSIONS.put(player.getUUID(), session);
        try {
            player.closeContainer();
            if (player.getCamera() != player) {
                player.setCamera(player);
            }
            player.setGameMode(GameType.SPECTATOR);
            attachToSelectedTarget(player, session, targets);
            sendState(player);
            LOGGER.info("{} started voluntarily spectating {}",
                    player.getGameProfile().name(), selected.getGameProfile().name());
        } catch (RuntimeException exception) {
            LOGGER.error("Could not start spectate session for {}", player.getUUID(), exception);
            stopSession(player, true, "Spectate could not start; your prior state was restored.", false);
        }
    }

    private static boolean cancelArming(ServerPlayer player, String message, boolean notifyClient) {
        ArmingSession removed = ARMING_SESSIONS.remove(player.getUUID());
        if (removed == null) {
            return false;
        }
        if (message != null) {
            player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
        }
        if (notifyClient) {
            sendState(player);
        }
        LOGGER.info("{} cancelled the voluntary spectate safety countdown",
                player.getGameProfile().name());
        return true;
    }

    private static void rejectStart(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.literal(reason).withStyle(ChatFormatting.RED));
        sendState(player);
    }

    private static void enforceSession(ServerPlayer observer) {
        ActiveSession session = ACTIVE_SESSIONS.get(observer.getUUID());
        if (session == null) {
            return;
        }
        List<ServerPlayer> targets = eligibleTargets(observer);
        ServerPlayer selected = targets.stream()
                .filter(target -> target.getUUID().equals(session.targetId))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            selected = targets.stream().findFirst().orElse(null);
            if (selected == null) {
                stopSession(observer, true, "No eligible players remain online.", false);
                return;
            }
            session.targetId = selected.getUUID();
        }

        if (observer.gameMode() != GameType.SPECTATOR) {
            observer.setGameMode(GameType.SPECTATOR);
        }
        observer.setDeltaMovement(Vec3.ZERO);
        if (observer.getCamera() != selected) {
            observer.setCamera(selected);
        }
    }

    private static void attachToSelectedTarget(
            ServerPlayer observer, ActiveSession session, List<ServerPlayer> targets) {
        ServerPlayer selected = targets.stream()
                .filter(target -> target.getUUID().equals(session.targetId))
                .findFirst()
                .orElse(null);
        if (selected != null && observer.getCamera() != selected) {
            observer.setCamera(selected);
        }
        observer.setDeltaMovement(Vec3.ZERO);
    }

    private static void stopSession(
            ServerPlayer player,
            boolean notifyClient,
            String message,
            boolean retainRecoveryUntilNextJoin) {
        ActiveSession session = ACTIVE_SESSIONS.remove(player.getUUID());
        if (session == null) {
            if (notifyClient) {
                sendState(player);
            }
            return;
        }

        boolean restored;
        try {
            restored = restoreOrigin(player, session.origin);
        } finally {
            session.originWorldLease.close();
        }
        if (restored && !retainRecoveryUntilNextJoin) {
            try {
                store.remove(player.getUUID());
            } catch (IOException exception) {
                LOGGER.error("Restored {} but could not clear its recovery record",
                        player.getUUID(), exception);
            }
        } else {
            LOGGER.error("Could not restore spectate origin for {}; recovery record retained",
                    player.getUUID());
        }
        if (notifyClient) {
            sendState(player);
        }
        if (message != null) {
            player.sendSystemMessage(Component.literal(message)
                    .withStyle(restored ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        LOGGER.info("{} stopped voluntarily spectating; restored={}, recoveryRetained={}",
                player.getGameProfile().name(), restored, retainRecoveryUntilNextJoin);
    }

    private static boolean restoreOrigin(ServerPlayer player, StoredSession origin) {
        if (server == null) {
            return false;
        }
        if (player.getCamera() != player) {
            player.setCamera(player);
        }

        ServerLevel destination = null;
        Identifier dimensionId = Identifier.tryParse(origin.dimension());
        if (dimensionId != null) {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            destination = server.getLevel(dimension);
        }
        boolean teleported = destination != null && player.teleportTo(
                destination,
                origin.x(), origin.y(), origin.z(),
                Set.of(), origin.yaw(), origin.pitch(), false);
        if (!teleported) {
            teleported = teleportToSpawn(player);
        }

        GameType gameType = GameType.byName(origin.gameMode(), GameType.SURVIVAL);
        if (gameType == GameType.SPECTATOR) {
            gameType = GameType.SURVIVAL;
        }
        player.setGameMode(gameType);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        return teleported;
    }

    private static boolean teleportToSpawn(ServerPlayer player) {
        LevelData.RespawnData respawn = server.getRespawnData();
        ServerLevel destination = server.getLevel(respawn.dimension());
        if (destination == null) {
            destination = server.overworld();
        }
        BlockPos position = respawn.pos();
        return player.teleportTo(destination,
                position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D,
                Set.of(), respawn.yaw(), respawn.pitch(), false);
    }

    private static List<ServerPlayer> eligibleTargets(ServerPlayer observer) {
        if (server == null) {
            return List.of();
        }
        return server.getPlayerList().getPlayers().stream()
                .filter(target -> !target.getUUID().equals(observer.getUUID()))
                .filter(ServerPlayer::isAlive)
                .filter(target -> target.gameMode() != GameType.SPECTATOR)
                .sorted(Comparator
                        .comparing((ServerPlayer target) -> target.getGameProfile().name(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(target -> target.getUUID().toString()))
                .toList();
    }

    private static SafetyResult evaluateSafety(ServerPlayer player) {
        return SpectateSafetyRules.initialBlocker(safetySnapshot(player))
                .map(blocker -> SafetyResult.blocked(blocker.initialMessage()))
                .orElseGet(SafetyResult::permitted);
    }

    private static SpectateSafetyRules.Snapshot safetySnapshot(ServerPlayer player) {
        Long lastDamageTick = LAST_DAMAGE_TICKS.get(player.getUUID());
        boolean recentlyDamaged = player.hurtTime > 0 || lastDamageTick != null
                && tickCounter - lastDamageTick < SpectateSafetyRules.RECENT_DAMAGE_TICKS;
        return new SpectateSafetyRules.Snapshot(
                player.isAlive(),
                player.gameMode() == GameType.SPECTATOR,
                player.isPassenger(),
                player.isSleeping(),
                player.onGround(),
                player.isFallFlying(),
                player.isOnFire(),
                player.isInLava(),
                player.isInWater(),
                recentlyDamaged,
                hasActiveThreat(player));
    }

    private static boolean hasActiveThreat(ServerPlayer player) {
        return !player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(ACTIVE_THREAT_RADIUS),
                mob -> mob.isAlive()
                        && mob.getTarget() == player
                        && mob.hasLineOfSight(player)).isEmpty();
    }

    private static void sendState(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, SpectateStatePayload.TYPE)) {
            return;
        }
        List<ServerPlayer> targets = eligibleTargets(player);
        ActiveSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) {
            ArmingSession arming = ARMING_SESSIONS.get(player.getUUID());
            if (arming != null) {
                ServerPlayNetworking.send(player, new SpectateStatePayload(
                        false, true, arming.ticksRemaining,
                        false, "", "", -1, -1, targets.size()));
                return;
            }
            SafetyResult safety = evaluateSafety(player);
            boolean allowed = safety.allowed() && !targets.isEmpty();
            String reason = targets.isEmpty()
                    ? "No eligible players are online."
                    : safety.reason();
            ServerPlayNetworking.send(player, new SpectateStatePayload(
                    false, false, 0, allowed, reason, "", -1, -1, targets.size()));
            return;
        }

        int targetIndex = -1;
        for (int index = 0; index < targets.size(); index++) {
            if (targets.get(index).getUUID().equals(session.targetId)) {
                targetIndex = index;
                break;
            }
        }
        String targetName = targetIndex >= 0
                ? targets.get(targetIndex).getGameProfile().name()
                : "";
        int targetEntityId = targetIndex >= 0 ? targets.get(targetIndex).getId() : -1;
        ServerPlayNetworking.send(player, new SpectateStatePayload(
                true, false, 0, false, "",
                targetName, targetEntityId, targetIndex, targets.size()));
    }

    private static final class ArmingSession {
        private final ResourceKey<Level> dimension;
        private final Vec3 anchor;
        private final UUID requestedTargetId;
        private int ticksRemaining;

        private ArmingSession(
                ResourceKey<Level> dimension,
                Vec3 anchor,
                UUID requestedTargetId,
                int ticksRemaining) {
            this.dimension = dimension;
            this.anchor = anchor;
            this.requestedTargetId = requestedTargetId;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private static final class ActiveSession {
        private final StoredSession origin;
        private final VoxyWorldLease originWorldLease;
        private UUID targetId;

        private ActiveSession(
                StoredSession origin, UUID targetId, VoxyWorldLease originWorldLease) {
            this.origin = origin;
            this.targetId = targetId;
            this.originWorldLease = originWorldLease;
        }
    }

    private record SafetyResult(boolean allowed, String reason) {
        private static SafetyResult permitted() {
            return new SafetyResult(true, "");
        }

        private static SafetyResult blocked(String reason) {
            return new SafetyResult(false, reason);
        }
    }
}
