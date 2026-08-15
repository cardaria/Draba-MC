package xyz.draba.spectate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.draba.spectate.network.ClientModPolicyChallengePayload;
import xyz.draba.spectate.network.ClientModPolicyResponsePayload;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ClientModPolicy {
    static final String CONFIG_FILE = "draba-client-mod-policy.json";
    private static final Logger LOGGER = LoggerFactory.getLogger("draba_spectate/mod_policy");
    private static final Gson GSON = new GsonBuilder().create();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> ESSENTIAL_IDS = Set.of(
            "minecraft", "java", "fabricloader", "fabric-api", "draba_spectate");

    private static final Map<UUID, PendingCheck> PENDING = new HashMap<>();
    private static PolicyConfig config;
    private static long tick;

    private ClientModPolicy() {
    }

    static void initialize(Path configPath) {
        config = load(configPath);
        LOGGER.info("Client mod policy loaded: enforce={}, allowed={}, required={}, timeout={} ticks",
                config.enforce(), config.allowedMods().size(), config.requiredMods().size(),
                config.responseTimeoutTicks());
    }

    static void onJoin(ServerPlayer player) {
        if (config == null || !config.enforce()) {
            return;
        }
        long nonce = RANDOM.nextLong();
        boolean supported = ServerPlayNetworking.canSend(
                player, ClientModPolicyChallengePayload.TYPE);
        PENDING.put(player.getUUID(), new PendingCheck(
                nonce, tick + config.responseTimeoutTicks(), supported));
        if (supported) {
            ServerPlayNetworking.send(player, new ClientModPolicyChallengePayload(nonce));
        }
    }

    static void onLeave(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    static void onServerStopped() {
        PENDING.clear();
        tick = 0L;
    }

    static void onServerTick(MinecraftServer server) {
        tick++;
        for (Map.Entry<UUID, PendingCheck> entry : new ArrayList<>(PENDING.entrySet())) {
            if (tick < entry.getValue().deadlineTick()) {
                continue;
            }
            PENDING.remove(entry.getKey());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            String message = entry.getValue().channelSupported()
                    ? config.messageTimeout()
                    : config.messageUpdateRequired();
            LOGGER.warn("Disconnecting {}: client mod policy response missing",
                    player.getGameProfile().name());
            player.connection.disconnect(Component.literal(message));
        }
    }

    static void handleResponse(ServerPlayer player, ClientModPolicyResponsePayload response) {
        if (config == null || !config.enforce()) {
            return;
        }
        PendingCheck pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.channelSupported()
                || pending.nonce() != response.nonce()) {
            LOGGER.warn("Disconnecting {}: invalid client mod policy challenge response",
                    player.getGameProfile().name());
            player.connection.disconnect(Component.literal(config.messageMalformed()));
            return;
        }

        Evaluation evaluation = evaluate(config, response.modIds());
        PENDING.remove(player.getUUID());
        if (!evaluation.valid()) {
            String detail = !evaluation.disallowed().isEmpty()
                    ? config.messageDisallowed() + " " + String.join(", ", evaluation.disallowed())
                    : config.messageMissing() + " " + String.join(", ", evaluation.missing());
            LOGGER.warn("Disconnecting {} for client mod policy violation: disallowed={}, missing={}",
                    player.getGameProfile().name(), evaluation.disallowed(), evaluation.missing());
            player.connection.disconnect(Component.literal(detail));
            return;
        }
        LOGGER.info("{} passed client mod policy verification ({} top-level mods)",
                player.getGameProfile().name(), evaluation.reportedCount());
    }

    static Evaluation evaluate(PolicyConfig policy, List<String> reportedIds) {
        Set<String> reported = new HashSet<>();
        List<String> malformed = new ArrayList<>();
        for (String rawId : reportedIds) {
            String id = normalize(rawId);
            if (id.isEmpty() || !isValidModId(id)) {
                malformed.add(rawId == null ? "<null>" : rawId);
            } else {
                reported.add(id);
            }
        }

        Set<String> allowed = new HashSet<>(ESSENTIAL_IDS);
        policy.allowedMods().stream().map(ClientModPolicy::normalize).forEach(allowed::add);
        List<String> disallowed = new ArrayList<>(malformed);
        for (String id : reported) {
            if (!allowed.contains(id) && !id.startsWith("fabric-")) {
                disallowed.add(id);
            }
        }

        List<String> missing = new ArrayList<>();
        for (String required : policy.requiredMods()) {
            String id = normalize(required);
            if (!reported.contains(id)) {
                missing.add(id);
            }
        }
        disallowed.sort(String::compareTo);
        missing.sort(String::compareTo);
        return new Evaluation(List.copyOf(disallowed), List.copyOf(missing), reported.size());
    }

    private static PolicyConfig load(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            PolicyConfig loaded = GSON.fromJson(reader, PolicyConfig.class);
            if (loaded == null) {
                throw new IOException("Config is empty");
            }
            return loaded.validated();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not load " + path, exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isValidModId(String value) {
        if (value.length() > ClientModPolicyResponsePayload.MAX_ID_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_' && character != '-') {
                return false;
            }
        }
        return true;
    }

    record PolicyConfig(
            boolean enforce,
            int responseTimeoutTicks,
            List<String> allowedMods,
            List<String> requiredMods,
            String messageDisallowed,
            String messageMissing,
            String messageUpdateRequired,
            String messageTimeout,
            String messageMalformed) {
        PolicyConfig {
            allowedMods = allowedMods == null ? List.of() : List.copyOf(allowedMods);
            requiredMods = requiredMods == null ? List.of() : List.copyOf(requiredMods);
        }

        private PolicyConfig validated() {
            if (responseTimeoutTicks < 20 || responseTimeoutTicks > 1200) {
                throw new IllegalArgumentException("responseTimeoutTicks must be between 20 and 1200");
            }
            if (messageDisallowed == null || messageMissing == null
                    || messageUpdateRequired == null || messageTimeout == null
                    || messageMalformed == null) {
                throw new IllegalArgumentException("All client mod policy messages are required");
            }
            return this;
        }
    }

    record Evaluation(List<String> disallowed, List<String> missing, int reportedCount) {
        boolean valid() {
            return disallowed.isEmpty() && missing.isEmpty();
        }
    }

    private record PendingCheck(long nonce, long deadlineTick, boolean channelSupported) {
    }
}
